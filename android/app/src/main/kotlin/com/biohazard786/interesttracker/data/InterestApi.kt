package com.biohazard786.interesttracker.data

import com.biohazard786.interesttracker.parsing.ParsedTxn
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class ApiException(val code: Int, message: String) : Exception(message)

class InterestApi(baseUrl: String, private val tokens: TokenStore) {
  private val base = baseUrl.trimEnd('/').toHttpUrl()
  private val json = Json { ignoreUnknownKeys = true }
  private val jsonType = "application/json".toMediaType()

  private val client =
    OkHttpClient.Builder()
      .addInterceptor { chain ->
        val token = tokens.token.value
        val request =
          if (token == null) chain.request()
          else chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        chain.proceed(request)
      }
      .build()

  /** Exchanges a Google idToken for a better-auth bearer token and stores it. */
  suspend fun signInWithGoogle(idToken: GoogleIdToken) {
    // better-auth checks the token's nonce claim against this one.
    val body = json.encodeToString(SocialSignInBody("google", IdTokenBody(idToken.token, idToken.nonce)))
    val request =
      Request.Builder()
        .url(base.newBuilder().addPathSegments("api/auth/sign-in/social").build())
        .post(body.toRequestBody(jsonType))
        .build()
    val token =
      call(request) { response ->
        // The bearer plugin exposes the token in this header; the body is a fallback.
        response.header("set-auth-token")
          ?: json.decodeFromString<SignInResponse>(response.body.string()).token
      }
    tokens.save(token ?: throw ApiException(500, "Server did not return a session token"))
  }

  /** Best-effort server sign-out (invalidates the session), then always forget the token locally. */
  suspend fun signOut() {
    val request =
      Request.Builder()
        .url(base.newBuilder().addPathSegments("api/auth/sign-out").build())
        .post("{}".toRequestBody(jsonType))
        .build()
    runCatching { call(request) {} }
    tokens.clear()
  }

  suspend fun stats(): Stats = get("api/v1/stats") { json.decodeFromString(it) }

  suspend fun lastDates(): Map<String, String?> = get("api/v1/last-dates") { json.decodeFromString(it) }

  suspend fun projection(
    cursor: String?,
    descending: Boolean,
    statuses: Set<DonationStatus>,
  ): ProjectionPage =
    get(
      "api/v1/projection",
      buildList {
        add("sortDirection" to if (descending) "desc" else "asc")
        cursor?.let { add("cursor" to it) }
        statuses.forEach { add("statusFilter" to it.apiValue) }
      },
    ) {
      json.decodeFromString(it)
    }

  /** The signed-in user's id. Statement hashes include it, so the phone needs it to match the web's. */
  suspend fun userId(): String =
    get("api/auth/get-session") { json.decodeFromString<SessionResponse?>(it) }?.user?.id
      ?: throw ApiException(401, "You are signed out. Sign in again.")

  /** Uploads parsed transactions; the server skips any whose hash it already has. Returns the server's message. */
  suspend fun syncTransactions(bankId: String, transactions: List<ParsedTxn>): String {
    val body =
      SyncBody(
        bankId,
        transactions.map {
          SyncTxn(it.transactionHash, it.amount, it.balance, it.date.toString(), it.type, it.description, it.transactionId)
        },
      )
    val request =
      Request.Builder()
        .url(base.newBuilder().addPathSegments("api/v1/transactions/sync").build())
        .post(json.encodeToString(body).toRequestBody(jsonType))
        .build()
    return call(request) { json.decodeFromString<ApiMessage>(it.body.string()).message }
  }

  suspend fun donate(amount: Double) {
    val request =
      Request.Builder()
        .url(base.newBuilder().addPathSegments("api/v1/donations").build())
        .post(json.encodeToString(DonationBody(amount)).toRequestBody(jsonType))
        .build()
    call(request) {}
  }

  private suspend fun <T> get(
    path: String,
    query: List<Pair<String, String>> = emptyList(),
    parse: (String) -> T,
  ): T {
    val url =
      base.newBuilder().addPathSegments(path).apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
    return call(Request.Builder().url(url).build()) { parse(it.body.string()) }
  }

  private suspend fun <T> call(request: Request, handle: (Response) -> T): T =
    withContext(Dispatchers.IO) {
      try {
        client.newCall(request).execute().use { response ->
          if (response.isSuccessful) return@use handle(response)
          // An expired/invalid token (but not a failed sign-in) sends the user back to sign-in.
          if (response.code == 401 && tokens.token.value != null) tokens.clear()
          val message =
            runCatching { json.decodeFromString<ApiMessage>(response.body.string()).message }
              .getOrNull()
              ?.takeIf { it.isNotBlank() }
          throw ApiException(response.code, message ?: "Request failed (${response.code})")
        }
      } catch (e: IOException) {
        throw ApiException(0, "Can't reach the server. Check your connection.")
      }
    }
}
