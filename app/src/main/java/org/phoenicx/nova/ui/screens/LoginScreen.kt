package org.phoenicx.nova.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCode2
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.phoenicx.nova.NovaApp
import org.phoenicx.nova.ui.components.NovaButton
import org.phoenicx.nova.ui.components.NovaLoader
import org.phoenicx.nova.ui.components.NovaTextField
import org.phoenicx.nova.ui.components.api
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaType

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val prefs = NovaApp.instance.prefs
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(prefs.server) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var qcCode by remember { mutableStateOf<String?>(null) }
    var qcSecret by remember { mutableStateOf<String?>(null) }
    val serverRequester = remember { FocusRequester() }

    // Highlight the server box (without opening the keyboard) so Quick Connect is a few presses away
    LaunchedEffect(Unit) { delay(100); runCatching { serverRequester.requestFocus() } }
    // After a failed sign-in the buttons briefly disappear (loading), so put the highlight back on the Server box
    LaunchedEffect(error, busy) {
        if (error != null && !busy) { delay(80); runCatching { serverRequester.requestFocus() } }
    }

    // Poll Quick Connect until approved from another device
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
                val base = api.normalizeServer(server)
                val (secret, code) = api.quickConnectInitiate(base)
                qcCode = code; qcSecret = secret
            } catch (e: Exception) {
                error = if (e.message?.contains("401") == true || e.message?.contains("error") == true)
                    "Quick Connect is disabled on this server" else e.message
            } finally {
                busy = false
            }
        }
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 80.dp), verticalAlignment = Alignment.CenterVertically) {
        // Brand side
        Column(Modifier.weight(1f)) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(org.phoenicx.nova.R.drawable.logo_stacked),
                contentDescription = null,
                modifier = Modifier.size(230.dp),
            )
            Spacer(Modifier.height(12.dp))
            Spacer(Modifier.height(12.dp))
            Text("Your cinema, reimagined.", style = NovaType.Section.copy(fontWeight = FontWeight.Normal), color = Nova.TextDim)
        }

        // Glass card
        Box(
            Modifier
                .width(460.dp)
                .background(
                    Brush.verticalGradient(listOf(Nova.SurfaceHi.copy(alpha = 0.20f), Nova.Surface.copy(alpha = 0.40f))),
                    RoundedCornerShape(28.dp),
                )
                .border(1.dp, Nova.Stroke, RoundedCornerShape(28.dp))
                .padding(36.dp)
        ) {
            if (qcCode != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("QUICK CONNECT", style = NovaType.Caption, color = Nova.Cyan)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        qcCode!!.chunked(3).joinToString(" "),
                        style = NovaType.Display.copy(fontSize = 64.sp, letterSpacing = 8.sp),
                        color = Nova.Text,
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "On your phone or computer, open Jellyfin → Profile → Quick Connect and enter this code.",
                        style = NovaType.Body, color = Nova.TextDim,
                    )
                    Spacer(Modifier.height(24.dp))
                    NovaButton("Cancel") { qcCode = null; qcSecret = null }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("Sign in", style = NovaType.Title, color = Nova.Text)
                    NovaTextField(server, { server = it }, "Server", icon = Icons.Outlined.Dns,
                        keyboardType = KeyboardType.Text, placeholder = "your-server.com", focusRequester = serverRequester)
                    NovaTextField(user, { user = it }, "Username", icon = Icons.Outlined.Person)
                    NovaTextField(pass, { pass = it }, "Password", password = true, icon = Icons.Outlined.Lock,
                        imeAction = ImeAction.Done, onDone = { signIn() })
                    if (error != null) Text(error!!, style = NovaType.Label, color = Nova.Rose)
                    if (busy) Box(Modifier.fillMaxWidth().height(48.dp)) { NovaLoader() }
                    else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NovaButton("Sign In", Icons.Outlined.Login, primary = true) { signIn() }
                        NovaButton("Quick Connect", Icons.Outlined.QrCode2) { quickConnect() }
                    }
                }
            }
        }
    }
}
