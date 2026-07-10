package com.example.webdavplayer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 应用配色：基于 Material 3 规范手动定义 light / dark 双套语义色板，
 * 主色选取沉稳的「蓝—靛—青」调（契合媒体播放器气质），并保证 AA 对比度与可读性。
 *
 * 命名约定：Light* / Dark* 前缀分别对应浅色 / 深色方案的各语义角色，
 * 实际装配在 [WebDavPlayerTheme] 中通过 lightColorScheme / darkColorScheme 完成。
 */

// ===================== 浅色方案 (Light) =====================
val LightPrimary = Color(0xFF3B5BDB)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFDEE2FF)
val LightOnPrimaryContainer = Color(0xFF00105C)

val LightSecondary = Color(0xFF565F71)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFDAE2F9)
val LightOnSecondaryContainer = Color(0xFF121B2C)

val LightTertiary = Color(0xFF6E5894)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFF8DDFE)
val LightOnTertiaryContainer = Color(0xFF27163D)

val LightBackground = Color(0xFFFDFCFF)
val LightOnBackground = Color(0xFF1A1C20)

val LightSurface = Color(0xFFFDFCFF)
val LightOnSurface = Color(0xFF1A1C20)
val LightSurfaceVariant = Color(0xFFE0E2F3)
val LightOnSurfaceVariant = Color(0xFF44474E)

val LightOutline = Color(0xFF74777F)
val LightOutlineVariant = Color(0xFFC4C6D4)

val LightInverseSurface = Color(0xFF2F3033)
val LightInverseOnSurface = Color(0xFFEFF0F4)
val LightInversePrimary = Color(0xFFAFC1FF)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val LightScrim = Color(0xFF000000)

// ===================== 深色方案 (Dark) =====================
val DarkPrimary = Color(0xFFAFC1FF)
val DarkOnPrimary = Color(0xFF05257E)
val DarkPrimaryContainer = Color(0xFF26409A)
val DarkOnPrimaryContainer = Color(0xFFDEE2FF)

val DarkSecondary = Color(0xFFBEC7DE)
val DarkOnSecondary = Color(0xFF273141)
val DarkSecondaryContainer = Color(0xFF3D4759)
val DarkOnSecondaryContainer = Color(0xFFDAE2F9)

val DarkTertiary = Color(0xFFD5BEE8)
val DarkOnTertiary = Color(0xFF3D2752)
val DarkTertiaryContainer = Color(0xFF553B6B)
val DarkOnTertiaryContainer = Color(0xFFF8DDFE)

val DarkBackground = Color(0xFF1A1C20)
val DarkOnBackground = Color(0xFFE3E2E6)

val DarkSurface = Color(0xFF1A1C20)
val DarkOnSurface = Color(0xFFE3E2E6)
val DarkSurfaceVariant = Color(0xFF44474E)
val DarkOnSurfaceVariant = Color(0xFFC4C6D4)

val DarkOutline = Color(0xFF8E9099)
val DarkOutlineVariant = Color(0xFF3E4147)

val DarkInverseSurface = Color(0xFFE3E2E6)
val DarkInverseOnSurface = Color(0xFF1A1C20)
val DarkInversePrimary = Color(0xFF3B5BDB)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkScrim = Color(0xFF000000)
