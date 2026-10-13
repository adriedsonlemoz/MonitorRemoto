package br.dev.monitorremoto.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Chaves de pareamento protegidas por uma chave não exportável no Android Keystore. */
object SecretVault {
    private const val KEY_ALIAS = "monitor_remoto_secrets_v1"
    private const val PREFS = "monitor_remoto_private"
    const val MONITOR_KEY = "monitor_key"
    const val CONTROLLER_KEY = "controller_key"

    /** Um identificador opaco por endereço impede reutilizar a chave de outro aparelho. */
    fun profileKeyName(host: String): String {
        val canonical = host.trim().lowercase(java.util.Locale.ROOT)
        require(canonical.isNotBlank() && canonical.length <= 253)
        val bytes = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return "profile_" + bytes.joinToString("") { "%02x".format(it) }
    }

    private fun validName(name: String) =
        name == MONITOR_KEY || name == CONTROLLER_KEY ||
            (name.startsWith("profile_") && name.length == 72 && name.substring(8).all { it in "0123456789abcdef" })

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    @Synchronized fun save(context: Context, name: String, bytes: ByteArray) {
        require(validName(name))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(bytes)
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(name, encoded).apply()
    }

    @Synchronized fun load(context: Context, name: String): ByteArray? {
        require(validName(name))
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(name, null) ?: return null
        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.size < 29) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
            cipher.doFinal(payload.copyOfRange(12, payload.size))
        } catch (_: Exception) {
            null // Por exemplo, se a keystore foi invalidada. Exigir novo pareamento.
        }
    }

    @Synchronized fun delete(context: Context, name: String) {
        require(validName(name))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(name).commit()
    }

    fun getOrCreateMonitorKey(context: Context): ByteArray =
        load(context, MONITOR_KEY) ?: CryptoBox.newPairingKey().also { save(context, MONITOR_KEY, it) }
}
