package com.fatih.litepdf

import android.app.Application
import androidx.room.Room
import com.fatih.litepdf.data.database.LitePdfDatabase
import com.fatih.litepdf.data.datastore.SettingsDataStore
import com.fatih.litepdf.data.repository.RoomDocumentRepository
import com.fatih.litepdf.domain.repository.DocumentRepository
import com.fatih.litepdf.domain.repository.SettingsRepository
import com.fatih.litepdf.pdf.AndroidPdfEngine
import com.fatih.litepdf.pdf.PdfBitmapCache
import com.fatih.litepdf.pdf.PdfBoxTextSearchEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class LitePdfApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        appContainer = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    val database: LitePdfDatabase = Room.databaseBuilder(
        application,
        LitePdfDatabase::class.java,
        "litepdf.db"
    )
        .addMigrations(LitePdfDatabase.MIGRATION_1_2)
        .build()

    val bitmapCache = PdfBitmapCache()
    val pdfEngine = AndroidPdfEngine(application.contentResolver)
    val textSearchEngine = PdfBoxTextSearchEngine(application.contentResolver)
    val textDocumentEngine = office.TextDocumentEngine(application.contentResolver)
    val documentRepository: DocumentRepository = RoomDocumentRepository(
        contentResolver = application.contentResolver,
        recentDao = database.recentDocumentDao(),
        bookmarkDao = database.bookmarkDao(),
        pdfEngine = pdfEngine
    )
    val settingsRepository: SettingsRepository = SettingsDataStore(application)
}",
