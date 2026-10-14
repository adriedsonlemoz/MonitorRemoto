package br.dev.monitorremoto.ui

import android.graphics.Typeface
import android.content.res.ColorStateList
import android.widget.ImageView
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Alpha.22: módulo de interface extraído de MainActivity. Sem estado de conexão próprio. */

internal fun MainActivity.dp(value: Int) = (resources.displayMetrics.density * value).toInt()
internal fun MainActivity.shape(color: Int, corner: Int = 13, stroke: Boolean = false) = GradientDrawable().apply {
    setColor(color); cornerRadius = dp(corner).toFloat()
    if (stroke) setStroke(dp(1), lineColor)
}

internal fun MainActivity.label(value: String, size: Float = 15f, strong: Boolean = false, muted: Boolean = false): TextView =
    TextView(this).apply {
        setText(value)
        textSize = size
        setTextColor(if (muted) dim else ink)
        if (strong) setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(5), 0, dp(5))
    }

internal fun MainActivity.addText(value: String, size: Float = 15f, strong: Boolean = false, muted: Boolean = false): TextView =
    label(value, size, strong, muted).also { content.addView(it) }

internal fun MainActivity.gap(px: Int = 10) { content.addView(View(this), LinearLayout.LayoutParams(1, dp(px))) }

internal fun MainActivity.button(title: String, emphasis: Boolean = false, onClick: () -> Unit): Button =
    Button(this).apply {
        text = title
        isAllCaps = false
        textSize = 14f
        setTextColor(if (emphasis) highlightText else ink)
        background = shape(if (emphasis) highlight else surface)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))
        params.bottomMargin = dp(8)
        layoutParams = params
        setOnClickListener { onClick() }
    }

internal fun MainActivity.field(hintText: String, initial: String = ""): EditText = EditText(this).apply {
    hint = hintText
    setText(initial)
    textSize = 16f
    setTextColor(ink)
    setHintTextColor(dim)
    background = shape(fieldColor, 14, true)
    setSingleLine(true)
    minHeight = dp(56)
    gravity = Gravity.CENTER_VERTICAL
    setPadding(dp(16), dp(14), dp(16), dp(14))
    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    val params = LinearLayout.LayoutParams(-1, dp(56))
    params.bottomMargin = dp(10)
    layoutParams = params
}

internal fun MainActivity.actionButton(title: String, emphasis: Boolean = false, onClick: () -> Unit): Button =
    Button(this).apply {
        text = title; isAllCaps = false; textSize = 13f
        setTextColor(if (emphasis) highlightText else ink)
        background = shape(if (emphasis) highlight else surface, 12, !emphasis)
        setPadding(dp(9), 0, dp(9), 0)
        minHeight = dp(42)
        setOnClickListener { onClick() }
    }

internal fun MainActivity.panel(pad: Int = 16): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = shape(surface, 18, true)
    setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
}

internal fun MainActivity.sectionGap(parent: LinearLayout, pixels: Int = 12) {
    parent.addView(View(this), LinearLayout.LayoutParams(1, dp(pixels)))
}

internal fun MainActivity.infoChip(value: String, accent: Boolean = false): TextView = TextView(this).apply {
    text = value
    textSize = 12f
    setTextColor(if (accent) highlightText else ink)
    background = shape(if (accent) highlight else fieldColor, 999, !accent)
    setPadding(dp(12), dp(8), dp(12), dp(8))
}

internal fun MainActivity.dialogWrapper(): Pair<ScrollView, LinearLayout> {
    val wrapper = ScrollView(this)
    val layout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(10), dp(18), dp(10))
    }
    wrapper.addView(layout)
    return wrapper to layout
}

internal fun MainActivity.dialogIntro(parent: LinearLayout, text: String) {
    if (text.isBlank()) return
    parent.addView(label(text, 12.5f, muted = true).apply { setPadding(0, 0, 0, dp(6)) })
}

internal fun MainActivity.dialogCardRow(
    title: String,
    subtitle: String = "",
    trailing: String = "›",
    accent: Boolean = false,
    onClick: (() -> Unit)? = null
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    background = shape(fieldColor, 16, true)
    setPadding(dp(14), dp(13), dp(14), dp(13))
    val textBlock = LinearLayout(this@dialogCardRow).apply { orientation = LinearLayout.VERTICAL }
    textBlock.addView(label(title, 15f, strong = true).apply {
        setTextColor(if (accent) highlight else ink)
        setPadding(0, 0, 0, if (subtitle.isBlank()) 0 else dp(2))
    })
    if (subtitle.isNotBlank()) textBlock.addView(label(subtitle, 11.5f, muted = true).apply { setPadding(0, 0, 0, 0) })
    addView(textBlock, LinearLayout.LayoutParams(0, -2, 1f))
    if (trailing.isNotBlank()) addView(label(trailing, 18f, strong = accent, muted = !accent).apply { gravity = Gravity.CENTER_VERTICAL })
    if (onClick != null) {
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }
}

internal fun MainActivity.dialogMetricRow(name: String, value: String): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setPadding(0, dp(5), 0, dp(5))
    addView(label(name, 12f, muted = true), LinearLayout.LayoutParams(0, -2, 1f))
    addView(label(value, 14f, strong = true))
}



/** Ícones vetoriais nativos com tinta temática e descrição definida pelo cartão. */
internal fun MainActivity.vectorIcon(resource: Int, size: Int, tint: Int = ink): ImageView = ImageView(this).apply {
    setImageResource(resource)
    imageTintList = ColorStateList.valueOf(tint)
    contentDescription = null
    scaleType = ImageView.ScaleType.FIT_CENTER
    layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
}
