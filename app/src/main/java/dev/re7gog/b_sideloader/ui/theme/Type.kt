package dev.re7gog.b_sideloader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.re7gog.b_sideloader.R

@OptIn(ExperimentalTextApi::class)
val RubikVariable = FontFamily(
    // Normal
    Font(
        resId = R.font.rubik_variable_wght,
        weight = FontWeight.W100, // Thin
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(100))
    ),
    Font(
        resId = R.font.rubik_variable_wght,
        weight = FontWeight.W400, // Regular
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        resId = R.font.rubik_variable_wght,
        weight = FontWeight.W500, // Medium
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        resId = R.font.rubik_variable_wght,
        weight = FontWeight.W700, // Bold
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
    Font(
        resId = R.font.rubik_variable_wght,
        weight = FontWeight.W900, // Black
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(900))
    ),
    // Italic
    Font(
        resId = R.font.rubik_variable_wght,
        weight = FontWeight.W100, // Thin
        style = FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(100))
    ),
    Font(
        resId = R.font.rubik_italic_variable_wght,
        weight = FontWeight.W400, // Regular
        style = FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        resId = R.font.rubik_italic_variable_wght,
        weight = FontWeight.W500, // Medium
        style = FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        resId = R.font.rubik_italic_variable_wght,
        weight = FontWeight.W700, // Bold
        style = FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
    Font(
        resId = R.font.rubik_italic_variable_wght,
        weight = FontWeight.W900, // Black
        style = FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(900))
    )
)

val OrelegaOne = FontFamily(
    Font(R.font.orelega_one)
)

private val defaultTypography = Typography()
val Typography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = OrelegaOne),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = OrelegaOne),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = OrelegaOne),

    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = OrelegaOne),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = OrelegaOne),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = OrelegaOne),

    titleLarge = defaultTypography.titleLarge.copy(fontFamily = OrelegaOne),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = OrelegaOne),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = OrelegaOne),

    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = RubikVariable),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = RubikVariable),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = RubikVariable),

    labelLarge = defaultTypography.labelLarge.copy(fontFamily = RubikVariable),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = RubikVariable),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = RubikVariable)
)