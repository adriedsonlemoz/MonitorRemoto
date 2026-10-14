package br.dev.monitorremoto.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

/** Leitura somente sob demanda, sem persistência própria. */
object SmsInboxReader {
    fun hasAccess(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun snapshot(context: Context): JSONArray {
        val result = JSONArray()
        if (!SettingsStore.isMonitorEnabled(context) || !SettingsStore.shareSmsInbox(context) || !hasAccess(context)) return result
        val fields = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE)
        try {
            context.contentResolver.query(Telephony.Sms.CONTENT_URI, fields,
                "${Telephony.Sms.TYPE} IN (?, ?)", arrayOf("1", "2"), "${Telephony.Sms.DATE} DESC")?.use { cursor ->
                val addressColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                var scanned = 0
                while (cursor.moveToNext() && result.length() < 60 && scanned++ < 180) {
                    val address = cursor.getString(addressColumn).orEmpty()
                    val body = cursor.getString(bodyColumn).orEmpty()
                    if (SensitiveContent.isSensitive(address, body) || MessagePolicy.looksLikeAuthenticationSms(body)) continue
                    result.put(JSONObject().put("address", address.take(40))
                        .put("text", EventJournal.sanitized(body).take(400))
                        .put("whenMs", cursor.getLong(dateColumn))
                        .put("direction", if (cursor.getInt(typeColumn) == 2) "enviado" else "recebido"))
                }
            }
        } catch (_: Exception) { return JSONArray() }
        if (!SettingsStore.isMonitorEnabled(context) || !SettingsStore.shareSmsInbox(context) || !hasAccess(context)) return JSONArray()
        return result
    }

    /** Apenas remetentes regulares presentes na caixa recebida. */
    fun mayReply(context: Context, destination: String): Boolean {
        if (!hasAccess(context) || !SettingsStore.shareSmsInbox(context) ||
            !MessagePolicy.validReplyAddress(destination)) return false
        val normalized = destination.filter(Char::isDigit)
        return try {
            context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS), null, null, "${Telephony.Sms.DATE} DESC")?.use { cursor ->
                val column = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                var scans = 0
                var found = false
                while (cursor.moveToNext() && scans++ < 180) {
                    if (cursor.getString(column).orEmpty().filter(Char::isDigit) == normalized) {
                        found = true
                        break
                    }
                }
                found
            } ?: false
        } catch (_: Exception) { false }
    }
}
