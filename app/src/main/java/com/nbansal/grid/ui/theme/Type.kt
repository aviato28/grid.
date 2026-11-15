package com.nbansal.grid.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font as GoogleFontEntry
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.nbansal.grid.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun family(name: String, vararg weights: FontWeight): FontFamily =
    FontFamily(weights.map { GoogleFontEntry(GoogleFont(name), provider, it) })

val SpaceGrotesk: FontFamily = family(
    "Space Grotesk", FontWeight.Normal, FontWeight.Medium, FontWeight.Bold,
)
val JetBrainsMono: FontFamily = family(
    "JetBrains Mono", FontWeight.Normal, FontWeight.Medium,
)

val GridTypography = Typography().let { base ->
    fun TextStyle.ui() = copy(fontFamily = SpaceGrotesk)
    base.copy(
        displayLarge = base.displayLarge.ui(),
        displayMedium = base.displayMedium.ui(),
        displaySmall = base.displaySmall.ui(),
        headlineLarge = base.headlineLarge.ui(),
        headlineMedium = base.headlineMedium.ui(),
        headlineSmall = base.headlineSmall.ui(),
        titleLarge = base.titleLarge.ui(),
        titleMedium = base.titleMedium.ui(),
        titleSmall = base.titleSmall.ui(),
        bodyLarge = base.bodyLarge.ui(),
        bodyMedium = base.bodyMedium.ui(),
        bodySmall = base.bodySmall.ui(),
        labelLarge = base.labelLarge.ui(),
        labelMedium = base.labelMedium.ui(),
        labelSmall = base.labelSmall.ui(),
    )
}

/** Mono label style (RECENT, versions, sizes). */
val MonoLabel = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    letterSpacing = 1.sp,
)
