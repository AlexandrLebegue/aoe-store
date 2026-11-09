package dev.proxboss.aoestore.ui.components

import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "—"
    if (bytes < 1024L) return "$bytes o"
    val units = listOf("Ko", "Mo", "Go")
    var value = bytes / 1024.0
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index += 1
    }
    val rounded = if (value >= 100) value.roundToInt().toString() else String.format(Locale.FRENCH, "%.1f", value)
    return "$rounded ${units[index]}"
}

fun formatDate(iso: String): String = format(iso, "d MMM yyyy")

fun formatShortDate(iso: String): String = format(iso, "d MMM")

fun relativeDate(iso: String): String {
    val instant = parseInstant(iso) ?: return ""
    val days = java.time.Duration.between(instant, Instant.now()).toDays()
    return when {
        days <= 0L -> "aujourd’hui"
        days == 1L -> "hier"
        days < 30L -> "il y a $days j"
        else -> formatDate(iso)
    }
}

private fun format(iso: String, pattern: String): String {
    val instant = parseInstant(iso) ?: return ""
    val formatter = DateTimeFormatter.ofPattern(pattern, Locale.FRENCH).withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}

private fun parseInstant(iso: String): Instant? {
    if (iso.isBlank()) return null
    return runCatching { Instant.parse(iso) }.getOrNull()
}

fun String.toAccentColor(fallback: Color = Color(0xFF5B8CFF)): Color {
    return runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrDefault(fallback)
}

fun versionLabel(version: String?): String = if (version.isNullOrBlank()) "—" else "v$version"
