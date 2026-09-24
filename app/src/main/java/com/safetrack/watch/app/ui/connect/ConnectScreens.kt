package com.safetrack.watch.app.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.safetrack.watch.app.theme.SafeTrackColors
import com.safetrack.watch.app.ui.components.ScreenTitle
import com.safetrack.watch.app.ui.components.StatusDot

/** Device Connection: the child types the guardian's 6-digit connection code. */
@Composable
fun ConnectScreen(viewModel: ConnectViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { ScreenTitle("SafeTrack") }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Connect Watch", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Enter Connection Code", fontSize = 12.sp, color = SafeTrackColors.TextMuted)
                }
            }
            item {
                CodeField(
                    code = viewModel.code,
                    enabled = !state.verifying,
                    onChange = viewModel::onCodeChange,
                    onDone = viewModel::connect,
                )
            }

            state.error?.let { message ->
                item {
                    Text(
                        message,
                        color = SafeTrackColors.Danger,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
            }

            item {
                Button(
                    onClick = viewModel::connect,
                    enabled = !state.verifying && viewModel.code.length == ConnectUiState.CODE_LENGTH,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SafeTrackColors.Green),
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (state.verifying) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Text("CONNECT", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Connection Status", fontSize = 11.sp, color = SafeTrackColors.TextMuted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(if (state.verifying) SafeTrackColors.Warning else SafeTrackColors.Danger, size = 9)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (state.verifying) "Verifying…" else "Not Connected",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }

            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                ) {
                    Text("Watch ID", fontSize = 11.sp, color = SafeTrackColors.TextMuted)
                    Text(
                        state.watchId,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SafeTrackColors.Mint,
                    )
                    Text(
                        "Your guardian adds this ID in the SafeTrack app.",
                        fontSize = 10.sp,
                        color = SafeTrackColors.TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Six code boxes. Tapping them opens the watch's own keyboard in number mode.
 *
 * Wear OS keyboards differ in what their ✓ key does (Done, another action, Enter,
 * or just closing the keyboard), so the code is submitted in every one of those
 * cases, and also as soon as the sixth digit arrives. Submitting twice is
 * harmless: the view model ignores a second call while verifying.
 */
@Composable
private fun CodeField(code: String, enabled: Boolean, onChange: (String) -> Unit, onDone: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val currentCode by rememberUpdatedState(code)

    fun submit() {
        keyboard?.hide()
        focusManager.clearFocus()
        onDone()
    }

    BasicTextField(
        value = code,
        onValueChange = { input ->
            val wasComplete = currentCode.length == ConnectUiState.CODE_LENGTH
            onChange(input)
            val digits = input.filter(Char::isDigit).take(ConnectUiState.CODE_LENGTH)
            if (!wasComplete && digits.length == ConnectUiState.CODE_LENGTH) submit()
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = { submit() },
            onGo = { submit() },
            onSend = { submit() },
            onNext = { submit() },
            onSearch = { submit() },
        ),
        cursorBrush = SolidColor(Color.Transparent),
        modifier = Modifier
            .onPreviewKeyEvent { event ->
                val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                if (enter && event.type == KeyEventType.KeyUp) submit()
                enter
            }
            .onFocusChanged { focus ->
                // Keyboards whose ✓ only closes the keyboard.
                if (!focus.isFocused && currentCode.length == ConnectUiState.CODE_LENGTH) onDone()
            }
            .semantics {
                contentDescription = "Connection code, ${code.length} of 6 digits. Tap to type."
            },
        decorationBox = { innerTextField ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CodeSlots(code)
                Text(
                    if (code.isEmpty()) "Tap to type the code" else "Tap to change",
                    fontSize = 11.sp,
                    color = SafeTrackColors.GreenLight,
                    modifier = Modifier.padding(top = 3.dp),
                )
                // The real text field is invisible; the boxes above show the digits.
                Box(Modifier.size(1.dp).alpha(0f)) { innerTextField() }
            }
        },
    )
}

@Composable
private fun CodeSlots(code: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(ConnectUiState.CODE_LENGTH) { index ->
            val digit = code.getOrNull(index)
            Box(
                Modifier
                    .size(width = 22.dp, height = 32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SafeTrackColors.SurfaceRaised)
                    .border(
                        1.5.dp,
                        if (index == code.length) SafeTrackColors.GreenLight else Color.Transparent,
                        RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(digit?.toString() ?: "", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

/** Connection Success. */
@Composable
fun ConnectedScreen(childName: String, onContinue: () -> Unit) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { ScreenTitle("SafeTrack") }
            item {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = SafeTrackColors.Safe,
                    modifier = Modifier.size(40.dp),
                )
            }
            item { Text("Connected", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(childName.ifBlank { "Child Profile" }, fontSize = 15.sp, color = SafeTrackColors.Mint)
                    Text("Connected Successfully", fontSize = 12.sp, color = SafeTrackColors.TextMuted)
                }
            }
            item {
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SafeTrackColors.Green),
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("CONTINUE", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
