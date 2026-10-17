package br.dev.monitorremoto.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Leituras reais expostas pelas APIs públicas. NÃO interpreta headroom como graus Celsius. */
object ThermalDiagnostics {
    fun snapshot(context: Context): JSONObject {
        val battery = BatteryReader.snapshot(context)
        val readings = JSONArray()
        if (!battery.isNull("temperatureC")) readings.put(JSONObject()
            .put("name", "Bateria").put("type", "battery")
            .put("celsius", battery.optDouble("temperatureC"))
            .put("source", "Android BatteryManager"))
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val result = JSONObject().put("battery", battery)
            .put("sampledAt", System.currentTimeMillis())
            .put("thermalStatus", if (Build.VERSION.SDK_INT >= 29)
                power.currentThermalStatus else JSONObject.NULL)
            .put("headroom", JSONObject.NULL)
        if (Build.VERSION.SDK_INT >= 30) runCatching {
            val value = power.getThermalHeadroom(0)
            if (value.isFinite() && value >= 0f) result.put("headroom", value.toDouble())
        }
        // O sensor ambiente, se presente, fornece leitura assincrona. Não se tenta ler
        // /sys/class/thermal ou APIs ocultas sem privilégios.
        if (Thread.currentThread() != android.os.Looper.getMainLooper().thread) {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensors = manager.getSensorList(Sensor.TYPE_AMBIENT_TEMPERATURE).take(4)
            if (sensors.isNotEmpty()) {
                val worker = HandlerThread("MonitorRemoto-thermal-sensor").also { it.start() }
                val waiter = CountDownLatch(1)
                val observed = java.util.concurrent.atomic.AtomicReference<Pair<String, Float>?>(null)
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val c = event.values.firstOrNull() ?: return
                        if (c.isFinite() && c in -40f..120f) {
                            observed.compareAndSet(null, event.sensor.name.take(70) to c)
                            waiter.countDown()
                        }
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                try {
                    sensors.forEach { manager.registerListener(listener, it,
                        SensorManager.SENSOR_DELAY_NORMAL, Handler(worker.looper)) }
                    waiter.await(650, TimeUnit.MILLISECONDS)
                    observed.get()?.let { (name, c) -> readings.put(JSONObject()
                        .put("name", "Ambiente · $name").put("type", "ambient")
                        .put("celsius", c.toDouble()).put("source", "Sensor Android")) }
                } finally {
                    manager.unregisterListener(listener)
                    worker.quitSafely()
                }
            }
        }
        return result.put("readings", readings)
    }
}
