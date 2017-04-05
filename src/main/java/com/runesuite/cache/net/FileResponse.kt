package com.runesuite.cache.net

import com.runesuite.cache.Compressor
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled

class FileResponse(override val input: ByteBuf) : Response(input) {

    companion object {
        const val SIZE = 512
    }

    val headerDone = input.readableBytes() >= 8

    val size get() = compressedDataSize + 5 + (if (compression == Compressor.NONE) 0 else 4)

    val index = input.getUnsignedByte(0).toInt()

    val file = input.getUnsignedShort(1)

    val compression = checkNotNull(Compressor.LOOKUP[input.getUnsignedByte(3).toInt()])

    val compressedDataSize = input.getInt(4)

    lateinit var compressedData: ByteBuf
        private set

    lateinit var data: ByteBuf
        private set

    var version: Int? = null
        private set

    val done = headerDone && size + 3 + breaks <= input.readableBytes()

    init {
        if (done) {
            read()
            decompress()
        }
    }

    private fun read() {
        val array = ByteArray(size)
        val view = input.slice()
        var totalRead = 3
        view.skipBytes(totalRead)
        var compressedDataOffset = 0
        for (i in 0..breaks) {
            val bytesInBlock = SIZE - (totalRead % SIZE)
            val bytesToRead = Math.min(bytesInBlock, size - compressedDataOffset)
            view.getBytes(view.readerIndex(), array, compressedDataOffset, bytesToRead)
            view.skipBytes(bytesToRead)
            compressedDataOffset += bytesToRead
            totalRead += bytesToRead
            if (i < breaks) {
                check(compressedDataOffset < size)
                val b = view.readUnsignedByte().toInt()
                totalRead++
                check(b == 0xff)
            }
        }
        check(compressedDataOffset == size)
        compressedData = Unpooled.wrappedBuffer(array)
    }

    private fun decompress() {
        val compressed = compressedData.slice()
        compressed.readBytes(5)
        data = compression.decompress(compressed.readSlice(size - 5))
        version = if (compressed.readableBytes() >= 2) {
            compressed.readUnsignedShort()
        } else {
            null
        }
    }

    val breaks: Int get() {
        val initialSize = SIZE - 3
        if (size <= initialSize) {
            return 0
        }
        val left = size - initialSize
        if (left % (SIZE - 1) == 0) {
            return left / (SIZE - 1)
        } else {
            return left / (SIZE - 1) + 1
        }
    }

    override fun toString(): String {
        return "FileResponse(done=$done, index=$index, file=$file, compression=$compression, compressedDataSize=$compressedDataSize)"
    }
}