package br.dev.monitorremoto.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import org.json.JSONObject

/** Somente memória da sessão. Nenhum histórico de coordenadas ou cache após Parar. */
object LocationShareStore {
    @Volatile private var active = false
    @Volatile private var point: Location? = null
    @Volatile private var profile = LocationSharePolicy.ECONOMICO

    @Synchronized fun begin(mode: String) {
        point = null
        profile = mode
        active = true
    }
    @Synchronized fun stop() {
        active = false
        point = null
    }
    fun active() = active
    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun hasBackgroundPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 29 ||
        context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun hasFinePermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun locationEnabled(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(LocationManager::class.java)
        if (Build.VERSION.SDK_INT >= 28) manager.isLocationEnabled else
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)
    @Synchronized fun update(location: Location) {
        if (!active || !LocationSharePolicy.coordinatesValid(location.latitude, location.longitude)) return
        val now = SystemClock.elapsedRealtime()
        // Não substituir leitura recente por correção claramente mais antiga.
        val incomingAge = now - (location.elapsedRealtimeNanos / 1_000_000L)
        if (!LocationSharePolicy.usableAgeMs(incomingAge)) return
        val current = point
        if (current != null && location.elapsedRealtimeNanos < current.elapsedRealtimeNanos) return
        point = Location(location)
    }
    @Synchronized fun snapshot(context: Context): JSONObject {
        val authorized = active && SettingsStore.isMonitorEnabled(context)
        val result = JSONObject().put("sharing", authorized)
            .put("recoveryArmed", SettingsStore.locationRecoveryEnabled(context))
            .put("backgroundPermission", hasBackgroundPermission(context))
            .put("permission", hasPermission(context))
            .put("locationEnabled", locationEnabled(context))
            .put("profile", if (authorized) profile else JSONObject.NULL)
        if (!authorized) return result.put("state", "desativada")
        if (!hasPermission(context)) return result.put("state", "sem_permissao")
        if (!locationEnabled(context)) return result.put("state", "gps_desligado")
        val found = point ?: return result.put("state", "aguardando")
        val age = (SystemClock.elapsedRealtime() - found.elapsedRealtimeNanos / 1_000_000L).coerceAtLeast(0L)
        if (!LocationSharePolicy.usableAgeMs(age)) return result.put("state", "expirada")
        return result.put("state", if (age > LocationSharePolicy.intervalMs(profile) * 3) "antiga" else "disponivel")
            .put("latitude", found.latitude).put("longitude", found.longitude)
            .put("accuracyM", if (found.hasAccuracy()) found.accuracy.toDouble() else JSONObject.NULL)
            .put("provider", found.provider ?: "desconhecido")
            .put("timestampMs", found.time)
            .put("ageMs", age)
    }
}
