package com.fatih.litepdf.util

fun Int.normalizeRotation(): Int {
    val normalized = ((this % 360) + 360) % 360
    return when {
        normalized < 45 -> 0
        normalized < 135 -> 90
        normalized < 225 -> 180
        normalized < 315 -> 270
        else -> 0
    }
}
