package com.biohazard786.interesttracker

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.biohazard786.interesttracker.data.InterestApi
import com.biohazard786.interesttracker.data.TokenStore

class App : Application() {
  // Hand-wired: two objects don't justify a DI framework.
  lateinit var tokens: TokenStore
    private set

  lateinit var api: InterestApi
    private set

  override fun onCreate() {
    super.onCreate()
    PDFBoxResourceLoader.init(this) // glyph lists and fonts PDFBox reads from assets
    tokens = TokenStore(this)
    api = InterestApi(BuildConfig.API_BASE_URL, tokens)
  }
}
