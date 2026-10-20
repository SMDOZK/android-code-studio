package com.smdozk.tvremote.adb

// Splits an H.264 Annex-B byte stream into NAL units, each emitted with a 4-byte start code.
class AnnexBFramer {
    private var buf = ByteArray(256 * 1024)
    private var size = 0
    private var scanFrom = 0
    private var nalStart = -1

    fun append(data: ByteArray, emit: (ByteArray) -> Unit) = append(data, 0, data.size, emit)

    fun append(data: ByteArray, offset: Int, length: Int, emit: (ByteArray) -> Unit) {
        // Reclaim already-emitted bytes only when space runs out, keeping appends amortized O(n).
        if (size + length > buf.size) compact()
        ensureCapacity(size + length)
        System.arraycopy(data, offset, buf, size, length)
        size += length

        var i = scanFrom
        while (i + 2 < size) {
            // No start code can begin at i, i+1 or i+2 when the third byte is neither 0 nor 1.
            if ((buf[i + 2].toInt() and 0xFF) > 1) {
                i += 3
            } else if (buf[i].toInt() == 0 && buf[i + 1].toInt() == 0 && buf[i + 2].toInt() == 1) {
                if (nalStart >= 0) emitNal(nalStart, i, emit)
                nalStart = i + 3
                i += 3
            } else {
                i++
            }
        }
        scanFrom = i
    }

    // Without per-frame framing, a quiet stream is the only signal that the last NAL is complete.
    fun flush(emit: (ByteArray) -> Unit) {
        if (nalStart < 0 || nalStart >= size) return
        var end = size
        while (end > nalStart && buf[end - 1].toInt() == 0) end--
        emitNal(nalStart, end, emit)
        // Trailing zeros may be the first half of the next start code.
        val keep = size - end
        System.arraycopy(buf, end, buf, 0, keep)
        size = keep
        scanFrom = 0
        nalStart = -1
    }

    private fun emitNal(from: Int, to: Int, emit: (ByteArray) -> Unit) {
        var end = to
        while (end > from && buf[end - 1].toInt() == 0) end--
        if (end <= from) return
        val nal = ByteArray(4 + end - from)
        nal[3] = 1
        System.arraycopy(buf, from, nal, 4, end - from)
        emit(nal)
    }

    private fun compact() {
        val keepFrom = if (nalStart >= 0) nalStart else scanFrom
        if (keepFrom <= 0) return
        val remaining = size - keepFrom
        System.arraycopy(buf, keepFrom, buf, 0, remaining)
        size = remaining
        scanFrom -= keepFrom
        if (nalStart >= 0) nalStart -= keepFrom
    }

    private fun ensureCapacity(needed: Int) {
        if (needed <= buf.size) return
        var capacity = buf.size
        while (capacity < needed) capacity *= 2
        buf = buf.copyOf(capacity)
    }
}
