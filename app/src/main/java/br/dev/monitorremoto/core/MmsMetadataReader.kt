package br.dev.monitorremoto.core

import android.content.Context
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

/** Metadados básicos de MMS se o provedor permitir, sem anexos ou corpos multimídia. */
object MmsMetadataReader {
    fun snapshot(context: Context): JSONArray = JSONArray().apply {
        if (!SettingsStore.isMonitorEnabled(context) || !SettingsStore.shareSmsInbox(context) ||
            !SmsInboxReader.hasAccess(context)) return@apply
        runCatching {
            val columns = arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.SUBJECT)
            context.contentResolver.query(Telephony.Mms.CONTENT_URI, columns, null, null,
                "${Telephony.Mms.DATE} DESC")?.use { cursor ->
                var scanned = 0
                while (cursor.moveToNext() && scanned++ < 30 && length() < 10) {
                    val raw = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.SUBJECT)).orEmpty()
                    if (SensitiveContent.isSensitive("", raw)) continue
                    put(JSONObject().put("subject", raw.take(120).ifBlank { "MMS sem assunto" })
                        .put("whenMs", cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms.DATE)) * 1000L))
                }
            }
        }
    }
}
