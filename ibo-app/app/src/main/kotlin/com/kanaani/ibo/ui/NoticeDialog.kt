package com.kanaani.ibo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kanaani.ibo.data.Panel

/** Texte de l'avis déjà fermé (une fois par ouverture de l'application). */
private var dismissed by mutableStateOf<String?>(null)

/** Avis de maintenance du panneau de contrôle, au-dessus de tous les écrans. */
@Composable
fun NoticeDialog() {
    val n = Panel.notice ?: return
    if (dismissed == n.text) return
    ChoiceDialog("📢 " + n.text, listOf(Str.ok to { dismissed = n.text })) { dismissed = n.text }
}
