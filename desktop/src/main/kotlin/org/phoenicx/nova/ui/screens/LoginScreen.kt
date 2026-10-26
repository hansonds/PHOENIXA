package org.phoenicx.nova.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.phoenicx.nova.App
import org.phoenicx.nova.ui.Loader
import org.phoenicx.nova.ui.Nova
import org.phoenicx.nova.ui.NovaButton
import org.phoenicx.nova.ui.NovaType

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val api = App.api
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(App.prefs.server) }
    var user by remember { mutableStateOf(App.prefs.userName ?: "") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var qcCode by remember { mutableStateOf<String?>(null) }
    var qcSecret by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(qcSecret) {
        val secret = qcSecret ?: return@LaunchedEffect
        val base = api.normalizeServer(server)
        repeat(150) {
            delay(2000)
            val ok = runCatching { api.quickConnectCheck(base, secret) }.getOrDefault(false)
            if (ok) {
                runCatching { api.quickConnectFinish(base, secret) }
                    .onSuccess { onLoggedIn() }
                    .onFailure { error = it.message; qcSecret = null; qcCode = null }
                return@LaunchedEffect
            }
        }
        qcSecret = null; qcCode = null
        error = "Quick Connect timed out"
    }

    fun signIn() {
        if (busy) return
        if (server.isBlank()) { error = "Enter your server address first"; return }
        busy = true; error = null
        scope.launch {
            try {
                val base = api.normalizeServer(server)
                api.ping(base)
                api.login(base, user.trim(), pass)
                onLoggedIn()
            } catch (e: Exception) {
                error = e.message ?: "Couldn't sign in"
            } finally {
                busy = false
            }
        }
    }

    fun quickConnect() {
        if (busy) return
        if (server.isBlank()) { error = "Enter your server address first"; return }
        busy = true; error = null
        scope.launch {
            try {
                val (secret, code) = api.quickConnectInitiate(api.normalizeServer(server))
                qcCode = code; qcSecret = secret
            } catch (e: Exception) {
                error = if (e.message?.contains("401") == true) "Quick Connect is disabled on this server" else e.message
            } finally {
                busy = false
            }
        }
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 96.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Image(painterResource("logo_stacked.png"), "PHOENIXA", modifier = Modifier.size(260.dp))
            Spacer(Modifier.height(16.dp))
            Text("Your cinema, reimagined.", style = NovaType.Title.copy(fontWeight = FontWeight.Normal), color = Nova.TextDim)
            Spacer(Modifier.height(8.dp))
            Text("Sign in to your Jellyfin server to begin.", style = NovaType.Body, color = Nova.TextFaint)
        }
        Box(
            Modifier
                .width(480.dp)
                .background(Brush.verticalGradient(listOf(Nova.SurfaceHi.copy(alpha = 0.55f), Nova.Surface.copy(alpha = 0.75f))), RoundedCornerShape(28.dp))
                .border(1.dp, Nova.Stroke, RoundedCornerShape(28.dp))
                .padding(40.dp)
        ) {
            if (qcCode != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("QUICK CONNECT", style = NovaType.Caption, color = Nova.Cyan)
                    Spacer(Modifier.height(18.dp))
                    Text(qcCode!!.chunked(3).joinToString(" "), style = NovaType.Display.copy(fontSize = 60.sp, letterSpacing = 8.sp), color = Nova.Text)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "On your phone or another device, open Jellyfin → Profile → Quick Connect and enter this code.",
                        style = NovaType.Body, color = Nova.TextDim,
                    )
                    Spacer(Modifier.height(24.dp))
                    NovaButton("Cancel") { qcCode = null; qcSecret = null }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Sign in", style = NovaType.Title, color = Nova.Text)
                    Field(server, { server = it }, "Server", "your-server.com", Icons.Outlined.Dns) { signIn() }
                    Field(user, { user = it }, "Username", "", Icons.Outlined.Person) { signIn() }
                    Field(pass, { pass = it }, "Password", "", Icons.Outlined.Lock, password = true) { signIn() }
                    if (error != null) Text(error!!, style = NovaType.Label, color = Nova.Rose)
                    if (busy) Loader(Modifier.height(64.dp))
                    else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NovaButton("Sign In", Icons.Outlined.Login, primary = true) { signIn() }
                        NovaButton("Quick Connect", Icons.Outlined.QrCode2) { quickConnect() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    password: Boolean = false,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, color = Nova.TextFaint) },
        leadingIcon = { Icon(icon, null, tint = Nova.TextDim) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = if (password) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Nova.Cyan,
            unfocusedBorderColor = Nova.Stroke,
            focusedLabelColor = Nova.Cyan,
            unfocusedLabelColor = Nova.TextDim,
            cursorColor = Nova.Cyan,
            focusedTextColor = Nova.Text,
            unfocusedTextColor = Nova.Text,
            focusedContainerColor = Color(0x14FFFFFF),
            unfocusedContainerColor = Color(0x0AFFFFFF),
        ),
        modifier = Modifier.fillMaxWidth().onEnter(onDone),
    )
}

/** Pressing Enter in a text box signs in (desktop keyboards don't send "Done") */
private fun Modifier.onEnter(action: () -> Unit): Modifier = this.onPreviewKeyEvent { e ->
    if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { action(); true } else false
}
