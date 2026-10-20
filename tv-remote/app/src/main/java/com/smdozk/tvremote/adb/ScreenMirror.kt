package com.smdozk.tvremote.adb

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

sealed interface MirrorState {
    data object Idle : MirrorState
    data object Starting : MirrorState
    data class Live(val fps: Int) : MirrorState
    data class Failed(val message: String) : MirrorState
}

// Streams `screenrecord` H.264 from the TV into a hardware decoder that renders onto [Surface].
class ScreenMirror(private val adb: AdbManager, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow<MirrorState>(MirrorState.Idle)
    val state: StateFlow<MirrorState> = _state.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var job: Job? = null

    fun start(surface: Surface, width: Int, height: Int) {
        val previous = job
        previous?.cancel()
        _state.value = MirrorState.Starting
        job = scope.launch(Dispatchers.IO) {
            // A surface accepts one producer; the old decoder must let go before a new one connects.
            previous?.join()
            try {
                while (isActive) {
                    // screenrecord stops itself after its time limit, so a session that produced frames just restarts.
                    val frames = session(surface, width, height)
                    if (frames == 0) {
                        _state.value = MirrorState.Failed("TV ekran yayınını desteklemiyor ya da yayın başlatılamadı.")
                        break
                    }
                    delay(RESTART_DELAY_MS)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = MirrorState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    // [onStopped] runs on the main thread once the decoder no longer touches the surface.
    fun stop(onStopped: (() -> Unit)? = null) {
        // The reference stays so the next start() can wait for this cleanup to finish.
        val current = job
        _state.value = MirrorState.Idle
        if (current == null || current.isCompleted) {
            onStopped?.invoke()
            return
        }
        current.cancel()
        if (onStopped != null) current.invokeOnCompletion { mainHandler.post(onStopped) }
    }

    private suspend fun session(surface: Surface, width: Int, height: Int): Int = coroutineScope {
        val stream = adb.openScreenRecord(width, height, BIT_RATE)
        val codecThread = HandlerThread("mirror-decoder").apply { start() }
        val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val freeInputs = LinkedBlockingQueue<Int>()
        val rendered = AtomicInteger()
        val decoderError = AtomicReference<Throwable?>()
        val chunks = Channel<ByteArray>(capacity = 128)

        try {
            codec.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                    freeInputs.offer(index)
                }

                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    // May race with stop(); a stopped codec throws here.
                    runCatching { codec.releaseOutputBuffer(index, true) }
                    rendered.incrementAndGet()
                }

                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                    decoderError.set(e)
                    chunks.close()
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) = Unit
            }, Handler(codecThread.looper))
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height)
                setInteger(MediaFormat.KEY_PRIORITY, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
            codec.configure(format, surface, null, 0)
            codec.start()

            launch(Dispatchers.IO) {
                val buffer = ByteArray(64 * 1024)
                try {
                    while (true) {
                        val read = stream.source.read(buffer)
                        if (read < 0) break
                        chunks.send(buffer.copyOf(read))
                    }
                } catch (_: Throwable) {
                    // Closing the stream is how a stop unblocks this read.
                } finally {
                    chunks.close()
                }
            }

            val fpsJob = launch {
                var last = 0
                while (true) {
                    delay(1000)
                    val now = rendered.get()
                    if (now > 0) _state.value = MirrorState.Live(now - last)
                    last = now
                }
            }

            val framer = AnnexBFramer()
            val feeder = NalFeeder(codec, freeInputs)
            while (true) {
                val chunk = withTimeoutOrNull(IDLE_FLUSH_MS) { chunks.receiveCatching() }
                when {
                    chunk == null -> framer.flush(feeder::submit)
                    chunk.isClosed -> break
                    else -> framer.append(chunk.getOrThrow(), feeder::submit)
                }
            }
            framer.flush(feeder::submit)
            fpsJob.cancel()
            decoderError.get()?.let { throw it }
            rendered.get()
        } finally {
            withContext(NonCancellable) {
                runCatching { stream.close() }
                chunks.cancel()
                runCatching { codec.stop() }
                runCatching { codec.release() }
                codecThread.quitSafely()
            }
        }
    }

    private class NalFeeder(private val codec: MediaCodec, private val freeInputs: LinkedBlockingQueue<Int>) {
        private var sawFrame = false
        private var pending: ByteArray? = null

        fun submit(nal: ByteArray) {
            when (nal[4].toInt() and 0x1F) {
                NAL_SPS, NAL_PPS -> if (sawFrame) hold(nal) else queue(nal, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                NAL_SLICE, NAL_IDR -> {
                    sawFrame = true
                    val data = pending?.let { it + nal } ?: nal
                    pending = null
                    queue(data, 0)
                }
                // SEI, access unit delimiters, etc. travel with the next slice.
                else -> hold(nal)
            }
        }

        private fun hold(nal: ByteArray) {
            pending = pending?.let { it + nal } ?: nal
        }

        private fun queue(data: ByteArray, flags: Int) {
            val index = freeInputs.poll(INPUT_WAIT_MS, TimeUnit.MILLISECONDS) ?: return
            val buffer = runCatching { codec.getInputBuffer(index) }.getOrNull() ?: return
            if (data.size > buffer.capacity()) {
                codec.queueInputBuffer(index, 0, 0, 0, 0)
                return
            }
            buffer.clear()
            buffer.put(data)
            codec.queueInputBuffer(index, 0, data.size, System.nanoTime() / 1000, flags)
        }
    }

    private companion object {
        const val BIT_RATE = 4_000_000
        const val IDLE_FLUSH_MS = 50L
        const val INPUT_WAIT_MS = 500L
        const val RESTART_DELAY_MS = 300L
        const val NAL_SLICE = 1
        const val NAL_IDR = 5
        const val NAL_SPS = 7
        const val NAL_PPS = 8
    }
}
