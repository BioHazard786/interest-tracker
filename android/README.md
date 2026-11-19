# Interest Tracker (Android)

Jetpack Compose + Material 3 Expressive client for the web app in the repo root. It talks to the web app's `/api/v1` routes with a better-auth bearer token.

## Configure

1. Deploy the web app with the `/api/v1` routes and the `bearer()` auth plugin.
2. Google Cloud Console, same project as the web OAuth client: create an OAuth client of type **Android** with package `com.biohazard786.interesttracker` and the SHA-1 of your signing key.
   - Both debug and release builds are signed with the key in `android/keystore.properties` (gitignored, along with `android/keystore/`), so one SHA-1 covers both. Read it with `keytool -list -v -keystore <storeFile> -alias <keyAlias> | grep SHA1`.
3. Fill `android/local.properties` (gitignored):
   ```
   API_BASE_URL=https://your-app.vercel.app
   GOOGLE_WEB_CLIENT_ID=<the web client ID, same as GOOGLE_CLIENT_ID on Vercel>
   ```
   The app asks Google for a token addressed to the *web* client ID, so the server needs no Android-specific config.

## Build and run

```
cd android
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
android run --apks=app/build/outputs/apk/debug/app-debug.apk
```

## Not built yet

Statement upload and the native bank parsers (PNB, SBI, IDFC, Kotak). Upload from the web app for now.
