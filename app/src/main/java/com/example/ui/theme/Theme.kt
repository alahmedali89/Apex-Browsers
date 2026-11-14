package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

val LocalApexDarkTheme = compositionLocalOf<Boolean?> { null }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ApexBrowserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedPreset: SeedPalettePreset = SeedPalettePreset.ELECTRIC_COBALT,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val reducedMotion = rememberSystemReducedMotion()

    val colorScheme = remember(dynamicColor, darkTheme, seedPreset, context) {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            else -> ApexColorSchemeBuilder.fromSeed(seedPreset.seedColor, darkTheme)
        }
    }

    CompositionLocalProvider(
        LocalReducedMotion provides reducedMotion,
        LocalApexDarkTheme provides darkTheme
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = ApexTypography,
            shapes = ApexShapes,
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    ApexBrowserTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        content = content
    )
}
