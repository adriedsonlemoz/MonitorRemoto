package br.dev.monitorremoto.transport

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Leitor de enquadramento LF para sessão TCP de arquivos.
 * Reutilizado entre quadros para não perder bytes da mensagem seguinte.
 */
internal class StreamFrameReader(input: InputStream) {
    private val source = BufferedInputStream(input, 64 * 1024)
    private val buffer = ByteArray(64 * 1024)
    private var start = 0
    private var end = 0

    fun receive(maxBytes: Int): ByteArray {
        require(maxBytes > 0)
        val answer = ByteArrayOutputStream(minOf(maxBytes, 256 * 1024))
        while (true) {
            if (start == end) {
                end = source.read(buffer)
                if (end < 0) throw java.io.EOFException("Conexão interrompida")
                start = 0
            }
            var stop = start
            while (stop < end && buffer[stop] != 10.toByte()) stop++
            val available = stop - start
            check(answer.size().toLong() + available < maxBytes.toLong()) {
                "Mensagem acima do limite permitido"
            }
            answer.write(buffer, start, available)
            start = if (stop < end) stop + 1 else stop
            if (stop < end) return answer.toByteArray()
        }
    }
}
