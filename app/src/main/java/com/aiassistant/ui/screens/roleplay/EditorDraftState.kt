package com.aiassistant.ui.screens.roleplay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.content.ContextWrapper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

/** Navigation-entry scoped: survives Activity recreation without putting long text into a Bundle. */
class EditorDraftState : ViewModel() {
    private val fields = mutableMapOf<String, MutableState<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> field(name: String, initial: T): MutableState<T> =
        fields.getOrPut(name) { mutableStateOf(initial) } as MutableState<T>

    fun clearDraft() = fields.clear()
}

@Composable
internal fun <T> editorField(entity: String, field: String, initial: T): MutableState<T> =
    viewModel<EditorDraftState>(key = entity).field(field, initial)

/** Dialog editors share the chat entry: discard closed drafts, retain only Activity recreation. */
@Composable
internal fun retainEditorDraft(entity: String) {
    val draft = viewModel<EditorDraftState>(key = entity)
    val context = LocalContext.current
    DisposableEffect(draft, context) {
        onDispose {
            var owner = context
            while (owner is ContextWrapper && owner !is Activity) owner = owner.baseContext
            if ((owner as? Activity)?.isChangingConfigurations != true) draft.clearDraft()
        }
    }
}
