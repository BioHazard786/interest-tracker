# Credential Manager loads the Play Services provider reflectively.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

# kotlinx.serialization and OkHttp ship their own R8 rules; nothing else needed.

# PDFBox mentions optional JPEG2000 / crypto classes; text extraction never loads them.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
