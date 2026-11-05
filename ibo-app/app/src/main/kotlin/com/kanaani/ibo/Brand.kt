package com.kanaani.ibo

/** Marque, générée depuis ibo-app/brand.properties. */
object Brand {
    val host: String = BuildConfig.BRAND_HOST.trim().trimEnd('/')
    val name: String = BuildConfig.BRAND_PROVIDER_NAME
    /** Serveurs de secours (séparés par des virgules), essayés dans l'ordre si le principal ne répond pas ou refuse le compte. */
    val backupHosts: List<String> = BuildConfig.BRAND_BACKUP_HOST.split(',').map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }
    /** Principal puis secours. */
    val hosts: List<String> = (listOf(host) + backupHosts).filter { it.isNotEmpty() }.distinct()
}
