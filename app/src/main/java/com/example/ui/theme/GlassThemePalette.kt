package com.example.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

enum class GlassThemeOption(
    val key: String,
    val labelAr: String,
    val primary: Color,
    val secondary: Color
) {
    CYAN("CYAN", "سماوي", Color(0xFF38BDF8), Color(0xFF3B82F6)),
    EMERALD("EMERALD", "زمردي", Color(0xFF10B981), Color(0xFF34D399)),
    VIOLET("VIOLET", "بنفسجي", Color(0xFFA855F7), Color(0xFF6366F1)),
    GOLD("GOLD", "ذهبي", Color(0xFFF59E0B), Color(0xFFFBBF24)),
    RUBY("RUBY", "ياقوتي", Color(0xFFF43F5E), Color(0xFFFB7185));

    companion object {
        fun fromKey(key: String?): GlassThemeOption {
            return entries.firstOrNull { it.key == key } ?: CYAN
        }
    }
}

val LocalGlassAccent = compositionLocalOf { GlassThemeOption.CYAN }
