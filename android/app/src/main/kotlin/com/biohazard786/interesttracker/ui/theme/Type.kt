package com.biohazard786.interesttracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.biohazard786.interesttracker.R

/**
 * Google Sans Flex (variable: wght 1–1000, wdth 25–151, ROND 0–100, opsz 1–144).
 * Expressive pairing: body, title and label text is lighter and square; emphasized styles go heavy, a touch
 * wide and fully rounded. All big text (display and headline, regular or emphasized) is 100% rounded;
 * the regular big styles run thin so the heavy numerals carry the emphasis.
 */
private fun flexFamily(weight: Int, size: Float, width: Float = 100f, round: Float = 0f): FontFamily {
  // Snap optical size to a few buckets so styles share Typeface instances instead of each loading one.
  val opsz = when {
    size <= 14f -> 12f
    size <= 22f -> 18f
    size <= 36f -> 32f
    else -> 64f
  }
  return FontFamily(
    Font(
      R.font.google_sans_flex,
      weight = FontWeight(weight),
      variationSettings =
        FontVariation.Settings(
          FontVariation.weight(weight),
          FontVariation.width(width),
          FontVariation.Setting("ROND", round),
          FontVariation.Setting("opsz", opsz),
        ),
    )
  )
}

private fun TextStyle.flex(weight: Int, width: Float = 100f, round: Float = 0f, tracking: Float? = null): TextStyle =
  copy(
    fontFamily = flexFamily(weight, fontSize.value, width, round),
    fontWeight = FontWeight(weight),
    letterSpacing = tracking?.sp ?: letterSpacing,
  )

private val base = Typography()

val Typography =
  Typography(
    displayLarge = base.displayLarge.flex(320, tracking = -1.5f, round = 100f),
    displayMedium = base.displayMedium.flex(340, tracking = -1f, round = 100f),
    displaySmall = base.displaySmall.flex(380, tracking = -0.5f, round = 100f),
    headlineLarge = base.headlineLarge.flex(420, round = 100f),
    headlineMedium = base.headlineMedium.flex(440, round = 100f),
    headlineSmall = base.headlineSmall.flex(480, round = 100f),
    titleLarge = base.titleLarge.flex(540),
    titleMedium = base.titleMedium.flex(580),
    titleSmall = base.titleSmall.flex(580),
    bodyLarge = base.bodyLarge.flex(400),
    bodyMedium = base.bodyMedium.flex(400),
    bodySmall = base.bodySmall.flex(420),
    labelLarge = base.labelLarge.flex(600),
    labelMedium = base.labelMedium.flex(600),
    labelSmall = base.labelSmall.flex(640),
    displayLargeEmphasized = base.displayLarge.flex(820, width = 108f, round = 100f, tracking = -2f),
    displayMediumEmphasized = base.displayMedium.flex(820, width = 106f, round = 100f, tracking = -1.5f),
    displaySmallEmphasized = base.displaySmall.flex(800, width = 104f, round = 100f, tracking = -1f),
    headlineLargeEmphasized = base.headlineLarge.flex(760, width = 104f, round = 100f),
    headlineMediumEmphasized = base.headlineMedium.flex(760, width = 104f, round = 100f),
    headlineSmallEmphasized = base.headlineSmall.flex(740, round = 100f),
    titleLargeEmphasized = base.titleLarge.flex(760, round = 100f),
    titleMediumEmphasized = base.titleMedium.flex(760, round = 100f),
    titleSmallEmphasized = base.titleSmall.flex(760, round = 100f),
    bodyLargeEmphasized = base.bodyLarge.flex(600),
    bodyMediumEmphasized = base.bodyMedium.flex(600),
    bodySmallEmphasized = base.bodySmall.flex(600),
    labelLargeEmphasized = base.labelLarge.flex(780, round = 100f),
    labelMediumEmphasized = base.labelMedium.flex(780, round = 100f),
    labelSmallEmphasized = base.labelSmall.flex(780, round = 100f),
  )

/** The one-off hero numeral: as loud as the font goes — black, wide, fully round, tabular. */
val HeroNumeral =
  TextStyle(
    fontFamily = flexFamily(900, 72f, width = 112f, round = 100f),
    fontWeight = FontWeight(900),
    fontSize = 72.sp,
    lineHeight = 76.sp,
    letterSpacing = (-2.5).sp,
    fontFeatureSettings = "tnum",
  )

/** Amounts in lists: tabular figures so columns of money line up. */
val Tabular = TextStyle(fontFeatureSettings = "tnum")
