package com.fatih.litepdf

import android.app.ActivityManager
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
    val isLowRamDevice: Boolean =
        application.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true

    val database: LitePdfDatabase = Room.databaseBuilder(
        application,
        LitePdfDatabase::class.java,
        "litepdf.db"
    ).build()

    val bitmapCache = PdfBitmapCache(
        maxBytes = if (isLowRamDevice) {
            PdfBitmapCache.defaultMaxBytes().coerceAtMost(16 * 1024 * 1024)
        } else {
            PdfBitmapCache.defaultMaxBytes()
        },
        retentionRadius = if (isLowRamDevice) 1 else 3
    )
    val pdfEngine = AndroidPdfEngine(application.contentResolver, isLowRamDevice)
    val textSearchEngine = PdfBoxTextSearchEngine(application.contentResolver)
    val documentRepository: DocumentRepository = RoomDocumentRepository(
        contentResolver = application.contentResolver,
        recentDao = database.recentDocumentDao(),
        bookmarkDao = database.bookmarkDao(),
        pdfEngine = pdfEngine
    )
    val settingsRepository: SettingsRepository = SettingsDataStore(application)
}
