package com.runesuite.cache.net

import com.runesuite.cache.buf.toCurrentList
import io.netty.buffer.Unpooled
import io.netty.util.ResourceLeakDetector
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.net.NetSocket
import mu.KotlinLogging
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future

class CacheClient
@Throws(IOException::class)
constructor(val revision: Int, val host: String, val port: Int) : AutoCloseable, Closeable {

    private val logger = KotlinLogging.logger {  }

    private val vertx = Vertx.vertx()

    private val socket: NetSocket

    private val responses: MutableMap<Pair<Int, Int>, CompletableFuture<FileResponse>> = ConcurrentHashMap()

    private val requestBuffer = Unpooled.buffer(5)
    private val responseBuffer = Unpooled.buffer(FileResponse.SIZE)

    init {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID)

        val netClient = vertx.createNetClient()
        Unpooled.buffer()
        val socketFuture = CompletableFuture<NetSocket>()
        netClient.connect(port, host) {
            when (it.succeeded()) {
                true -> socketFuture.complete(it.result())
                false -> { close(); socketFuture.completeExceptionally(it.cause()) }
            }
        }
        socket = socketFuture.get()
        val handshakeRequest = HandshakeRequest(revision)
        logger.debug { handshakeRequest }
        val handshakeResponseFuture = CompletableFuture<HandshakeResponse>()
        socket.handler { handshakeResponseFuture.complete(HandshakeResponse(it.byteBuf)) }
        write(handshakeRequest)
        val handshakeResponse = handshakeResponseFuture.get()
        logger.debug { "Response: ${handshakeResponse.input.toCurrentList()}" }
        logger.debug { handshakeResponse }
        if (handshakeResponse.status != HandshakeResponse.Status.SUCCESS) {
            close()
            throw IOException(handshakeResponse.toString())
        }
        val connectionInfo = ConnectionInfoOffer(ConnectionInfoOffer.State.LOGGED_OUT)
        logger.debug { connectionInfo }
        write(connectionInfo)
        socket.handler { buffer ->
            val byteBuf = buffer.byteBuf
            logger.debug { "Response: ${byteBuf.toCurrentList()}" }
            responseBuffer.writeBytes(byteBuf)
            logger.debug { "Response buffer: ${responseBuffer.toCurrentList()}" }
            val response = FileResponse(responseBuffer)
            logger.debug { response }
            if (!response.done) {
                return@handler
            }
            val responseFuture = responses.remove(response.index to response.file)
            logger.debug { "Response requested: ${responseFuture != null}" }
            responseFuture?.complete(response)
            responseBuffer.clear()
        }
    }

    private fun write(request: Request) {
        requestBuffer.clear()
        request.write(requestBuffer)
        logger.debug { "Writing: ${requestBuffer.toCurrentList()}" }
        socket.write(Buffer.buffer(requestBuffer))
    }

    fun request(index: Int, file: Int): Future<FileResponse> {
        val responseFuture = CompletableFuture<FileResponse>()
        val fileRequest = FileRequest(index, file)
        logger.debug { fileRequest }
        responses[index to file] = responseFuture
        write(fileRequest)
        return responseFuture
    }

    override fun close() {
        requestBuffer.release()
        responseBuffer.release()
        vertx.close()
    }
}