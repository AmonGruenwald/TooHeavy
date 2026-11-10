package com.tooheavy.app.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation

/** A text field's value and what to call when it is edited, for a field that selects all when focused. */
class SelectAllValue(val value: TextFieldValue, val onValueChange: (TextFieldValue) -> Unit)

/**
 * Keeps the selection of a text field showing [value], and selects all of it when the field gains focus (when
 * [selectAll]), so text that is already there, like a default name or amount, is overwritten by typing. Pass
 * [interactionSource] to the field.
 */
@Composable
fun rememberSelectAllValue(
    value: String,
    onValueChange: (String) -> Unit,
    interactionSource: MutableInteractionSource,
    selectAll: Boolean = true,
): SelectAllValue {
    // Like Material's own String text fields: the selection lives here, the text comes from the caller.
    var state by remember { mutableStateOf(TextFieldValue(value)) }
    val shown = state.copy(text = value)
    SideEffect {
        if (shown.selection != state.selection || shown.composition != state.composition) state = shown
    }
    var lastText by remember(value) { mutableStateOf(value) }
    val focused by interactionSource.collectIsFocusedAsState()
    // Runs after the tap that focused the field has placed its cursor, so the selection wins.
    LaunchedEffect(focused) {
        if (focused && selectAll && value.isNotEmpty()) state = TextFieldValue(value, TextRange(0, value.length))
    }
    return SelectAllValue(shown) { v ->
        state = v
        val changed = lastText != v.text
        lastText = v.text
        if (changed) onValueChange(v.text)
    }
}

/**
 * An OutlinedTextField that selects all its text when tapped into, so a default or earlier value can be typed
 * over straight away. Fields of more than one line (notes) keep the cursor where tapped, for adding to them.
 */
@Composable
fun SelectAllOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    selectAllOnFocus: Boolean = singleLine && !readOnly,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val field = rememberSelectAllValue(value, onValueChange, interactionSource, selectAllOnFocus && enabled)
    OutlinedTextField(
        value = field.value,
        onValueChange = field.onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape,
        colors = colors,
    )
}
