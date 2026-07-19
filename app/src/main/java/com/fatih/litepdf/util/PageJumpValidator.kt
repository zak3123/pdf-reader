package com.fatih.litepdf.util

object PageJumpValidator {
    fun validate(input: String, pageCount: Int): Int? {
        val page = input.trim().toIntOrNull() ?: return null
        if (pageCount <= 0) return null
        return (page - 1).takeIf { it in 0 until pageCount }
    }
}
