package org.phoenicx.nova.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaType

data class SheetOption(val label: String, val detail: String, val selected: Boolean)

/**
 * Right-side picker panel. Keeps the remote's highlight inside the panel until you pick
 * something or press Back.
 */
@Composable
fun OptionSheet(
    title: String,
    options: List<SheetOption>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    val startIndex = options.indexOfFirst { it.selected }.coerceAtLeast(0)
    LaunchedEffect(Unit) { delay(80); runCatching { requester.requestFocus() } }
    BackHandler { onDismiss() }

    Box(Modifier.fillMaxSize().background(Color(0x99090B12))) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(440.dp)
                .background(Brush.horizontalGradient(listOf(Color(0xF2141826), Color(0xFC121522))))
                .border(1.dp, Nova.Stroke)
                .padding(28.dp)
                .onPreviewKeyEvent { e ->
                    // Never let the highlight escape the panel onto the page behind
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft, Key.DirectionRight -> true
                        Key.DirectionUp -> focusedIndex == 0
                        Key.DirectionDown -> focusedIndex == options.lastIndex
                        else -> false
                    }
                }
        ) {
            Text(title, style = NovaType.Title, color = Nova.Text)
            Spacer(Modifier.height(18.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options.size) { i ->
                    val o = options[i]
                    FocusSurface(
                        onClick = { onSelect(i) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        focusedScale = 1.02f,
                        glow = false,
                        focusRequester = if (i == startIndex) requester else null,
                        onFocused = { focusedIndex = i },
                    ) { focused ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (focused) Color(0x336CE5F5) else Color(0x14FFFFFF))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(o.label, style = NovaType.Button, color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (o.detail.isNotEmpty()) {
                                    Text(o.detail, style = NovaType.Label.copy(fontSize = 12.sp), color = Nova.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (o.selected) Icon(Icons.Outlined.Check, null, tint = Nova.Cyan)
                        }
                    }
                }
            }
        }
    }
}
