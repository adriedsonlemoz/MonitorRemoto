package br.dev.monitorremoto.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import br.dev.monitorremoto.core.MapProjection
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedHashMap
import java.util.concurrent.Executors
import kotlin.math.floor

/** Mapa nativo: blocos apenas da área visível, cache de blocos vistos, sem download em massa.
 * Nunca busca a localização do aparelho local. O marcador vem do snapshot AES-GCM autorizado.
 */
internal class RemoteMapView(context: Context) : View(context) {
    private val land = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(231, 238, 241) }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(205, 217, 225); strokeWidth = resources.displayMetrics.density }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(22, 156, 142) }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3 * resources.displayMetrics.density }
    private val accuracyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 30, 110, 190) }
    private val accuracyEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(55, 124, 194); style = Paint.Style.STROKE; strokeWidth = 1.4f * resources.displayMetrics.density }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(78, 95, 113); textSize = 12f * resources.displayMetrics.scaledDensity; textAlign = Paint.Align.CENTER }
    private val tileExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "MonitorRemoto-map-tiles").apply { isDaemon = true } }
    private val pending = mutableSetOf<String>()
    private val memory = object : LinkedHashMap<String, Bitmap>(24, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 28
    }
    private val directory = File(context.cacheDir, "map_tiles_seen").apply { mkdirs() }
    private val density = resources.displayMetrics.density
    private var targetLat: Double? = null
    private var targetLon: Double? = null
    private var accuracyM: Double? = null
    // Overview is only a reference map, never the remote device's assumed position.
    private var centerLat = -14.235
    private var centerLon = -51.925
    private var zoom = 4
    private var initialized = true
    private var hasFix = false
    private var scaleAccumulator = 1f
    private var dragX = 0f
    private var dragY = 0f
    @Volatile private var canNetwork = false
    private var follow = true
    var onFollowChanged: ((Boolean) -> Unit)? = null
    @Volatile private var disposed = false
    private val gesture = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleAccumulator *= detector.scaleFactor
            if (scaleAccumulator >= 1.17f) { zoomBy(1); scaleAccumulator = 1f }
            else if (scaleAccumulator <= .85f) { zoomBy(-1); scaleAccumulator = 1f }
            return true
        }
    })

    /** Ativar rede exige toque explícito; visualizações anteriores continuam no cache. */
    fun enableOnline(enabled: Boolean) { canNetwork = enabled; invalidate() }
    fun setFollow(enabled: Boolean) {
        follow = enabled
        if (enabled) centerOnMarker()
        onFollowChanged?.invoke(enabled)
    }
    fun isFollowing(): Boolean = follow
    fun setPosition(lat: Double, lon: Double, accuracy: Double?) {
        if (!br.dev.monitorremoto.core.LocationSharePolicy.coordinatesValid(lat, lon)) return
        targetLat = lat; targetLon = lon
        accuracyM = accuracy?.takeIf { it.isFinite() && it >= 0.0 }
        if (!hasFix || follow) {
            centerLat = MapProjection.latitude(lat); centerLon = MapProjection.longitude(lon)
            if (!hasFix) zoom = 15
            initialized = true
        }
        hasFix = true
        invalidate()
    }
    fun clearPosition() { targetLat = null; targetLon = null; accuracyM = null; invalidate() }
    fun centerOnMarker() {
        val a = targetLat ?: return
        val b = targetLon ?: return
        centerLat = MapProjection.latitude(a); centerLon = MapProjection.longitude(b); initialized = true; invalidate()
    }
    fun zoomBy(step: Int) { zoom = (zoom + step).coerceIn(MapProjection.MIN_ZOOM, MapProjection.MAX_ZOOM); invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(239, 244, 247))
        // Keep displaying the map without a remote fix; do not draw a false marker.
        val cx = MapProjection.x(centerLon, zoom)
        val cy = MapProjection.y(centerLat, zoom)
        val count = 1 shl zoom
        val left = cx - width / 2.0
        val top = cy - height / 2.0
        val x0 = floor(left / 256.0).toInt()
        val y0 = floor(top / 256.0).toInt()
        val x1 = floor((left + width) / 256.0).toInt()
        val y1 = floor((top + height) / 256.0).toInt()
        // Jamais fazer prefetch: solicitar apenas os blocos que o usuário está vendo.
        for (y in y0..y1) for (x in x0..x1) {
            val px = (x * 256.0 - left).toFloat()
            val py = (y * 256.0 - top).toFloat()
            val rect = RectF(px, py, px + 256f, py + 256f)
            canvas.drawRect(rect, land)
            canvas.drawRect(rect, grid)
            if (y !in 0 until count) continue
            val nx = MapProjection.normalizedTileX(x, zoom)
            val key = "${zoom}_${nx}_${y}"
            val tile = synchronized(memory) { memory[key] }
            if (tile != null && !tile.isRecycled) canvas.drawBitmap(tile, null, rect, null)
            else requestTile(key, zoom, nx, y)
        }
        val a = targetLat
        val b = targetLon
        if (a != null && b != null) {
            var deltaX = MapProjection.x(b, zoom) - cx
            val world = MapProjection.scale(zoom)
            if (deltaX > world / 2) deltaX -= world
            if (deltaX < -world / 2) deltaX += world
            val px = (width / 2.0 + deltaX).toFloat()
            val py = (height / 2.0 + MapProjection.y(a, zoom) - cy).toFloat()
            accuracyM?.let {
                val radius = (it / MapProjection.metersPerPixel(a, zoom)).toFloat().coerceIn(0f, 2000f)
                canvas.drawCircle(px, py, radius, accuracyFill)
                canvas.drawCircle(px, py, radius, accuracyEdge)
            }
            canvas.drawCircle(px, py, 9 * density, dot)
            canvas.drawCircle(px, py, 9 * density, white)
        }
        if (targetLat == null) {
            val label = if (canNetwork) "MAPA DE REFERÊNCIA  ·  SEM POSIÇÃO REMOTA" else "ATIVAR MAPA ONLINE  ·  SEM GPS REMOTO"
            val overlay = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 18, 33, 48) }
            val rect = RectF(12 * density, 12 * density, width - 12 * density, 45 * density)
            canvas.drawRoundRect(rect, 9 * density, 9 * density, overlay)
            val textPaint = Paint(hint).apply { color = Color.WHITE; textSize = 10f * resources.displayMetrics.scaledDensity }
            canvas.drawText(label, width / 2f, 33 * density, textPaint)
        }
        if (!canNetwork) canvas.drawText("Mapa online desativado · cache já visualizado", width / 2f, height - 26 * density, hint)
    }

    private fun requestTile(key: String, z: Int, x: Int, y: Int) {
        if (disposed) return
        synchronized(pending) { if (!pending.add(key)) return }
        tileExecutor.execute {
            try {
                val file = File(directory, "$key.png")
                val fresh = file.isFile && System.currentTimeMillis() - file.lastModified() < 7L * 24 * 3600 * 1000
                if (!fresh && canNetwork && !disposed) downloadTile(file, z, x, y)
                if (file.isFile && file.length() in 1..150_000) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        synchronized(memory) { memory[key] = bitmap }
                        postInvalidate()
                    }
                }
            } catch (_: Exception) { /* Ausência de internet nunca oculta a posição. */ }
            finally { synchronized(pending) { pending.remove(key) } }
        }
    }

    private fun downloadTile(file: File, z: Int, x: Int, y: Int) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as HttpURLConnection
            connection.connectTimeout = 4500; connection.readTimeout = 4500
            connection.setRequestProperty("User-Agent", "MonitorRemoto-Android/0.1.0-alpha.87 (+https://github.com/adriedsonlemoz)")
            if (file.exists()) connection.ifModifiedSince = file.lastModified()
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) { file.setLastModified(System.currentTimeMillis()); return }
            if (code != HttpURLConnection.HTTP_OK || (connection.contentLengthLong > 150_000L)) return
            val bytes = connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > 150_000) return
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            if (bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) {
                File(directory, "${file.name}.tmp").apply { writeBytes(bytes); if (!renameTo(file)) delete() }
            }
        } finally { connection?.disconnect() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gesture.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { dragX = event.x; dragY = event.y; parent?.requestDisallowInterceptTouchEvent(true); return true }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && initialized && !gesture.isInProgress) {
                    val dx = (event.x - dragX).toDouble()
                    val dy = (event.y - dragY).toDouble()
                    centerLon = MapProjection.lon(MapProjection.x(centerLon, zoom) - dx, zoom)
                    centerLat = MapProjection.lat((MapProjection.y(centerLat, zoom) - dy).coerceIn(0.0, MapProjection.scale(zoom)), zoom)
                    if (follow) { follow = false; onFollowChanged?.invoke(false) }
                    invalidate()
                }
                dragX = event.x; dragY = event.y
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                dragX = event.x; dragY = event.y; return true
            }
            MotionEvent.ACTION_UP -> { performClick(); parent?.requestDisallowInterceptTouchEvent(false); return true }
            MotionEvent.ACTION_CANCEL -> { parent?.requestDisallowInterceptTouchEvent(false); return true }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    fun dispose() { disposed = true; tileExecutor.shutdownNow(); synchronized(memory) { memory.clear() } }
}
