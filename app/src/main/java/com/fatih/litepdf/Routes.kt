package com.fatih.litepdf.navigation

object Routes {
    const val Home = "home"
    const val Settings = "settings"
    const val About = "about"
    const val ReaderBase = "reader"
    const val Reader = "$ReaderBase/{documentId}"
    const val TextReaderBase = "textreader"
    const val TextReader = "$TextReaderBase/{documentId}"

    fun reader(documentId: String): String = "$ReaderBase/$documentId"
    fun textReader(documentId: String): String = "$TextReaderBase/$documentId"
}
