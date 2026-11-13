package dev.atomix.launcher.ui.home

import kotlin.math.pow

private fun channelLum(c: Int): Double {
    val s = c / 255.0
    return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
}

/** WCAG relative luminance of an opaque ARGB colour. */
fun luminance(argb: Int): Double =
    0.2126 * channelLum((argb shr 16) and 0xFF) + 0.7152 * channelLum((argb shr 8) and 0xFF) + 0.0722 * channelLum(argb and 0xFF)

fun contrast(a: Int, b: Int): Double {
    val la = luminance(a); val lb = luminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** Darkens [argb] until #F1F1F1 text on it reaches 7:1 (AAA), keeping its hue. */
fun darkenForText(argb: Int, text: Int = 0xFFF1F1F1.toInt()): Int {
    var r = (argb shr 16) and 0xFF; var g = (argb shr 8) and 0xFF; var b = argb and 0xFF
    var c = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    while (contrast(text, c) < 7.0) {
        r = r * 9 / 10; g = g * 9 / 10; b = b * 9 / 10
        c = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    return c
}

/** Average colour of a logo's mostly-opaque pixels, or null for an all-transparent image. */
fun averageOpaque(pixels: IntArray): Int? {
    var r = 0L; var g = 0L; var b = 0L; var n = 0L
    for (p in pixels) {
        if ((p ushr 24) < 128) continue
        r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
    }
    if (n == 0L) return null
    return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
}

private val PLATE_HUES = intArrayOf(0xFF2B4C7E.toInt(), 0xFF5B3A7A.toInt(), 0xFF1F5E5B.toInt(), 0xFF7A3B3B.toInt(), 0xFF3D5A2A.toInt(), 0xFF6B4E1F.toInt())

/** A stable plate colour for a channel without a usable logo. */
fun hashedPlate(name: String): Int = darkenForText(PLATE_HUES[Math.floorMod(name.hashCode(), PLATE_HUES.size)])
