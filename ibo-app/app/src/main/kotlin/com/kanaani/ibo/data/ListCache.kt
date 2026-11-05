package com.kanaani.ibo.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Copie sur l'appareil des listes complètes « Tous » (films, séries) : à l'ouverture suivante elles s'affichent tout
 * de suite depuis ce fichier, puis sont rafraîchies en arrière-plan. Une ligne par élément, champs séparés par des
 * tabulations (bien plus rapide à relire que le JSON du serveur). Un seul compte gardé : les fichiers d'un autre
 * compte sont effacés.
 */
object ListCache {
    private const val VERSION = "v1"
    private var dir: File? = null

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "lists").apply { mkdirs() }
    }

    private fun account(user: String, pass: String): String =
        MessageDigest.getInstance("SHA-1").digest("$user\u0000$pass".toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    private fun file(user: String, pass: String, kind: String): File? = dir?.let { File(it, "${account(user, pass)}_$kind.tsv") }

    private fun clean(s: String?) = s?.replace('\t', ' ')?.replace('\n', ' ')?.replace('\r', ' ') ?: ""

    private fun write(user: String, pass: String, kind: String, rows: Sequence<List<String?>>) {
        val f = file(user, pass, kind) ?: return
        runCatching {
            // Fichiers d'un ancien compte : effacés.
            val prefix = account(user, pass) + "_"
            dir?.listFiles()?.filter { !it.name.startsWith(prefix) }?.forEach { it.delete() }
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.bufferedWriter(bufferSize = 64 * 1024).use { w ->
                w.write(VERSION); w.write("\n")
                for (r in rows) { w.write(r.joinToString("\t") { clean(it) }); w.write("\n") }
            }
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }

    private fun <T> read(user: String, pass: String, kind: String, fields: Int, map: (List<String>) -> T?): List<T>? {
        val f = file(user, pass, kind)?.takeIf { it.isFile } ?: return null
        return runCatching {
            f.bufferedReader(bufferSize = 64 * 1024).useLines { lines ->
                val it = lines.iterator()
                if (!it.hasNext() || it.next() != VERSION) return@useLines null
                val out = ArrayList<T>()
                for (line in it) {
                    val p = line.split('\t')
                    if (p.size >= fields) map(p)?.let(out::add)
                }
                out
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun opt(s: String) = s.ifEmpty { null }

    fun saveMovies(user: String, pass: String, list: List<Movie>) = write(user, pass, "vod",
        list.asSequence().map { listOf(it.id, it.name, it.poster, it.rating, it.ext, it.categoryId, it.added.toString()) })

    fun loadMovies(user: String, pass: String): List<Movie>? = read(user, pass, "vod", 7) { p ->
        Movie(p[0], p[1], opt(p[2]), opt(p[3]), p[4].ifEmpty { "mp4" }, opt(p[5]), p[6].toLongOrNull() ?: 0)
    }

    fun saveShows(user: String, pass: String, list: List<Show>) = write(user, pass, "series",
        list.asSequence().map { listOf(it.id, it.name, it.poster, it.rating, it.categoryId, it.updated.toString()) })

    fun loadShows(user: String, pass: String): List<Show>? = read(user, pass, "series", 6) { p ->
        Show(p[0], p[1], opt(p[2]), opt(p[3]), opt(p[4]), p[5].toLongOrNull() ?: 0)
    }
}
