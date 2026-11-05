# Kanaani IBO : les bibliothèques fournissent leurs propres règles.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Décodeur audio FFmpeg (méthodes appelées depuis le code natif)
-keep class androidx.media3.decoder.ffmpeg.** { *; }
