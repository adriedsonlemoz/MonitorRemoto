package br.dev.monitorremoto.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

/** Consulta sob demanda e limitada: SMS nunca são gravados em arquivo pelo Monitor Remoto. */
object SmsInboxReader {
    fun hasAccess(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun snapshot(context: Context): JSONArray {
        val result = JSONArray()
        if (!SettingsStore.isMonitorEnabled(context) || !SettingsStore.shareSmsInbox(context) || !hasAccess(context)) return result
        val fields = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, fields, null, null, "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                var scanned = 0
                while (cursor.moveToNext() && result.length() < 30 && scanned++ < 100) {
                    val address = cursor.getString(addressIndex).orEmpty()
                    val body = cursor.getString(bodyIndex).orEmpty()
                    if (SensitiveContent.isSensitive(address, body) || MessagePolicy.looksLikeAuthenticationSms(body)) continue
                    result.put(JSONObject().put("address", address.take(40))
                        .put("text", EventJournal.sanitized(body).take(400))
                        .put("whenMs", cursor.getLong(dateIndex)))
                }
            }
        } catch (_: SecurityException) { return JSONArray() }
        // A autorização pode ter sido revogada durante a consulta do ContentResolver.
        if (!SettingsStore.isMonitorEnabled(context) || !SettingsStore.shareSmsInbox(context) || !hasAccess(context))
            return JSONArray()
        return result
    }

    /** Resposta apenas a número regular presente na caixa de entrada, nunca a código curto. */
    fun mayReply(context: Context, destination: String): Boolean {
        if (!hasAccess(context) || !SettingsStore.shareSmsInbox(context)) return false
        if (!MessagePolicy.validReplyAddress(destination)) return false
        val normalized = destination.filter(Char::isDigit)
        return try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS),
                null, null, "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val index = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                var scans = 0
                var found = false
                while (cursor.moveToNext() && scans++ < 100) {
                    if (cursor.getString(index).orEmpty().filter(Char::isDigit) == normalized) {
                        found = true; break
                    }
                }
                found
            } ?: false
        } catch (_: Exception) { false }
    }
}
