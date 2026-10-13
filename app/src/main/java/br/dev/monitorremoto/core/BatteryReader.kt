package br.dev.monitorremoto.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import org.json.JSONObject

object BatteryReader {
    fun snapshot(context: Context): JSONObject {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100.0 / scale) else null
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val temperatureTenth = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1000) ?: -1000
        val current = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val average = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        fun readableCurrent(raw: Int): Double? =
            raw.takeUnless { it == Int.MIN_VALUE || it == Int.MAX_VALUE || it == 0 }?.div(1000.0)

        return JSONObject().apply {
            put("levelPercent", percent ?: JSONObject.NULL)
            put("charging", status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
            put("full", status == BatteryManager.BATTERY_STATUS_FULL)
            put("temperatureC", if (temperatureTenth > -1000) temperatureTenth / 10.0 else JSONObject.NULL)
            put("voltageMv", if (voltageMv > 0) voltageMv else JSONObject.NULL)
            put("currentMa", readableCurrent(current) ?: JSONObject.NULL)
            put("averageMa", readableCurrent(average) ?: JSONObject.NULL)
            put("updatedMs", System.currentTimeMillis())
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            put("androidSdk", Build.VERSION.SDK_INT)
        }
    }
}
