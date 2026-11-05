package com.kanaani.ibo.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.kanaani.ibo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request
import java.io.File

/**
 * Mises à jour depuis la release GitHub : `update.json` (versionCode, versionName, url, notes) publié par la CI à côté
 * de l'APK. L'application télécharge l'APK elle-même puis ouvre l'installeur Android (un appui « Installer » reste
 * obligatoire pour une application hors Play Store).
 */
object Updater {
    data class Info(val code: Int, val name: String, val url: String, val notes: String?, val sha256: String?, val size: Long?)

    /** Raison d'un échec, affichée au lieu du « Application non installée » d'Android. */
    enum class Problem { Download, Corrupt, OldVersion, Signature, Install }
    var problem by mutableStateOf<Problem?>(null)
        private set

    enum class Phase { Idle, Available, Downloading, Ready, Failed, UpToDate }

    var info by mutableStateOf<Info?>(null)
        private set
    var phase by mutableStateOf(Phase.Idle)
        private set
    var progress by mutableStateOf(0f)
        private set
    /** L'utilisateur a choisi « Plus tard » : plus de fenêtre automatique pendant cette session. */
    var dismissed by mutableStateOf(false)

    private var apk: File? = null

    /** Vérifie la release ; [manual] : afficher aussi « à jour » / l'erreur. */
    suspend fun check(manual: Boolean = false) {
        val url = BuildConfig.UPDATE_URL
        if (url.isBlank()) { if (manual) phase = Phase.UpToDate; return }
        val found = withContext(Dispatchers.IO) {
            runCatching {
                Http.client.newCall(Request.Builder().url("$url?t=${System.currentTimeMillis()}").build()).execute().use { r ->
                    if (!r.isSuccessful) return@use null
                    val o = Json.parseToJsonElement(r.body.string()) as? JsonObject ?: return@use null
                    fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
                    Info(s("versionCode")?.toIntOrNull() ?: 0, s("versionName").orEmpty(), s("url").orEmpty(), s("notes"),
                        s("sha256")?.lowercase(), s("size")?.toLongOrNull())
                }
            }.getOrNull()
        }
        when {
            found != null && found.code > BuildConfig.VERSION_CODE && found.url.isNotBlank() -> {
                info = found
                if (manual) dismissed = false
                if (phase != Phase.Downloading && phase != Phase.Ready) phase = Phase.Available
            }
            manual -> phase = if (found == null) Phase.Failed else Phase.UpToDate
        }
    }

    fun later() { dismissed = true; if (phase == Phase.UpToDate || phase == Phase.Failed) phase = Phase.Idle }

    /** Télécharge l'APK (progression 0..1) puis lance l'installation. */
    suspend fun downloadAndInstall(ctx: Context) {
        val i = info ?: return
        problem = null
        // Deux essais : un fichier incomplet ou abîmé (coupure réseau) est retéléchargé avant d'abandonner.
        repeat(2) {
            val f = download(ctx, i)
            val p = if (f == null) Problem.Download else verify(ctx, f, i)
            if (p == null) {
                apk = f
                phase = Phase.Ready
                install(ctx)
                return
            }
            problem = p
            if (p == Problem.OldVersion || p == Problem.Signature) { phase = Phase.Failed; return }
        }
        phase = Phase.Failed
    }

    private suspend fun download(ctx: Context, i: Info): File? {
        phase = Phase.Downloading
        progress = 0f
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val out = File(dir, "Kanaani-${i.name}.apk")
                Http.client.newCall(Request.Builder().url(i.url).build()).execute().use { r ->
                    if (!r.isSuccessful) return@use null
                    val body = r.body
                    val total = body.contentLength().takeIf { it > 0 } ?: -1L
                    body.byteStream().use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var read = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                read += n
                                if (total > 0) progress = (read.toFloat() / total).coerceIn(0f, 1f)
                            }
                        }
                    }
                    out
                }
            }.getOrNull()
        }
        return file?.takeIf { it.length() > 0L }
    }

    /**
     * Contrôles AVANT l'installeur (sinon Android affiche seulement « Application non installée ») :
     * taille et empreinte SHA-256 annoncées, APK lisible, même application, version plus récente, même signature.
     */
    private suspend fun verify(ctx: Context, f: File, i: Info): Problem? = withContext(Dispatchers.IO) {
        if (i.size != null && i.size > 0 && f.length() != i.size) return@withContext Problem.Corrupt
        if (!i.sha256.isNullOrBlank()) {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            f.inputStream().use { s -> val b = ByteArray(64 * 1024); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
            if (md.digest().joinToString("") { "%02x".format(it) } != i.sha256) return@withContext Problem.Corrupt
        }
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            else android.content.pm.PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(f.absolutePath, flags) ?: return@withContext Problem.Corrupt
        if (archive.packageName != ctx.packageName) return@withContext Problem.Corrupt
        @Suppress("DEPRECATION")
        val newCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode else archive.versionCode.toLong()
        if (newCode <= BuildConfig.VERSION_CODE) return@withContext Problem.OldVersion
        val installed = runCatching { pm.getPackageInfo(ctx.packageName, flags) }.getOrNull()
        // Certaines versions d'Android ne lisent pas la signature d'un APK non installé : on ne bloque que sur une vraie différence.
        val newSigs = signatures(archive)
        if (installed != null && newSigs.isNotEmpty() && signatures(installed) != newSigs) return@withContext Problem.Signature
        null
    }

    @Suppress("DEPRECATION")
    private fun signatures(p: android.content.pm.PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = p.signingInfo ?: return emptySet()
            if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        } else p.signatures
        return sigs.orEmpty().map { java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
    }

    /** Ouvre l'installeur ; la première fois, Android demande d'autoriser Kanaani à installer des applications. */
    fun install(ctx: Context) {
        val f = apk ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            // Écran « Installer des applications inconnues » ; certaines TV ne l'ont pas : on ouvre alors directement
            // l'installeur, qui demande lui-même l'autorisation.
            val opened = runCatching {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (opened) return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", f)
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { problem = Problem.Install; phase = Phase.Failed }
    }
}
