package io.legado.app.ui.widget.components.reader

import androidx.compose.runtime.Immutable

enum class ReaderMenuEffect {
    None,
    Haze,
    LiquidGlass,
}

enum class ReaderMenuTintStyle {
    Fill,
    Gradient,
}

/**
 * Resolves reader-menu appearance from independent style and effect inputs.
 *
 * This deliberately contains no book-type- or settings-specific state so the reader menu keeps
 * its visual rules independent of content actions and navigation.
 */
@Immutable
data class ReaderMenuVisualState(
    val effect: ReaderMenuEffect,
    val tintStyle: ReaderMenuTintStyle,
    val styleEnabled: Boolean,
    val tintAllowed: Boolean,
    val tintFill: Boolean,
) {
    val isGradient: Boolean
        get() = styleEnabled && tintStyle == ReaderMenuTintStyle.Gradient

    val isProgressiveBlur: Boolean
        get() = effect == ReaderMenuEffect.Haze && isGradient

    val useContrastContent: Boolean
        get() = isProgressiveBlur

    val useTint: Boolean
        get() = tintAllowed && (isGradient || effect != ReaderMenuEffect.None || tintFill)
}
