package com.safetrack.watch.app.ui.connect

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import com.safetrack.watch.app.ui.components.Eyebrow
import com.safetrack.watch.app.ui.components.SafeTrackBrand
import com.safetrack.watch.app.ui.components.StatusPill

/** Device Connection: the child types the guardian's existing 6-digit connection code. */
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
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    SafeTrackBrand()
                    Eyebrow("Child watch")
                    Text(
                        "Link this watch",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SafeTrackColors.TextPrimary,
                    )
                    Text(
                        "Enter the 6-digit code from the Guardian app.",
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = SafeTrackColors.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                    )
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SafeTrackColors.Danger.copy(alpha = 0.11f))
                            .border(1.dp, SafeTrackColors.Danger.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            message,
                            color = SafeTrackColors.Danger,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = viewModel::connect,
                    enabled = !state.verifying && viewModel.code.length == ConnectUiState.CODE_LENGTH,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafeTrackColors.Green,
                        contentColor = Color.Black,
                    ),
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (state.verifying) {
                            CircularProgressIndicator(modifier = Modifier.size(23.dp))
                        } else {
                            Text("CONNECT WATCH", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                    }
                }
            }

            item {
                StatusPill(
                    label = if (state.verifying) "Verifying code" else "Waiting to link",
                    color = if (state.verifying) SafeTrackColors.Warning else SafeTrackColors.Neutral,
                )
            }

            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 3.dp, bottom = 8.dp),
                ) {
                    Eyebrow("Watch ID", color = SafeTrackColors.TextFaint)
                    Text(
                        state.watchId,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SafeTrackColors.Mint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        "This identifies this smartwatch during linking.",
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        color = SafeTrackColors.TextFaint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Six code slots backed by the watch keyboard. Existing auto-submit behavior is
 * preserved: reaching six digits or pressing the keyboard action calls [onDone].
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
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
        ),
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
                if (!focus.isFocused && currentCode.length == ConnectUiState.CODE_LENGTH) onDone()
            }
            .semantics {
                contentDescription = "Connection code, ${code.length} of 6 digits. Tap to type."
            },
        decorationBox = { innerTextField ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CodeSlots(code)
                Text(
                    if (code.isEmpty()) "Tap the digits to enter your code" else "${code.length}/6 digits entered",
                    fontSize = 9.sp,
                    color = if (code.length == ConnectUiState.CODE_LENGTH) SafeTrackColors.Safe else SafeTrackColors.TextMuted,
                    modifier = Modifier.padding(top = 5.dp),
                )
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
            val active = index == code.length && code.length < ConnectUiState.CODE_LENGTH
            val filled = digit != null
            Box(
                Modifier
                    .size(width = 25.dp, height = 34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        when {
                            filled -> SafeTrackColors.SurfaceAccent
                            else -> SafeTrackColors.SurfaceRaised
                        },
                    )
                    .border(
                        width = if (active || filled) 1.25.dp else 1.dp,
                        color = when {
                            active -> SafeTrackColors.GreenLight
                            filled -> SafeTrackColors.Green.copy(alpha = 0.48f)
                            else -> SafeTrackColors.Divider
                        },
                        shape = RoundedCornerShape(9.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    digit?.toString() ?: "•",
                    fontSize = if (filled) 18.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (filled) SafeTrackColors.TextPrimary else SafeTrackColors.TextFaint,
                )
            }
        }
    }
}

/** Connection Success. */
@Composable
fun ConnectedScreen(childName: String, onContinue: () -> Unit) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            item { SafeTrackBrand() }
            item {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(SafeTrackColors.Safe.copy(alpha = 0.13f))
                        .border(1.dp, SafeTrackColors.Safe.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = SafeTrackColors.Safe,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Eyebrow("Watch linked")
                    Text(
                        childName.ifBlank { "Child Profile" },
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SafeTrackColors.TextPrimary,
                    )
                    Text(
                        "SafeTrack can now begin monitoring on this watch.",
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = SafeTrackColors.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                    )
                }
            }
            item {
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafeTrackColors.Green,
                        contentColor = Color.Black,
                    ),
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("CONTINUE", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
