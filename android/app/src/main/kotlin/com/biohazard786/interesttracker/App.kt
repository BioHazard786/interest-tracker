package com.biohazard786.interesttracker

import android.app.Application
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
    tokens = TokenStore(this)
    api = InterestApi(BuildConfig.API_BASE_URL, tokens)
  }
}
