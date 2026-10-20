package com.smdozk.tvremote.adb

import okio.Buffer
import okio.ForwardingSink
import okio.ForwardingSource
import okio.Sink
import okio.Source

class ProgressSource(delegate: Source, private val onBytes: (Long) -> Unit) : ForwardingSource(delegate) {
    private var total = 0L

    override fun read(sink: Buffer, byteCount: Long): Long {
        val read = super.read(sink, byteCount)
        if (read > 0) {
            total += read
            onBytes(total)
        }
        return read
    }
}

class ProgressSink(delegate: Sink, private val onBytes: (Long) -> Unit) : ForwardingSink(delegate) {
    private var total = 0L

    override fun write(source: Buffer, byteCount: Long) {
        super.write(source, byteCount)
        total += byteCount
        onBytes(total)
    }
}

fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
