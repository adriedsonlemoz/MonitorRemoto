package br.dev.monitorremoto.core

/** Regras de entrada para gestos normalizados 0..1, sem acessar Android. */
object RemoteGesturePolicy {
    /** Ações de navegação não exigem modo toque; exigem Acessibilidade e projeção autorizadas. */
    fun isGlobalAction(action: String): Boolean =
        action in setOf("back", "home", "recents", "notifications")

    /** CENTER_CROP: o toque deve considerar as bordas cortadas da imagem. */
    fun centerCropPoint(x: Float, y: Float, viewportW: Int, viewportH: Int,
        frameW: Int, frameH: Int): Pair<Float, Float>? {
        if (!x.isFinite() || !y.isFinite() || viewportW <= 0 || viewportH <= 0 ||
            frameW <= 0 || frameH <= 0 || x < 0 || y < 0 || x > viewportW || y > viewportH) return null
        val scale = maxOf(viewportW.toFloat() / frameW, viewportH.toFloat() / frameH)
        val renderedW = frameW * scale
        val renderedH = frameH * scale
        val left = (viewportW - renderedW) / 2f
        val top = (viewportH - renderedH) / 2f
        return Pair(((x - left) / renderedW).coerceIn(0f, 1f),
            ((y - top) / renderedH).coerceIn(0f, 1f))
    }

    fun valid(action: String, x: Float, y: Float, endX: Float, endY: Float, duration: Long): Boolean =
        action in setOf("tap", "swipe", "longPress", "back", "home", "recents", "notifications") &&
            x.isFinite() && y.isFinite() && endX.isFinite() && endY.isFinite() &&
            x in 0f..1f && y in 0f..1f && endX in 0f..1f && endY in 0f..1f &&
            duration in 60L..1200L

    /** FIT_CENTER: toque em letterbox preto nunca envia comando. */
    fun fitCenterPoint(x: Float, y: Float, viewportW: Int, viewportH: Int,
        frameW: Int, frameH: Int): Pair<Float, Float>? {
        if (!x.isFinite() || !y.isFinite() || viewportW <= 0 || viewportH <= 0 ||
            frameW <= 0 || frameH <= 0) return null
        val scale = minOf(viewportW.toFloat() / frameW, viewportH.toFloat() / frameH)
        val renderedW = frameW * scale
        val renderedH = frameH * scale
        val left = (viewportW - renderedW) / 2f
        val top = (viewportH - renderedH) / 2f
        if (x < left || x > left + renderedW || y < top || y > top + renderedH) return null
        return Pair(((x - left) / renderedW).coerceIn(0f, 1f),
            ((y - top) / renderedH).coerceIn(0f, 1f))
    }
}
