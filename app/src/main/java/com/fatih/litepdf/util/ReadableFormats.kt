package com.fatih.litepdf.util

import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun Long.asReadableDate(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
        .format(Date(this))

fun Long.asReadableFileSize(): String {
    if (this < 1024) return "$this B"
    val units = arrayOf("KB", "MB", "GB")
    var value = this / 1024.0
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(Locale.getDefault(), value, units[unitIndex])
}
