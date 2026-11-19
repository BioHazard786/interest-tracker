package com.biohazard786.interesttracker.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keeps the bearer token AES-GCM encrypted with an Android Keystore key. */
class TokenStore(context: Context) {
  private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
  private val _token = MutableStateFlow(read())

  /** Emits null when signed out; the UI switches screens off this. */
  val token: StateFlow<String?> = _token.asStateFlow()

  fun save(token: String) {
    val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
    val blob = cipher.iv + cipher.doFinal(token.toByteArray())
    prefs.edit().putString(PREF_KEY, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    _token.value = token
  }

  fun clear() {
    prefs.edit().remove(PREF_KEY).apply()
    _token.value = null
  }

  private fun read(): String? {
    val stored = prefs.getString(PREF_KEY, null) ?: return null
    return try {
      val blob = Base64.decode(stored, Base64.NO_WRAP)
      val cipher =
        Cipher.getInstance(TRANSFORM).apply {
          init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, IV_SIZE))
        }
      String(cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE))
    } catch (_: Exception) {
      // Key invalidated or data corrupt: treat as signed out.
      prefs.edit().remove(PREF_KEY).apply()
      null
    }
  }

  private fun key(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
      .apply {
        init(
          KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        )
      }
      .generateKey()
  }

  private companion object {
    const val ALIAS = "interest_tracker_token"
    const val PREF_KEY = "token"
    const val TRANSFORM = "AES/GCM/NoPadding"
    const val IV_SIZE = 12
  }
}
