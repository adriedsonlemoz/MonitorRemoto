package br.dev.monitorremoto.core

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.tan

/** Matemática pura do mapa Web Mercator. Nenhum pedido de rede ocorre aqui. */
object MapProjection {
    const val MIN_ZOOM = 3
    const val MAX_ZOOM = 18
    const val TILE_SIZE = 256.0
    const val MAX_LATITUDE = 85.05112878
    fun latitude(lat: Double): Double = lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
    fun longitude(lon: Double): Double = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
    fun scale(zoom: Int): Double = TILE_SIZE * (1 shl zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
    fun x(lon: Double, zoom: Int): Double = (longitude(lon) + 180.0) / 360.0 * scale(zoom)
    fun y(lat: Double, zoom: Int): Double {
        val rad = latitude(lat) * PI / 180.0
        return ((1.0 - ln(tan(rad) + 1.0 / kotlin.math.cos(rad)) / PI) / 2.0 * scale(zoom)).coerceIn(0.0, scale(zoom))
    }
    fun lat(y: Double, zoom: Int): Double = atan(kotlin.math.sinh(PI * (1.0 - 2.0 * y / scale(zoom)))) * 180.0 / PI
    fun lon(x: Double, zoom: Int): Double = longitude((x / scale(zoom)) * 360.0 - 180.0)
    fun metersPerPixel(latitude: Double, zoom: Int): Double =
        156543.03392804097 * kotlin.math.cos(latitude.coerceIn(-85.0, 85.0) * PI / 180.0) / (1 shl zoom)
    fun normalizedTileX(x: Int, zoom: Int): Int {
        val count = 1 shl zoom
        return ((x % count) + count) % count
    }
}
