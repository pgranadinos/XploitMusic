package com.auramusic.app.presentation.library

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun RenamePlaylistDialog(
    playlistId: Long,
    currentName: String,
    onDismiss: () -> Unit,
    onSave: suspend (String) -> Boolean,
) {
    var name by rememberSaveable(playlistId) { mutableStateOf(currentName) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Cambiar nombre") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                enabled = !saving,
                singleLine = true,
                label = { Text("Nombre de la playlist") },
                isError = error != null,
                supportingText = { error?.let { Text(it) } }
            )
        },
        confirmButton = {
            TextButton(
                enabled = !saving && name.isNotBlank() && name.trim() != currentName,
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            if (onSave(name.trim())) onDismiss()
                            else error = "No se pudo guardar. La playlist puede haber sido eliminada."
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = "No se pudo guardar el nombre. Intenta de nuevo."
                        } finally {
                            saving = false
                        }
                    }
                }
            ) { Text(if (saving) "Guardando…" else "Guardar") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
