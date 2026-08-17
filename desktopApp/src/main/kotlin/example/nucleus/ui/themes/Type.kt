package example.nucleus.ui.themes

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * PaltaSound / Material 3 Expressive type scale for dense desktop windows (~1280+).
 *
 * Starts from the platform [Typography] defaults (including `*Emphasized` roles), then
 * tightens tracking on headlines, bumps contrast on titles/labels, and applies [fontFamily]
 * everywhere so user-selected fonts stay coherent.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun paltaTypography(fontFamily: FontFamily): Typography {
    val d = Typography()

    fun TextStyle.tune(
        weight: FontWeight? = null,
        size: androidx.compose.ui.unit.TextUnit = fontSize,
        tracking: androidx.compose.ui.unit.TextUnit = letterSpacing,
        line: androidx.compose.ui.unit.TextUnit = lineHeight,
    ): TextStyle = copy(
        fontFamily = fontFamily,
        fontWeight = weight ?: fontWeight,
        fontSize = size,
        letterSpacing = tracking,
        lineHeight = line,
    )

    return d.copy(
        displayLarge = d.displayLarge.tune(weight = FontWeight.Normal, size = 52.sp, tracking = (-0.25).sp),
        displayMedium = d.displayMedium.tune(weight = FontWeight.Normal, size = 42.sp, tracking = (-0.2).sp),
        displaySmall = d.displaySmall.tune(weight = FontWeight.Normal, size = 34.sp, tracking = (-0.15).sp),

        headlineLarge = d.headlineLarge.tune(
            weight = FontWeight.Bold,
            size = 28.sp,
            tracking = (-0.4).sp,
            line = 34.sp,
        ),
        headlineMedium = d.headlineMedium.tune(
            weight = FontWeight.Bold,
            size = 24.sp,
            tracking = (-0.3).sp,
            line = 30.sp,
        ),
        headlineSmall = d.headlineSmall.tune(
            weight = FontWeight.SemiBold,
            size = 20.sp,
            tracking = (-0.2).sp,
            line = 26.sp,
        ),

        titleLarge = d.titleLarge.tune(
            weight = FontWeight.SemiBold,
            size = 20.sp,
            tracking = (-0.15).sp,
            line = 26.sp,
        ),
        titleMedium = d.titleMedium.tune(
            weight = FontWeight.Medium,
            size = 16.sp,
            tracking = 0.sp,
            line = 22.sp,
        ),
        titleSmall = d.titleSmall.tune(
            weight = FontWeight.Medium,
            size = 14.sp,
            tracking = 0.sp,
            line = 20.sp,
        ),

        bodyLarge = d.bodyLarge.tune(size = 15.sp, tracking = 0.1.sp, line = 22.sp),
        bodyMedium = d.bodyMedium.tune(size = 13.5.sp, tracking = 0.1.sp, line = 20.sp),
        bodySmall = d.bodySmall.tune(size = 12.sp, tracking = 0.15.sp, line = 16.sp),

        labelLarge = d.labelLarge.tune(weight = FontWeight.Medium, size = 13.sp, tracking = 0.1.sp),
        labelMedium = d.labelMedium.tune(weight = FontWeight.Medium, size = 11.5.sp, tracking = 0.15.sp),
        labelSmall = d.labelSmall.tune(weight = FontWeight.Medium, size = 10.5.sp, tracking = 0.2.sp),

        // Emphasized: higher weight / slightly larger — primary hierarchy (screen titles, CTAs).
        displayLargeEmphasized = d.displayLargeEmphasized.tune(
            weight = FontWeight.Bold,
            size = 54.sp,
            tracking = (-0.35).sp,
        ),
        displayMediumEmphasized = d.displayMediumEmphasized.tune(
            weight = FontWeight.Bold,
            size = 44.sp,
            tracking = (-0.3).sp,
        ),
        displaySmallEmphasized = d.displaySmallEmphasized.tune(
            weight = FontWeight.Bold,
            size = 36.sp,
            tracking = (-0.25).sp,
        ),
        headlineLargeEmphasized = d.headlineLargeEmphasized.tune(
            weight = FontWeight.ExtraBold,
            size = 30.sp,
            tracking = (-0.45).sp,
            line = 36.sp,
        ),
        headlineMediumEmphasized = d.headlineMediumEmphasized.tune(
            weight = FontWeight.ExtraBold,
            size = 26.sp,
            tracking = (-0.35).sp,
            line = 32.sp,
        ),
        headlineSmallEmphasized = d.headlineSmallEmphasized.tune(
            weight = FontWeight.Bold,
            size = 22.sp,
            tracking = (-0.25).sp,
            line = 28.sp,
        ),
        titleLargeEmphasized = d.titleLargeEmphasized.tune(
            weight = FontWeight.Bold,
            size = 21.sp,
            tracking = (-0.2).sp,
            line = 27.sp,
        ),
        titleMediumEmphasized = d.titleMediumEmphasized.tune(
            weight = FontWeight.SemiBold,
            size = 16.5.sp,
            tracking = (-0.05).sp,
            line = 22.sp,
        ),
        titleSmallEmphasized = d.titleSmallEmphasized.tune(
            weight = FontWeight.SemiBold,
            size = 14.5.sp,
            tracking = 0.sp,
            line = 20.sp,
        ),
        bodyLargeEmphasized = d.bodyLargeEmphasized.tune(
            weight = FontWeight.Medium,
            size = 15.sp,
            tracking = 0.05.sp,
        ),
        bodyMediumEmphasized = d.bodyMediumEmphasized.tune(
            weight = FontWeight.Medium,
            size = 13.5.sp,
        ),
        bodySmallEmphasized = d.bodySmallEmphasized.tune(
            weight = FontWeight.Medium,
            size = 12.sp,
        ),
        labelLargeEmphasized = d.labelLargeEmphasized.tune(
            weight = FontWeight.SemiBold,
            size = 13.5.sp,
            tracking = 0.05.sp,
        ),
        labelMediumEmphasized = d.labelMediumEmphasized.tune(
            weight = FontWeight.SemiBold,
            size = 12.sp,
        ),
        labelSmallEmphasized = d.labelSmallEmphasized.tune(
            weight = FontWeight.SemiBold,
            size = 11.sp,
        ),
    )
}

/** Primary screen / section title token (Home, Library, Settings root, etc.). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Typography.screenTitle: TextStyle
    get() = headlineMediumEmphasized

/** Song title in mini player / compact chrome. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Typography.songTitle: TextStyle
    get() = titleLargeEmphasized

/** Now Playing hero song title. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Typography.nowPlayingTitle: TextStyle
    get() = headlineSmallEmphasized

/** Primary text CTA label. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Typography.ctaLabel: TextStyle
    get() = labelLargeEmphasized
