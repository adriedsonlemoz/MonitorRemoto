package br.dev.monitorremoto.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import br.dev.monitorremoto.core.ScreenCapturePolicy
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Grava SOMENTE após toque do usuário, no controlador; nunca no monitorado. */
internal object RemoteImageSaver {
    fun fileName(): String = "Monitor_Remoto_" +
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date()) + ".jpg"

    fun validJpeg(bytes: ByteArray): Boolean = ScreenCapturePolicy.validJpeg(bytes)

    /** API 29+: salvar em Imagens/Monitor Remoto, sem permissão ampla de armazenamento. */
    fun save(context: Context, bytes: ByteArray, filename: String): Uri {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        require(validJpeg(bytes)) { "JPEG inválido ou acima do limite" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Monitor Remoto")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Galeria indisponível")
        try {
            resolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                ?: throw IOException("Não foi possível abrir a imagem")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            check(resolver.update(uri, values, null, null) == 1) { "Falha ao concluir captura" }
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    fun documentIntent(filename: String): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "image/jpeg"
        putExtra(Intent.EXTRA_TITLE, filename)
    }
}
