package org.phoenicx.nova.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.delay
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaType

/**
 * TV text field: moving the highlight onto it does NOT pop up the keyboard.
 * Press OK on it to start typing; Done/Next or moving away ends editing.
 */
@Composable
fun NovaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    icon: ImageVector? = null,
    imeAction: ImeAction = ImeAction.Next,
    keyboardType: KeyboardType = KeyboardType.Text,
    focusRequester: FocusRequester? = null,
    onDone: () -> Unit = {},
) {
    val fm = LocalFocusManager.current
    val kb = LocalSoftwareKeyboardController.current
    val displayReq = focusRequester ?: remember { FocusRequester() }
    val editReq = remember { FocusRequester() }
    var editing by remember { mutableStateOf(false) }
    var wasEditing by remember { mutableStateOf(false) }
    var hadEditFocus by remember { mutableStateOf(false) }
    var moveNextAfter by remember { mutableStateOf(false) }

    val displayInteraction = remember { MutableInteractionSource() }
    val displayFocused by displayInteraction.collectIsFocusedAsState()
    val editInteraction = remember { MutableInteractionSource() }
    val editFocused by editInteraction.collectIsFocusedAsState()
    val focused = displayFocused || editFocused

    LaunchedEffect(editing) {
        if (editing) {
            wasEditing = true
            hadEditFocus = false
            delay(40)
            runCatching { editReq.requestFocus() }
            kb?.show()
        } else if (wasEditing) {
            delay(40)
            runCatching { displayReq.requestFocus() }
            if (moveNextAfter) {
                moveNextAfter = false
                delay(40)
                fm.moveFocus(FocusDirection.Down)
            }
        }
    }

    fun finish(next: Boolean) {
        moveNextAfter = next
        kb?.hide()
        editing = false
    }

    val shape = RoundedCornerShape(14.dp)
    val boxModifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .background(if (focused) Color(0x2E6CE5F5) else Color(0x1AFFFFFF), shape)
        .border(if (focused) 2.dp else 1.dp, if (focused) Nova.AccentBrush else SolidColor(Nova.Stroke), shape)
        .padding(horizontal = 16.dp)

    Column(modifier) {
        if (label.isNotEmpty()) {
            Text(label.uppercase(), style = NovaType.Caption, color = if (focused) Nova.Cyan else Nova.TextFaint)
            Spacer(Modifier.height(8.dp))
        }
        if (!editing) {
            // Display mode: a normal focusable box, no keyboard until OK is pressed
            Row(
                Modifier
                    .focusRequester(displayReq)
                    .clip(shape)
                    .clickable(interactionSource = displayInteraction, indication = null) { editing = true }
                    .then(boxModifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(icon, null, tint = if (focused) Nova.Cyan else Nova.TextDim, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                }
                val shown = when {
                    value.isEmpty() -> placeholder
                    password -> "•".repeat(value.length.coerceAtMost(24))
                    else -> value
                }
                Text(
                    shown, style = NovaType.Body, maxLines = 1,
                    color = if (value.isEmpty()) Nova.TextFaint else Nova.Text,
                    modifier = Modifier.weight(1f),
                )
                if (displayFocused) Text("OK to type", style = NovaType.Label, color = Nova.TextFaint)
            }
        } else {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = NovaType.Body.copy(color = Nova.Text),
                cursorBrush = SolidColor(Nova.Cyan),
                interactionSource = editInteraction,
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (password) KeyboardType.Password else keyboardType,
                    imeAction = imeAction,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { finish(next = true) },
                    onDone = { finish(next = false); onDone() },
                    onSearch = { finish(next = false); onDone() },
                    onGo = { finish(next = false); onDone() },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(editReq)
                    .onFocusChanged { f ->
                        if (f.isFocused) hadEditFocus = true
                        else if (hadEditFocus && editing) { kb?.hide(); editing = false }
                    }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> { finish(next = true); true }
                            Key.DirectionUp -> { kb?.hide(); editing = false; true }
                            else -> false
                        }
                    },
                decorationBox = { inner ->
                    Row(boxModifier, verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) {
                            Icon(icon, null, tint = Nova.Cyan, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                        }
                        Box(Modifier.weight(1f)) {
                            if (value.isEmpty() && placeholder.isNotEmpty()) {
                                Text(placeholder, style = NovaType.Body, color = Nova.TextFaint)
                            }
                            inner()
                        }
                    }
                },
            )
        }
    }
}
