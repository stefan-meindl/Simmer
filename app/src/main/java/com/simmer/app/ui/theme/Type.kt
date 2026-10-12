package com.simmer.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Font families (Downloadable Fonts via Google Fonts provider)
// The XML descriptors in res/font/ reference the Google Fonts GMS provider.
// ---------------------------------------------------------------------------

val InstrumentSerifFontFamily = FontFamily(
    Font(resId = R.font.instrument_serif, weight = FontWeight.Normal),
)

val InterFontFamily = FontFamily(
    Font(resId = R.font.inter, weight = FontWeight.Normal),
    Font(resId = R.font.inter, weight = FontWeight.Medium),
    Font(resId = R.font.inter, weight = FontWeight.SemiBold),
)

// ---------------------------------------------------------------------------
// Simmer text styles
// ---------------------------------------------------------------------------

/** Instrument Serif 40/42 sp, letter-spacing -0.5 sp. For screen-level hero titles. */
val SimmerTextStyle_ScreenTitle = TextStyle(
    fontFamily = InstrumentSerifFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 40.sp,
    lineHeight = 42.sp,
    letterSpacing = (-0.5).sp,
)

/** Instrument Serif 36/43 sp, letter-spacing -0.5 sp. For recipe/card hero titles. */
val SimmerTextStyle_HeroTitle = TextStyle(
    fontFamily = InstrumentSerifFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 36.sp,
    lineHeight = 43.2.sp,
    letterSpacing = (-0.5).sp,
)

/** Instrument Serif 36/40 sp. For large numeric displays (servings, etc.). */
val SimmerTextStyle_ServingsNumber = TextStyle(
    fontFamily = InstrumentSerifFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 36.sp,
    lineHeight = 40.sp,
    letterSpacing = 0.sp,
)

/** Inter 22/29 sp, weight 500. Section headings. */
val SimmerTextStyle_Heading4 = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 22.sp,
    lineHeight = 28.6.sp,
    letterSpacing = 0.sp,
)

/** Inter 18/25 sp, weight 500. Sub-section headings. */
val SimmerTextStyle_Heading5 = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 18.sp,
    lineHeight = 25.2.sp,
    letterSpacing = 0.sp,
)

/** Inter 16/25 sp, weight 400. Default body text. */
val SimmerTextStyle_BodyMd = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.8.sp,
    letterSpacing = 0.sp,
)

/** Inter 16/25 sp, weight 500. Emphasized body text. */
val SimmerTextStyle_BodyMdMedium = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 24.8.sp,
    letterSpacing = 0.sp,
)

/** Inter 14/21 sp, weight 400. Secondary body text. */
val SimmerTextStyle_BodySm = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 21.sp,
    letterSpacing = 0.sp,
)

/** Inter 14/21 sp, weight 500. Emphasized secondary body text. */
val SimmerTextStyle_BodySmMedium = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 21.sp,
    letterSpacing = 0.sp,
)

/** Inter 13/18 sp, weight 400. Caption text. */
val SimmerTextStyle_Caption = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.2.sp,
    letterSpacing = 0.sp,
)

/** Inter 13/18 sp, weight 600. Bold caption text. */
val SimmerTextStyle_CaptionBold = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    lineHeight = 18.2.sp,
    letterSpacing = 0.sp,
)

/** Inter 12/17 sp, weight 500. Micro labels. */
val SimmerTextStyle_Micro = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.8.sp,
    letterSpacing = 0.sp,
)

/**
 * Inter 11/15 sp, weight 600, letter-spacing +1 sp.
 * Uppercase labels — callers are responsible for calling .uppercase() on the text string,
 * as Compose has no built-in textTransform property.
 */
val SimmerTextStyle_MicroUppercase = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 15.4.sp,
    letterSpacing = 1.sp,
)

/** Inter 14/18 sp, weight 500. Button labels. */
val SimmerTextStyle_Button = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 18.2.sp,
    letterSpacing = 0.sp,
)

// ---------------------------------------------------------------------------
// Material3 Typography mapping
// ---------------------------------------------------------------------------

val Typography = Typography(
    displayLarge = SimmerTextStyle_ScreenTitle,
    displayMedium = SimmerTextStyle_HeroTitle,
    displaySmall = SimmerTextStyle_ServingsNumber,
    headlineMedium = SimmerTextStyle_Heading4,
    headlineSmall = SimmerTextStyle_Heading5,
    bodyLarge = SimmerTextStyle_BodyMd,
    bodyMedium = SimmerTextStyle_BodySm,
    bodySmall = SimmerTextStyle_Caption,
    labelLarge = SimmerTextStyle_Button,
    labelMedium = SimmerTextStyle_BodySmMedium,
    labelSmall = SimmerTextStyle_Micro,
)
