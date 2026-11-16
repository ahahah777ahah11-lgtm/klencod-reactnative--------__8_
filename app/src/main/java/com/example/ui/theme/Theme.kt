package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class AppThemeMode(val titleAr: String) {
    SYSTEM("تلقائي حسب النظام"),
    LIGHT("الوضع الفاتح"),
    DARK("الوضع الداكن"),
}

enum class AppColorPalette(
    val titleAr: String,
    val previewPrimary: Color,
    val previewAccent: Color,
) {
    OFFICIAL_NAVY_TEAL("الأزرق المالي", NavyDark, TurquoiseAccent),
    EMERALD_FINANCE("الأخضر الزمردي", EmeraldPrimary, EmeraldAccent),
    ROYAL_INDIGO("النيلي الملكي", IndigoPrimary, IndigoAccent),
    AMBER_GOLD("الذهبي الكهرماني", AmberPrimary, AmberAccent),
    RED_WHITE("الأحمر والأبيض", CrimsonPrimary, CrimsonSecondary),
}

enum class AppBackgroundStyle(
    val titleAr: String,
    val lightPreview: Color,
    val darkPreview: Color,
) {
    THEME_DEFAULT("متناسق مع الثيم", SoftIceBackground, DarkBackground),
    PURE_CRISP("نقي واضح", Color(0xFFFFFFFF), Color(0xFF0A0F14)),
    WARM_CREAM("كريمي دافئ", WarmCreamBg, WarmEspressoBg),
    MINT_CALM("نعناعي هادئ", MintCalmBg, DeepPineBg),
    SLATE_FROST("رمادي أزرق عصري", SlateFrostBg, MidnightSlateBg),
    DEEP_AMOLED("تباين فائق AMOLED", Color(0xFFF2F5FA), Color(0xFF000000)),
}

enum class AppIconShape(
    val titleAr: String,
    val shape: Shape,
) {
    SQUIRCLE("مربع ناعم متدرج", RoundedCornerShape(12.dp)),
    LEAF_ASYMMETRIC("ورقة عصرية مائلة", RoundedCornerShape(topStart = 14.dp, bottomEnd = 14.dp, topEnd = 4.dp, bottomStart = 4.dp)),
    DIAMOND_CUT("معيّن مقصوص", CutCornerShape(10.dp)),
    CIRCLE("دائري كلاسيكي", CircleShape),
}

@Immutable
data class FinancialColors(
    val success: Color,
    val onSuccess: Color,
    val danger: Color,
    val onDanger: Color,
    val warning: Color,
    val orange: Color,
    val turquoise: Color,
    val heroStart: Color,
    val heroEnd: Color,
)

val LocalFinancialColors = staticCompositionLocalOf {
    FinancialColors(
        success = SuccessGreen,
        onSuccess = PureWhite,
        danger = DangerRed,
        onDanger = PureWhite,
        warning = WarningYellow,
        orange = AlertOrange,
        turquoise = TurquoiseAccent,
        heroStart = NavyDark,
        heroEnd = OceanBlue,
    )
}

val LocalAppIconShape = staticCompositionLocalOf<Shape> { AppIconShape.SQUIRCLE.shape }
val LocalAppIconShapeStyle = staticCompositionLocalOf { AppIconShape.SQUIRCLE }

private val OfficialLightColorScheme = lightColorScheme(
    primary = NavyDark,
    onPrimary = PureWhite,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = NavyDark,
    secondary = OceanBlue,
    onSecondary = PureWhite,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = NavyDark,
    tertiary = TurquoiseAccent,
    onTertiary = NavyDark,
    background = SoftIceBackground,
    onBackground = OnSurfaceLight,
    surface = PureWhite,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = DangerRed,
    onError = PureWhite,
)

private val OfficialDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkBackground,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = PureWhite,
    secondary = DarkSecondary,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = OnDarkSurface,
    tertiary = TurquoiseAccent,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = OnDarkSurface,
    surface = DarkSurface,
    onSurface = OnDarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkSurfaceVariant,
    outline = DarkOutline,
    error = Color(0xFFFF6B6B),
    onError = DarkBackground,
)

private val EmeraldLightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = PureWhite,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = EmeraldPrimary,
    secondary = EmeraldSecondary,
    onSecondary = PureWhite,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = EmeraldPrimary,
    tertiary = EmeraldAccent,
    onTertiary = EmeraldPrimary,
    background = EmeraldLightBg,
    onBackground = Color(0xFF0A2419),
    surface = PureWhite,
    onSurface = Color(0xFF0A2419),
    surfaceVariant = Color(0xFFE0EFE8),
    onSurfaceVariant = Color(0xFF3E6352),
    outline = Color(0xFFB7D7C7),
    error = DangerRed,
    onError = PureWhite,
)

private val EmeraldDarkColorScheme = darkColorScheme(
    primary = EmeraldDarkPrimary,
    onPrimary = EmeraldDarkBg,
    primaryContainer = Color(0xFF104B34),
    onPrimaryContainer = PureWhite,
    secondary = EmeraldAccent,
    onSecondary = EmeraldDarkBg,
    secondaryContainer = Color(0xFF17382B),
    onSecondaryContainer = Color(0xFFE6FBF2),
    tertiary = WarningYellow,
    onTertiary = EmeraldDarkBg,
    background = EmeraldDarkBg,
    onBackground = Color(0xFFE6F7EF),
    surface = EmeraldDarkSurface,
    onSurface = Color(0xFFE6F7EF),
    surfaceVariant = Color(0xFF1A362B),
    onSurfaceVariant = Color(0xFFA6CEC0),
    outline = Color(0xFF2F5B49),
    error = Color(0xFFFF6B6B),
    onError = EmeraldDarkBg,
)

private val IndigoLightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = PureWhite,
    primaryContainer = IndigoContainerLight,
    onPrimaryContainer = IndigoPrimary,
    secondary = IndigoSecondary,
    onSecondary = PureWhite,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = IndigoPrimary,
    tertiary = IndigoAccent,
    onTertiary = IndigoPrimary,
    background = IndigoLightBg,
    onBackground = Color(0xFF14132C),
    surface = PureWhite,
    onSurface = Color(0xFF14132C),
    surfaceVariant = Color(0xFFE6E8F6),
    onSurfaceVariant = Color(0xFF494D72),
    outline = Color(0xFFC5C9E8),
    error = DangerRed,
    onError = PureWhite,
)

private val IndigoDarkColorScheme = darkColorScheme(
    primary = IndigoDarkPrimary,
    onPrimary = IndigoDarkBg,
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = PureWhite,
    secondary = IndigoAccent,
    onSecondary = IndigoDarkBg,
    secondaryContainer = Color(0xFF232545),
    onSecondaryContainer = Color(0xFFECEEFE),
    tertiary = EmeraldAccent,
    onTertiary = IndigoDarkBg,
    background = IndigoDarkBg,
    onBackground = Color(0xFFECEEFE),
    surface = IndigoDarkSurface,
    onSurface = Color(0xFFECEEFE),
    surfaceVariant = Color(0xFF232545),
    onSurfaceVariant = Color(0xFFB4B9E0),
    outline = Color(0xFF3E4273),
    error = Color(0xFFFF6B6B),
    onError = IndigoDarkBg,
)

private val AmberLightColorScheme = lightColorScheme(
    primary = AmberPrimary,
    onPrimary = PureWhite,
    primaryContainer = AmberContainerLight,
    onPrimaryContainer = AmberPrimary,
    secondary = AmberSecondary,
    onSecondary = PureWhite,
    secondaryContainer = Color(0xFFFFEDD5),
    onSecondaryContainer = AmberPrimary,
    tertiary = AmberAccent,
    onTertiary = AmberPrimary,
    background = AmberLightBg,
    onBackground = Color(0xFF261807),
    surface = PureWhite,
    onSurface = Color(0xFF261807),
    surfaceVariant = Color(0xFFF3E8D5),
    onSurfaceVariant = Color(0xFF6B5335),
    outline = Color(0xFFDFCCA9),
    error = DangerRed,
    onError = PureWhite,
)

private val AmberDarkColorScheme = darkColorScheme(
    primary = AmberDarkPrimary,
    onPrimary = AmberDarkBg,
    primaryContainer = Color(0xFF5C3409),
    onPrimaryContainer = PureWhite,
    secondary = AmberAccent,
    onSecondary = AmberDarkBg,
    secondaryContainer = Color(0xFF362815),
    onSecondaryContainer = Color(0xFFFEF5E7),
    tertiary = TurquoiseAccent,
    onTertiary = AmberDarkBg,
    background = AmberDarkBg,
    onBackground = Color(0xFFFEF5E7),
    surface = AmberDarkSurface,
    onSurface = Color(0xFFFEF5E7),
    surfaceVariant = Color(0xFF362815),
    onSurfaceVariant = Color(0xFFD8C2A2),
    outline = Color(0xFF5D4626),
    error = Color(0xFFFF6B6B),
    onError = AmberDarkBg,
)

private val RedWhiteLightColorScheme = lightColorScheme(
    primary = CrimsonPrimary,
    onPrimary = PureWhite,
    primaryContainer = CrimsonContainerLight,
    onPrimaryContainer = CrimsonDark,
    secondary = CrimsonDark,
    onSecondary = PureWhite,
    secondaryContainer = Color(0xFFFFEDE8),
    onSecondaryContainer = CrimsonDark,
    tertiary = CrimsonSecondary,
    onTertiary = PureWhite,
    background = CrimsonLightSurface,
    onBackground = Color(0xFF2B1212),
    surface = PureWhite,
    onSurface = Color(0xFF2B1212),
    surfaceVariant = Color(0xFFF3E2E2),
    onSurfaceVariant = Color(0xFF6B4848),
    outline = Color(0xFFDDC2C2),
    error = DangerRed,
    onError = PureWhite,
)

private val RedWhiteDarkColorScheme = darkColorScheme(
    primary = CrimsonDarkPrimary,
    onPrimary = CrimsonDarkBackground,
    primaryContainer = CrimsonDark,
    onPrimaryContainer = PureWhite,
    secondary = CrimsonSecondary,
    onSecondary = CrimsonDarkBackground,
    secondaryContainer = Color(0xFF3A1D20),
    onSecondaryContainer = PureWhite,
    tertiary = WarningYellow,
    onTertiary = CrimsonDarkBackground,
    background = CrimsonDarkBackground,
    onBackground = Color(0xFFFCECEC),
    surface = CrimsonDarkSurface,
    onSurface = Color(0xFFFCECEC),
    surfaceVariant = Color(0xFF331C1F),
    onSurfaceVariant = Color(0xFFD9B8BA),
    outline = Color(0xFF5D3539),
    error = Color(0xFFFF6B6B),
    onError = CrimsonDarkBackground,
)

private fun applyBackgroundStyle(
    base: ColorScheme,
    backgroundStyle: AppBackgroundStyle,
    isDark: Boolean,
): ColorScheme {
    return when (backgroundStyle) {
        AppBackgroundStyle.THEME_DEFAULT -> base
        AppBackgroundStyle.PURE_CRISP -> if (isDark) {
            base.copy(
                background = Color(0xFF0A0F14),
                onBackground = Color(0xFFF1F5F9),
                surface = Color(0xFF141B24),
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = Color(0xFF1F2A38),
                onSurfaceVariant = Color(0xFFB6C4D6),
            )
        } else {
            base.copy(
                background = Color(0xFFFFFFFF),
                onBackground = Color(0xFF0F172A),
                surface = Color(0xFFF7F9FC),
                onSurface = Color(0xFF0F172A),
                surfaceVariant = Color(0xFFEBF0F7),
                onSurfaceVariant = Color(0xFF475569),
            )
        }
        AppBackgroundStyle.WARM_CREAM -> if (isDark) {
            base.copy(
                background = WarmEspressoBg,
                onBackground = Color(0xFFF7EFE4),
                surface = WarmEspressoSurface,
                onSurface = Color(0xFFF7EFE4),
                surfaceVariant = Color(0xFF31281E),
                onSurfaceVariant = Color(0xFFD4C5B4),
            )
        } else {
            base.copy(
                background = WarmCreamBg,
                onBackground = Color(0xFF281F14),
                surface = WarmCreamSurface,
                onSurface = Color(0xFF281F14),
                surfaceVariant = Color(0xFFEFE5D2),
                onSurfaceVariant = Color(0xFF635340),
            )
        }
        AppBackgroundStyle.MINT_CALM -> if (isDark) {
            base.copy(
                background = DeepPineBg,
                onBackground = Color(0xFFE7F6F0),
                surface = DeepPineSurface,
                onSurface = Color(0xFFE7F6F0),
                surfaceVariant = Color(0xFF1B352D),
                onSurfaceVariant = Color(0xFFB0D3C6),
            )
        } else {
            base.copy(
                background = MintCalmBg,
                onBackground = Color(0xFF11261E),
                surface = MintCalmSurface,
                onSurface = Color(0xFF11261E),
                surfaceVariant = Color(0xFFDCEEE6),
                onSurfaceVariant = Color(0xFF436457),
            )
        }
        AppBackgroundStyle.SLATE_FROST -> if (isDark) {
            base.copy(
                background = MidnightSlateBg,
                onBackground = Color(0xFFE9EFF7),
                surface = MidnightSlateSurface,
                onSurface = Color(0xFFE9EFF7),
                surfaceVariant = Color(0xFF213044),
                onSurfaceVariant = Color(0xFFB3C3D8),
            )
        } else {
            base.copy(
                background = SlateFrostBg,
                onBackground = Color(0xFF162233),
                surface = SlateFrostSurface,
                onSurface = Color(0xFF162233),
                surfaceVariant = Color(0xFFDCE5F0),
                onSurfaceVariant = Color(0xFF4B5E75),
            )
        }
        AppBackgroundStyle.DEEP_AMOLED -> if (isDark) {
            base.copy(
                background = Color(0xFF000000),
                onBackground = Color(0xFFF8FAFC),
                surface = Color(0xFF11151B),
                onSurface = Color(0xFFF8FAFC),
                surfaceVariant = Color(0xFF1D232E),
                onSurfaceVariant = Color(0xFFB8C4D4),
            )
        } else {
            base.copy(
                background = Color(0xFFF2F5FA),
                onBackground = Color(0xFF090F17),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF090F17),
                surfaceVariant = Color(0xFFE2E8F0),
                onSurfaceVariant = Color(0xFF334155),
            )
        }
    }
}

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun shapesForIconStyle(iconShape: AppIconShape): Shapes {
    return when (iconShape) {
        AppIconShape.DIAMOND_CUT -> Shapes(
            small = CutCornerShape(8.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(26.dp),
        )
        AppIconShape.LEAF_ASYMMETRIC -> Shapes(
            small = RoundedCornerShape(topStart = 12.dp, bottomEnd = 12.dp, topEnd = 4.dp, bottomStart = 4.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(28.dp),
        )
        else -> AppShapes
    }
}

@Composable
fun SmartDebtTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    colorPalette: AppColorPalette = AppColorPalette.OFFICIAL_NAVY_TEAL,
    backgroundStyle: AppBackgroundStyle = AppBackgroundStyle.THEME_DEFAULT,
    iconShape: AppIconShape = AppIconShape.SQUIRCLE,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val baseScheme = when (colorPalette) {
        AppColorPalette.OFFICIAL_NAVY_TEAL -> if (isDark) OfficialDarkColorScheme else OfficialLightColorScheme
        AppColorPalette.EMERALD_FINANCE -> if (isDark) EmeraldDarkColorScheme else EmeraldLightColorScheme
        AppColorPalette.ROYAL_INDIGO -> if (isDark) IndigoDarkColorScheme else IndigoLightColorScheme
        AppColorPalette.AMBER_GOLD -> if (isDark) AmberDarkColorScheme else AmberLightColorScheme
        AppColorPalette.RED_WHITE -> if (isDark) RedWhiteDarkColorScheme else RedWhiteLightColorScheme
    }
    val colorScheme = applyBackgroundStyle(baseScheme, backgroundStyle, isDark)

    val financialColors = when (colorPalette) {
        AppColorPalette.OFFICIAL_NAVY_TEAL -> FinancialColors(
            success = if (isDark) Color(0xFF38B87C) else SuccessGreen,
            onSuccess = PureWhite,
            danger = if (isDark) Color(0xFFFF6B6B) else DangerRed,
            onDanger = PureWhite,
            warning = WarningYellow,
            orange = AlertOrange,
            turquoise = TurquoiseAccent,
            heroStart = NavyDark,
            heroEnd = OceanBlue,
        )
        AppColorPalette.EMERALD_FINANCE -> FinancialColors(
            success = if (isDark) Color(0xFF4ADE80) else SuccessGreen,
            onSuccess = PureWhite,
            danger = if (isDark) Color(0xFFFF6B6B) else DangerRed,
            onDanger = PureWhite,
            warning = WarningYellow,
            orange = AlertOrange,
            turquoise = EmeraldAccent,
            heroStart = EmeraldPrimary,
            heroEnd = EmeraldSecondary,
        )
        AppColorPalette.ROYAL_INDIGO -> FinancialColors(
            success = if (isDark) Color(0xFF38B87C) else SuccessGreen,
            onSuccess = PureWhite,
            danger = if (isDark) Color(0xFFFF6B6B) else DangerRed,
            onDanger = PureWhite,
            warning = WarningYellow,
            orange = AlertOrange,
            turquoise = IndigoAccent,
            heroStart = IndigoPrimary,
            heroEnd = IndigoSecondary,
        )
        AppColorPalette.AMBER_GOLD -> FinancialColors(
            success = if (isDark) Color(0xFF38B87C) else SuccessGreen,
            onSuccess = PureWhite,
            danger = if (isDark) Color(0xFFFF6B6B) else DangerRed,
            onDanger = PureWhite,
            warning = AmberAccent,
            orange = AmberSecondary,
            turquoise = AmberAccent,
            heroStart = AmberPrimary,
            heroEnd = AmberSecondary,
        )
        AppColorPalette.RED_WHITE -> FinancialColors(
            success = if (isDark) Color(0xFF38B87C) else SuccessGreen,
            onSuccess = PureWhite,
            danger = if (isDark) Color(0xFFFF6B6B) else DangerRed,
            onDanger = PureWhite,
            warning = WarningYellow,
            orange = AlertOrange,
            turquoise = if (isDark) Color(0xFFFFB088) else CrimsonSecondary,
            heroStart = CrimsonDark,
            heroEnd = CrimsonPrimary,
        )
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalFinancialColors provides financialColors,
        LocalAppIconShape provides iconShape.shape,
        LocalAppIconShapeStyle provides iconShape,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = shapesForIconStyle(iconShape),
            content = content,
        )
    }
}
