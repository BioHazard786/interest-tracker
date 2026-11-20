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

## Uploading statements

The **Upload** button opens a full-screen flow: pick a bank, add one or more statement files, review the interest
rows found, then sync. Parsing happens on the phone (`parsing/`, ported from `src/lib/banking/parsers`); only the
parsed rows are sent. Rows already on the server (same hash) are skipped, so re-uploading is safe.

- CSV (PNB) and XLSX (SBI, IDFC) are parsed by hand-written readers, no extra libraries.
- PDF (Kotak, IDFC) text comes from PDFBox. Password-protected PDFs ask for the password in the upload screen (the
  tests cover it with `fixtures/statements/real-kotak-locked.pdf`); password-protected spreadsheets are refused with a message.
- `./gradlew :app:testDebugUnitTest` checks every parser against the real web parsers' output, see
  [fixtures/README.md](fixtures/README.md).

## Releasing

Pushing a tag that starts with `v` builds a signed release APK and publishes it as a GitHub release
(`.github/workflows/android-release.yml`):

```bash
git tag v1.2.3          # v1.2.3-beta.1 makes a pre-release
git push origin v1.2.3
```

- The tag sets the app version: `v1.2.3` -> versionName `1.2.3`, versionCode `10203` (`major*10000 + minor*100 + patch`, so
  keep minor and patch under 100). Locally the version stays `1.0` / `1` unless you pass `-PversionName` / `-PversionCode`.
- The release is signed with the same key as local builds. The workflow fails early if a secret is missing.
- Repo settings the workflow reads (Settings -> Secrets and variables -> Actions):

  | Name | Kind | From |
  | --- | --- | --- |
  | `ANDROID_KEYSTORE_BASE64` | secret | `base64 -w0 keystore/release-key.jks` |
  | `ANDROID_KEYSTORE_PASSWORD` | secret | `storePassword` in `keystore.properties` |
  | `ANDROID_KEY_ALIAS` | secret | `keyAlias` in `keystore.properties` |
  | `ANDROID_KEY_PASSWORD` | secret | `keyPassword` in `keystore.properties` |
  | `API_BASE_URL` | variable | `local.properties` |
  | `GOOGLE_WEB_CLIENT_ID` | variable | `local.properties` |

  Change one with `gh secret set NAME` / `gh variable set NAME`. Losing the keystore means installed copies can never be
  updated, so keep a backup of `keystore/release-key.jks` somewhere safe.
- The unit tests are not part of the release job: the fixtures they read are gitignored (`*.pdf`, `*.csv`, `*.xlsx`).
