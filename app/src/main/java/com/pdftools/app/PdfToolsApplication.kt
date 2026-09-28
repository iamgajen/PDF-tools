package com.pdftools.app

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PdfToolsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
