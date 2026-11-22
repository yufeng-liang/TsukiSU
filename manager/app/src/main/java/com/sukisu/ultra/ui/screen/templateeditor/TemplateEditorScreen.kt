package com.sukisu.ultra.ui.screen.templateeditor

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import com.sukisu.ultra.R
import com.sukisu.ultra.toOrdinalList
import com.sukisu.ultra.toRootProfileFlags
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.util.deleteAppProfileTemplate
import com.sukisu.ultra.ui.viewmodel.TemplateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TemplateEditorScreen(template: TemplateViewModel.TemplateInfo, readOnly: Boolean) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiMode = LocalUiMode.current
    val isCreation = template.id.isBlank()
    val autoSave = uiMode == UiMode.Miuix && !isCreation

    var currentTemplate by rememberSaveable { mutableStateOf(template) }
    var idErrorHint by remember { mutableStateOf("") }
    var idCheckJob by remember { mutableStateOf<Job?>(null) }
    var autoSaveJob by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }

    val saveTemplateFailed = stringResource(id = R.string.app_profile_template_save_failed)
    val idConflictError = stringResource(id = R.string.app_profile_template_id_exist)
    val idInvalidError = stringResource(id = R.string.app_profile_template_id_invalid)

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun finishEditing() {
        if (readOnly) {
            navigator.pop()
            return
        }
        if (autoSave && autoSaveJob?.isActive == true) {
            autoSaveJob?.cancel()
            val pending = currentTemplate
            scope.launch {
                withContext(Dispatchers.IO) { saveTemplate(pending) }
                navigator.setResult("template_edit", true)
            }
        } else {
            navigator.setResult("template_edit", true)
        }
    }

    fun updateTemplate(updatedTemplate: TemplateViewModel.TemplateInfo) {
        currentTemplate = updatedTemplate
        if (autoSave) {
            autoSaveJob?.cancel()
            autoSaveJob = scope.launch {
                delay(300)
                withContext(Dispatchers.IO) { saveTemplate(updatedTemplate) }
            }
        }
    }

    val uiState = TemplateEditorUiState(
        template = currentTemplate,
        initialTemplate = template,
        readOnly = readOnly,
        isCreation = isCreation,
        idErrorHint = idErrorHint,
    )

    fun saveCurrentTemplate() {
        if (busy) return
        busy = true
        autoSaveJob?.cancel()
        idCheckJob?.cancel()
        val snapshot = currentTemplate
        scope.launch {
            try {
                if (uiMode == UiMode.Miuix) {
                    val checkResult = withContext(Dispatchers.IO) { idCheck(snapshot.id) }
                    when (checkResult) {
                        1 -> {
                            showToast(idConflictError)
                            return@launch
                        }

                        2 -> {
                            showToast(idInvalidError)
                            return@launch
                        }
                    }
                }

                val saved = withContext(Dispatchers.IO) { saveTemplate(snapshot, isCreation) }
                if (saved) {
                    navigator.setResult("template_edit", true)
                } else {
                    showToast(saveTemplateFailed)
                }
            } finally {
                busy = false
            }
        }
    }

    val actions = TemplateEditorActions(
        onBack = dropUnlessResumed { finishEditing() },
        onDelete = {
            if (busy) return@TemplateEditorActions
            busy = true
            autoSaveJob?.cancel()
            idCheckJob?.cancel()
            val targetId = currentTemplate.id
            scope.launch {
                try {
                    val deleted = withContext(Dispatchers.IO) { deleteAppProfileTemplate(targetId) }
                    if (deleted) {
                        navigator.setResult("template_edit", true)
                    }
                } finally {
                    busy = false
                }
            }
        },
        onSave = ::saveCurrentTemplate,
        onNameChange = { value ->
            updateTemplate(currentTemplate.copy(name = value))
        },
        onIdChange = { value ->
            currentTemplate = currentTemplate.copy(id = value)
            idCheckJob?.cancel()
            if (!isValidTemplateId(value)) {
                idErrorHint = idInvalidError
            } else {
                idCheckJob = scope.launch {
                    delay(250)
                    val exists = withContext(Dispatchers.IO) { isTemplateExist(value) }
                    if (currentTemplate.id == value) {
                        idErrorHint = if (exists) idConflictError else ""
                    }
                }
            }
        },
        onAuthorChange = { value ->
            updateTemplate(currentTemplate.copy(author = value))
        },
        onDescriptionChange = { value ->
            updateTemplate(currentTemplate.copy(description = value))
        },
        onProfileChange = { profile ->
            updateTemplate(
                currentTemplate.copy(
                    uid = profile.uid,
                    gid = profile.gid,
                    groups = profile.groups,
                    capabilities = profile.capabilities,
                    context = profile.context,
                    namespace = profile.namespace,
                    rules = profile.rules.split("\n"),
                    flags = profile.flags.toRootProfileFlags().toOrdinalList(),
                )
            )
        },
    )

    when (uiMode) {
        UiMode.Miuix -> TemplateEditorScreenMiuix(
            state = uiState,
            actions = actions,
        )

        UiMode.Material -> TemplateEditorScreenMaterial(
            state = uiState,
            actions = actions,
        )
    }
}