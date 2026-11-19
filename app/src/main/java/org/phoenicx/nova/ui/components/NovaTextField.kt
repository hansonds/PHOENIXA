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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaType

/**
 * Text box.
 * TV: moving the highlight onto it does NOT pop up the keyboard. Press OK on it to start typing;
 *     Done/Next or moving away ends editing. ◀ ▶ move the cursor.
 * Phone / tablet: a normal text box: tap anywhere in the text to put the cursor there.
 * The cursor starts at the end of the text, and Backspace deletes the letter before the cursor.
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
    val tv = org.phoenicx.nova.NovaApp.instance.isTv
    val displayReq = focusRequester ?: remember { FocusRequester() }
    val editReq = if (tv) remember { FocusRequester() } else displayReq
    var editing by remember { mutableStateOf(false) }
    var wasEditing by remember { mutableStateOf(false) }
    var hadEditFocus by remember { mutableStateOf(false) }
    var moveNextAfter by remember { mutableStateOf(false) }

    // Text plus cursor position. If the text is changed from outside, the cursor goes to the end.
    var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val current = if (tfv.text == value) tfv else TextFieldValue(value, TextRange(value.length))
    fun update(n: TextFieldValue) {
        tfv = n
        if (n.text != value) onValueChange(n.text)
    }

    val displayInteraction = remember { MutableInteractionSource() }
    val displayFocused by displayInteraction.collectIsFocusedAsState()
    val editInteraction = remember { MutableInteractionSource() }
    val editFocused by editInteraction.collectIsFocusedAsState()
    val focused = displayFocused || editFocused

    LaunchedEffect(editing) {
        if (!tv) return@LaunchedEffect
        if (editing) {
            wasEditing = true
            // retry a few times in case the text box isn't ready yet
            for (attempt in 0 until 5) {
                delay(40)
                runCatching { editReq.requestFocus() }
                if (hadEditFocus) break
            }
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

    fun startEditing() {
        hadEditFocus = false   // must be cleared before the text box appears, or it closes itself instantly
        moveNextAfter = false
        tfv = TextFieldValue(value, TextRange(value.length))  // cursor at the end
        editing = true
    }

    fun finish(next: Boolean) {
        kb?.hide()
        if (tv) {
            moveNextAfter = next
            editing = false
        } else if (next) fm.moveFocus(FocusDirection.Down) else fm.clearFocus()
    }

    /** Backspace: delete the selected text, or the letter just before the cursor */
    fun backspace() {
        val t = current
        val sel = t.selection
        when {
            !sel.collapsed -> update(TextFieldValue(t.text.removeRange(sel.min, sel.max), TextRange(sel.min)))
            sel.start > 0 -> update(TextFieldValue(t.text.removeRange(sel.start - 1, sel.start), TextRange(sel.start - 1)))
        }
    }

    val shape = RoundedCornerShape(14.dp)
    val boxModifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .background(if (focused) Nova.Cyan.copy(alpha = 0.18f) else Color(0x1AFFFFFF), shape)
        .border(if (focused) 2.dp else 1.dp, if (focused) Nova.AccentBrush else SolidColor(Nova.Stroke), shape)
        .padding(horizontal = 16.dp)

    Column(modifier) {
        if (label.isNotEmpty()) {
            Text(label.uppercase(), style = NovaType.Caption, color = if (focused) Nova.Cyan else Nova.TextFaint)
            Spacer(Modifier.height(8.dp))
        }
        if (tv && !editing) {
            // TV display mode: a normal focusable box, no keyboard until OK is pressed
            Row(
                Modifier
                    .focusRequester(displayReq)
                    .clip(shape)
                    .clickable(interactionSource = displayInteraction, indication = null) { startEditing() }
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
                value = current,
                onValueChange = { update(it) },
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
                        if (!tv) return@onFocusChanged
                        if (f.isFocused) hadEditFocus = true
                        else if (hadEditFocus && editing) { kb?.hide(); editing = false }
                    }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            // Some keyboards don't delete on their own; delete at the cursor, not at the end
                            Key.Backspace -> { backspace(); true }
                            Key.DirectionDown -> if (tv) { finish(next = true); true } else false
                            Key.DirectionUp -> if (tv) { kb?.hide(); editing = false; true } else false
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
                                Text(placeholder, style = NovaType.Body, color = Nova.TextFaint, maxLines = 1)
                            }
                            inner()
                        }
                    }
                },
            )
        }
    }
}
