package com.uri.lee.dl

import androidx.annotation.ColorInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

fun CoroutineScope.EditTextComponent(
    state: Flow<EditTextState>,
    editTextLayout: EditTextLayout,
    onTextChange: (String) -> Unit,
    onFocusChange: () -> Unit = {}
) {
    state.distinctUntilChanged()
        .map { it.text }
        .distinctUntilChanged()
        .onEach { editTextLayout.text = it }
        .launchIn(this)
    state.distinctUntilChanged()
        .map { it.highlight }
        .distinctUntilChanged()
        .onEach {
            editTextLayout.isErrorEnabled = it != null
            editTextLayout.error = it?.text.orEmpty()
            editTextLayout.errorColor = it?.color
        }
        .launchIn(this)
    state.distinctUntilChanged()
        .map { it.isEnabled }
        .distinctUntilChanged()
        .onEach { editTextLayout.isEnabled = it }
        .launchIn(this)
    editTextLayout.onTextChange = onTextChange
    editTextLayout.onFocusChange = onFocusChange
}

data class EditTextState(
    val text: String = "",
    val highlight: Highlight? = null,
    val isEnabled: Boolean = true
) {
    data class Highlight(val text: String, @ColorInt val color: Int? = null)

    constructor(
        text: String,
        errorText: String?,
        isEnabled: Boolean = true
    ) : this(
        text = text,
        highlight = errorText?.let { Highlight(text = errorText) },
        isEnabled = isEnabled
    )
}
