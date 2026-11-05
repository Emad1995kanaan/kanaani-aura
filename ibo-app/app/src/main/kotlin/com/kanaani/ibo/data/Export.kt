package com.kanaani.ibo.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Outil interne (caché : 5 appuis sur « À propos ») : exporte les noms des catégories (direct, films, séries) et des
 * chaînes du compte connecté dans un fichier texte, puis ouvre le partage Android. Sert à préparer les traductions.
 */
object Export {
    suspend fun lists(ctx: Context, x: Xtream): Boolean {
        val live = x.liveCategories()
        val movies = x.movieCategories()
        val series = x.showCategories()
        val channels = x.channels(null)
        if (live.isEmpty() && movies.isEmpty() && series.isEmpty()) return false
        val liveNames = live.associate { it.id to it.name }
        val text = buildString {
            fun section(title: String, list: List<Category>) {
                append("### ").append(title).append(" (").append(list.size).append(")\n")
                list.forEach { append(it.id).append('\t').append(it.name).append('\n') }
                append('\n')
            }
            section("LIVE CATEGORIES", live)
            section("MOVIE CATEGORIES", movies)
            section("SERIES CATEGORIES", series)
            append("### LIVE CHANNELS (").append(channels.size).append(")\n")
            channels.forEach { c ->
                append(c.categoryId ?: "").append('\t').append(liveNames[c.categoryId] ?: "").append('\t').append(c.name).append('\n')
            }
        }
        val file = withContext(Dispatchers.IO) {
            File(ctx.cacheDir, "updates").apply { mkdirs() }.resolve("kanaani-lists.txt").apply { writeText(text) }
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", file)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "kanaani-lists.txt").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return runCatching {
            ctx.startActivity(Intent.createChooser(send, "kanaani-lists.txt").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}
