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

/** Alpha.27: linguagem visual unificada para Home, Monitorado, pareamento e configurações.
 * Os ícones usam os mesmos drawables vetoriais do painel remoto da alpha.26.
 */
internal fun MainActivity.iconTile(
    iconRes: Int,
    title: String,
    subtitle: String = "",
    featured: Boolean = false,
    onClick: () -> Unit
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER
    background = shape(if (featured) highlight else fieldColor, 20, !featured)
    setPadding(dp(8), dp(14), dp(8), dp(14))
    minimumHeight = dp(108)
    isFocusable = true
    isClickable = true
    contentDescription = if (subtitle.isBlank()) title else "$title. $subtitle"
    setOnClickListener { onClick() }
    addView(vectorIcon(iconRes, 27, if (featured) highlightText else ink).apply {
        layoutParams = LinearLayout.LayoutParams(dp(27), dp(27)).apply { gravity = Gravity.CENTER_HORIZONTAL }
    })
    addView(label(title, 13f, strong = true).apply {
        gravity = Gravity.CENTER
        setTextColor(if (featured) highlightText else ink)
        setPadding(0, dp(11), 0, 0)
        maxLines = 2
    })
    if (subtitle.isNotBlank()) addView(label(subtitle, 10.5f).apply {
        gravity = Gravity.CENTER
        setTextColor(if (featured) highlightText else dim)
        setPadding(0, dp(3), 0, 0)
        maxLines = 2
    })
}

internal fun MainActivity.iconMenuRow(
    iconRes: Int,
    title: String,
    subtitle: String = "",
    dangerous: Boolean = false,
    onClick: () -> Unit
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    background = shape(fieldColor, 18, true)
    setPadding(dp(12), dp(12), dp(12), dp(12))
    minimumHeight = dp(64)
    isClickable = true
    isFocusable = true
    contentDescription = "$title. $subtitle"
    setOnClickListener { onClick() }
    val symbol = LinearLayout(this@iconMenuRow).apply {
        background = shape(surface, 14, true)
        gravity = Gravity.CENTER
        addView(vectorIcon(iconRes, 23, if (dangerous) android.graphics.Color.rgb(186, 67, 58) else highlight))
    }
    addView(symbol, LinearLayout.LayoutParams(dp(44), dp(44)))
    addView(View(this@iconMenuRow), LinearLayout.LayoutParams(dp(10), 1))
    val words = LinearLayout(this@iconMenuRow).apply { orientation = LinearLayout.VERTICAL }
    words.addView(label(title, 14f, strong = true).apply {
        if (dangerous) setTextColor(android.graphics.Color.rgb(186, 67, 58))
        setPadding(0, 0, 0, 0)
    })
    if (subtitle.isNotBlank()) words.addView(label(subtitle, 11.5f, muted = true).apply {
        maxLines = 2
        setPadding(0, dp(3), 0, 0)
    })
    addView(words, LinearLayout.LayoutParams(0, -2, 1f))
    addView(label("›", 20f, muted = true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(6), 0, 0, 0)
    })
}

internal fun MainActivity.iconSectionHeader(iconRes: Int, title: String, subtitle: String = ""): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(vectorIcon(iconRes, 23, highlight))
        addView(View(this@iconSectionHeader), LinearLayout.LayoutParams(dp(10), 1))
        val texts = LinearLayout(this@iconSectionHeader).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(label(title, 17f, strong = true).apply { setPadding(0, 0, 0, 0) })
        if (subtitle.isNotBlank()) texts.addView(label(subtitle, 11.5f, muted = true).apply { setPadding(0, dp(3), 0, 0) })
        addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
    }

internal fun MainActivity.twoColumnTiles(parent: LinearLayout, tiles: List<LinearLayout>) {
    tiles.chunked(2).forEachIndexed { rowIndex, entries ->
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        entries.forEachIndexed { index, tile ->
            row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index == 0) rightMargin = dp(9)
            })
        }
        if (entries.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        parent.addView(row)
        if (rowIndex < (tiles.size - 1) / 2) sectionGap(parent, 9)
    }
}

internal fun MainActivity.iconMetric(labelText: String, valueText: String, iconRes: Int): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(fieldColor, 18)
        setPadding(dp(13), dp(13), dp(10), dp(13))
        minimumHeight = dp(95)
        addView(vectorIcon(iconRes, 22, highlight))
        addView(label(labelText, 11f, muted = true).apply { setPadding(0, dp(7), 0, 0) })
        addView(label(valueText, 14f, strong = true).apply {
            setPadding(0, dp(4), 0, 0)
            maxLines = 2
        })
    }

internal fun MainActivity.headerIconButton(iconRes: Int, description: String, onClick: () -> Unit): ImageView =
    ImageView(this).apply {
        setImageResource(iconRes)
        imageTintList = ColorStateList.valueOf(ink)
        background = shape(surface, 16, true)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        isClickable = true
        isFocusable = true
        contentDescription = description
        setOnClickListener { onClick() }
    }
