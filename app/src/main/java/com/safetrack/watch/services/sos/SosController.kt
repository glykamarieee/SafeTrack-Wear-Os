package com.safetrack.watch.services.sos

import android.util.Log
import com.safetrack.watch.data.repository.Iso
import com.safetrack.watch.domain.model.PendingSos
import com.safetrack.watch.domain.model.SosActivation
import com.safetrack.watch.domain.model.SosState
import com.safetrack.watch.domain.repository.DeviceRepository
import com.safetrack.watch.domain.repository.SosRepository
import com.safetrack.watch.services.location.LocationTracker
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The SOS flow, shared by tap-and-hold and shake:
 *
 *   trigger -> countdown (cancellable) -> confirmed -> saved on the watch
 *           -> sent with the latest available location -> Sent, or Pending + retry
 *
 * Nothing reaches SafeTrack until the countdown finishes, so a cancelled SOS
 * never creates an alert. Lives in the application scope (main thread) so it
 * continues if the screen turns off or the activity closes.
 */
class SosController(
    private val scope: CoroutineScope,
    private val devices: DeviceRepository,
    private val sos: SosRepository,
    private val location: LocationTracker,
    private val alerts: SosAlerts,
    private val scheduleRetry: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<SosState>(SosState.Idle)
    val state: StateFlow<SosState> = _state.asStateFlow()

    private var countdown: Job? = null
    private var autoReturn: Job? = null

    // The last confirmed SOS, for "sent by mistake".
    private var lastLocalId: String? = null
    private var lastAlertId: String? = null

    // The last sent (or pending) SOS screen, kept for [RECENT_SOS_MS] after it was sent so the
    // dashboard can reopen it: "Sent by mistake" stays reachable after the screen closes.
    private var lastSentScreen: SosState? = null
    private var recentClear: Job? = null
    private val _recentSentAt = MutableStateFlow<Long?>(null)
    val recentSentAt: StateFlow<Long?> = _recentSentAt.asStateFlow()

    /** Reopens the last SOS's Sent / Pending screen (with "Sent by mistake"). */
    fun reopenSent(): Boolean {
        val sentAt = _recentSentAt.value ?: return false
        val screen = lastSentScreen ?: return false
        if (_state.value != SosState.Idle) return false
        if (clock() - sentAt > RECENT_SOS_MS) {
            forgetRecent()
            return false
        }
        showThenReturn(screen, SENT_SCREEN_MS)
        return true
    }

    private fun rememberRecent(screen: SosState) {
        lastSentScreen = screen
        _recentSentAt.value = clock()
        recentClear?.cancel()
        recentClear = scope.launch {
            delay(RECENT_SOS_MS)
            forgetRecent()
        }
    }

    private fun forgetRecent() {
        recentClear?.cancel()
        recentClear = null
        lastSentScreen = null
        _recentSentAt.value = null
    }

    /** Starts the countdown. Ignored while another SOS is counting down or sending. */
    fun trigger(activation: SosActivation): Boolean {
        if (devices.session.value == null) return false
        val current = _state.value
        if (current is SosState.Countdown || current is SosState.Sending) return false

        autoReturn?.cancel()
        val activatedAt = clock()
        Log.i(TAG, "SOS activated by $activation")

        countdown = scope.launch {
            alerts.showCountdown()
            for (seconds in COUNTDOWN_SECONDS downTo 1) {
                _state.value = SosState.Countdown(activation, seconds)
                alerts.tick()
                delay(1_000)
            }
            // Past this point the SOS is confirmed and can no longer be cancelled.
            _state.value = SosState.Sending
            withContext(NonCancellable) { confirm(activation, activatedAt) }
        }
        return true
    }

    /** Cancels during the countdown only. */
    fun cancel() {
        if (_state.value !is SosState.Countdown) return
        countdown?.cancel()
        countdown = null
        alerts.dismiss()
        alerts.cancelled()
        Log.i(TAG, "SOS cancelled during countdown")
        showThenReturn(SosState.Cancelled, CANCELLED_SCREEN_MS)
    }

    /** Closes the Sent / Pending / Cancelled screen. */
    fun dismiss() {
        val current = _state.value
        if (current is SosState.Sent || current is SosState.Pending || current is SosState.Cancelled || current is SosState.Withdrawn) {
            autoReturn?.cancel()
            _state.value = SosState.Idle
        }
    }

    /**
     * "I'm OK – sent by mistake" on the Sent or Pending screen (or Try again
     * after a failed attempt). An unsent SOS is dropped from the queue; a sent
     * one is cancelled on SafeTrack, which tells the guardian.
     */
    fun withdraw() {
        val current = _state.value
        val retry = current is SosState.Withdrawn && !current.done
        if (current !is SosState.Sent && current !is SosState.Pending && !retry) return
        val localId = lastLocalId ?: return

        autoReturn?.cancel()
        _state.value = SosState.Withdrawing
        scope.launch {
            val done = withContext(NonCancellable) {
                sos.discard(localId)
                val alertId = (current as? SosState.Sent)?.alertId ?: lastAlertId ?: sos.deliveredAlertId(localId)
                lastAlertId = alertId
                // Nothing reached SafeTrack (still pending): dropping it is enough.
                if (alertId == null) current is SosState.Pending else sos.cancelDelivered(alertId)
            }
            Log.i(TAG, "SOS withdrawn by the child (done=$done)")
            if (done) {
                alerts.cancelled()
                forgetRecent()
            }
            showThenReturn(SosState.Withdrawn(done), if (done) RESULT_SCREEN_MS else SENT_SCREEN_MS)
        }
    }

    private suspend fun confirm(activation: SosActivation, activatedAtMillis: Long) {
        alerts.confirmed()
        val session = devices.session.value
        if (session == null) {
            alerts.dismiss()
            _state.value = SosState.Idle
            return
        }

        // Save first, with whatever location is already known, so the SOS
        // survives if the app is killed while a fresh fix is being obtained.
        lastAlertId = null
        var record = PendingSos(
            localId = UUID.randomUUID().toString(),
            childId = session.childId,
            activation = activation.wireValue,
            triggeredAt = Iso.format(activatedAtMillis),
            location = location.recentKnown(),
        )
        sos.save(record)
        lastLocalId = record.localId
        Log.i(TAG, "SOS confirmed and saved (${record.localId})")

        // The guardian follows the child closely for the next 10 minutes.
        location.boostForSos()

        location.locationForSos()?.let { fresh ->
            record = record.copy(location = fresh)
            sos.save(record)
        }

        val sent = sos.transmit(record.localId)
        alerts.dismiss()
        val hasLocation = record.location != null

        if (sent) {
            Log.i(TAG, "SOS transmitted (${record.localId})")
            lastAlertId = sos.deliveredAlertId(record.localId)
            val screen = SosState.Sent(hasLocation, lastAlertId)
            rememberRecent(screen)
            showThenReturn(screen, SENT_SCREEN_MS)
        } else {
            Log.w(TAG, "SOS pending; retry scheduled (${record.localId})")
            scheduleRetry()
            val screen = SosState.Pending(hasLocation)
            rememberRecent(screen)
            showThenReturn(screen, SENT_SCREEN_MS)
        }
    }

    private fun showThenReturn(state: SosState, afterMs: Long) {
        _state.value = state
        autoReturn?.cancel()
        autoReturn = scope.launch {
            delay(afterMs)
            if (_state.value == state) _state.value = SosState.Idle
        }
    }

    companion object {
        private const val TAG = "SosController"
        // Time to notice and cancel a mistaken SOS (same as the phone app).
        const val COUNTDOWN_SECONDS = 5
        const val CANCELLED_SCREEN_MS = 2_500L
        const val RESULT_SCREEN_MS = 12_000L
        // Long enough to notice a mistake and tap "Sent by mistake".
        const val SENT_SCREEN_MS = 30_000L
        // After the Sent screen closes, the dashboard offers "Sent by mistake" this long
        // (the same 10 minutes the guardian follows the child closely after an SOS).
        const val RECENT_SOS_MS = 10 * 60_000L
    }
}
