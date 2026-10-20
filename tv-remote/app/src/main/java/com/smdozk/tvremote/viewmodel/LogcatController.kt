package com.smdozk.tvremote.viewmodel

import com.smdozk.tvremote.adb.AdbManager
import com.smdozk.tvremote.model.LogLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogcatController(private val adb: AdbManager, private val scope: CoroutineScope) {

    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var job: Job? = null
    private var nextId = 0L

    fun start() {
        if (job?.isActive == true) return
        // logcat -T replays the recent backlog, so keeping old lines would duplicate them.
        _lines.value = emptyList()
        _isRunning.value = true
        job = scope.launch(Dispatchers.IO) {
            try {
                val stream = adb.openLogcat()
                val incoming = Channel<String>(capacity = 4096, onBufferOverflow = BufferOverflow.DROP_OLDEST)
                coroutineScope {
                    launch {
                        try {
                            while (true) incoming.trySend(stream.source.readUtf8Line() ?: break)
                        } catch (_: Throwable) {
                            // Closing the stream is how a stop unblocks this read.
                        } finally {
                            incoming.close()
                        }
                    }
                    try {
                        while (true) {
                            val first = incoming.receiveCatching().getOrNull() ?: break
                            val batch = ArrayList<LogLine>()
                            batch += parseLogLine(nextId++, first)
                            while (batch.size < MAX_BATCH) {
                                batch += parseLogLine(nextId++, incoming.tryReceive().getOrNull() ?: break)
                            }
                            _lines.update { (it + batch).takeLast(MAX_LINES) }
                            // Batching caps UI updates at ~10/s however chatty the TV is.
                            delay(PUBLISH_INTERVAL_MS)
                        }
                    } finally {
                        withContext(NonCancellable) { runCatching { stream.close() } }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun clear() {
        _lines.value = emptyList()
    }

    private companion object {
        const val MAX_LINES = 2000
        const val MAX_BATCH = 500
        const val PUBLISH_INTERVAL_MS = 100L
    }
}

private val BRIEF_LINE = Regex("""^([VDIWEFS])/(.*?)\(\s*\d+\):\s?(.*)$""")

internal fun parseLogLine(id: Long, raw: String): LogLine {
    val line = raw.trimEnd('\r')
    val match = BRIEF_LINE.find(line) ?: return LogLine(id, ' ', "", line)
    val (level, tag, message) = match.destructured
    return LogLine(id, level[0], tag.trim(), message)
}
