package com.safetrack.watch.app.ui.connect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.safetrack.watch.app.theme.LocalReduceMotion
import com.safetrack.watch.app.theme.SafeTrackColors
import com.safetrack.watch.app.ui.components.BodyText
import com.safetrack.watch.app.ui.components.Eyebrow
import com.safetrack.watch.app.ui.components.PillButton
import com.safetrack.watch.app.ui.components.SafeTrackBrand
import com.safetrack.watch.app.ui.components.ScreenTitle
import com.safetrack.watch.app.ui.components.StatusPill
import com.safetrack.watch.app.ui.components.entrance
import com.safetrack.watch.app.ui.components.firstName
import com.safetrack.watch.app.ui.components.rememberPulse
import com.safetrack.watch.app.ui.components.roundInset

/** Device Connection: the child types the guardian's existing 6-digit connection code. */
@Composable
fun ConnectScreen(viewModel: ConnectViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)

    Box(Modifier.fillMaxSize().background(SafeTrackColors.Background)) {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            ScalingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { Box(Modifier.entrance(0)) { SafeTrackBrand(compact = true) } }

                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.roundInset(0.06f).entrance(1)) {
                        Eyebrow("Child watch")
                        ScreenTitle("Link this watch")
                        BodyText("Enter the 6-digit code from the Guardian app.", maxLines = 3)
                    }
                }

                item {
                    Box(Modifier.entrance(2)) {
                        CodeField(
                            code = viewModel.code,
                            enabled = !state.verifying,
                            onChange = viewModel::onCodeChange,
                            onDone = viewModel::connect,
                        )
                    }
                }

                state.error?.let { message ->
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .roundInset(0.05f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(SafeTrackColors.WarningSoft)
                                .border(1.dp, SafeTrackColors.WarningFill.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                message,
                                color = SafeTrackColors.Warning,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                item {
                    PillButton(
                        label = "CONNECT WATCH",
                        onClick = viewModel::connect,
                        enabled = !state.verifying && viewModel.code.length == ConnectUiState.CODE_LENGTH,
                        loading = state.verifying,
                        modifier = Modifier.roundInset(0.06f).widthIn(max = 170.dp),
                    )
                }

                item {
                    StatusPill(
                        label = if (state.verifying) "Checking code" else "Waiting to link",
                        color = if (state.verifying) SafeTrackColors.Warning else SafeTrackColors.TextFaint,
                        blinking = state.verifying,
                    )
                }

                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.roundInset(0.06f).padding(top = 2.dp),
                    ) {
                        Eyebrow("Watch ID", color = SafeTrackColors.TextFaint)
                        Text(
                            state.watchId,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SafeTrackColors.Forest,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Text(
                            "Your guardian types this ID in the app.",
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            color = SafeTrackColors.TextFaint,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
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
                    if (code.isEmpty()) "Tap the boxes to type" else "${code.length} of 6 digits",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (code.length == ConnectUiState.CODE_LENGTH) SafeTrackColors.Green else SafeTrackColors.TextFaint,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Box(Modifier.size(1.dp).alpha(0f)) { innerTextField() }
            }
        },
    )
}

@Composable
private fun CodeSlots(code: String) {
    val blink = rememberPulse(600, still = 1f)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(ConnectUiState.CODE_LENGTH) { index ->
            val digit = code.getOrNull(index)
            val active = index == code.length && code.length < ConnectUiState.CODE_LENGTH
            val filled = digit != null
            val pop = remember { Animatable(1f) }
            LaunchedEffect(filled) {
                if (filled) {
                    pop.snapTo(0.7f)
                    pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
            }
            val shape = RoundedCornerShape(9.dp)
            Box(
                Modifier
                    .size(width = 22.dp, height = 30.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
                    .clip(shape)
                    .background(if (filled) SafeTrackColors.Mint else SafeTrackColors.Surface)
                    .border(
                        width = if (active) 1.5.dp else 1.dp,
                        color = when {
                            active -> SafeTrackColors.Green.copy(alpha = 0.35f + 0.65f * blink)
                            filled -> SafeTrackColors.GreenBright.copy(alpha = 0.6f)
                            else -> SafeTrackColors.Divider
                        },
                        shape = shape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    digit?.toString() ?: "•",
                    fontSize = if (filled) 16.sp else 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (filled) SafeTrackColors.Forest else SafeTrackColors.TextFaint,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Connection Success: a bouncing check with a ripple and little sparkles. */
@Composable
fun ConnectedScreen(childName: String, onContinue: () -> Unit) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    val reduce = LocalReduceMotion.current
    val pop = remember { Animatable(if (reduce) 1f else 0.3f) }
    val burst = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    LaunchedEffect(Unit) { burst.animateTo(1f, tween(1100)) }

    Box(Modifier.fillMaxSize().background(SafeTrackColors.Background)) {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            ScalingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                item { SafeTrackBrand(compact = true) }
                item {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            val b = burst.value
                            if (b < 1f) {
                                val radius = size.minDimension / 2
                                drawCircle(SafeTrackColors.GreenBright.copy(alpha = 0.45f * (1f - b)), radius * (0.55f + 0.45f * b), style = Stroke(3.dp.toPx()))
                                // Eight sparkles flying outward.
                                repeat(8) { i ->
                                    val angle = Math.toRadians(i * 45.0 - 90.0)
                                    val distance = radius * (0.55f + 0.45f * b)
                                    drawCircle(
                                        color = if (i % 2 == 0) SafeTrackColors.GreenBright.copy(alpha = 1f - b) else SafeTrackColors.WarningFill.copy(alpha = 1f - b),
                                        radius = 2.5.dp.toPx(),
                                        center = center + Offset((kotlin.math.cos(angle) * distance).toFloat(), (kotlin.math.sin(angle) * distance).toFloat()),
                                    )
                                }
                            }
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(52.dp)
                                .graphicsLayer {
                                    scaleX = pop.value
                                    scaleY = pop.value
                                }
                                .clip(CircleShape)
                                .background(Brush.verticalGradient(listOf(SafeTrackColors.GreenBright, SafeTrackColors.Green))),
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                }
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.roundInset(0.06f)) {
                        Eyebrow("Watch linked")
                        val first = firstName(childName)
                        ScreenTitle(if (first.isBlank()) "All set!" else "Hi, $first!")
                        BodyText("Monitoring can now start on this watch.", maxLines = 3)
                    }
                }
                item {
                    PillButton(label = "CONTINUE", onClick = onContinue, modifier = Modifier.roundInset(0.06f).widthIn(max = 160.dp))
                }
            }
        }
    }
}
