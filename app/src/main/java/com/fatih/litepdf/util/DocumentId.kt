package com.fatih.litepdf.util

import java.security.MessageDigest

fun stableDocumentId(uriString: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(uriString.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}
