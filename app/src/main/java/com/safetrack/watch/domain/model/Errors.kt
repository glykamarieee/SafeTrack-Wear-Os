package com.safetrack.watch.domain.model

/**
 * Failures the child can understand. Technical details stay in Logcat and are
 * never shown on screen.
 */
sealed interface SafeTrackError {
    val message: String

    data object NoInternet : SafeTrackError {
        override val message = "No internet connection. Please try again when communication is available."
    }

    data object InvalidCode : SafeTrackError {
        override val message = "Connection code is invalid or expired."
    }

    data object NoActiveCode : SafeTrackError {
        override val message = "No connection code is waiting for this watch. Ask your guardian to make a new code."
    }

    data class WatchNotRegistered(val watchId: String) : SafeTrackError {
        override val message = "This watch is not added yet. Ask your guardian to add Watch ID $watchId."
    }

    data object WatchNotLinked : SafeTrackError {
        override val message = "This watch is not linked to a child profile. Ask your guardian for help."
    }

    data object Unlinked : SafeTrackError {
        override val message = "This watch was disconnected from SafeTrack. Ask your guardian for a new code."
    }

    data object StorageFailed : SafeTrackError {
        override val message = "This watch could not save the connection. Restart the watch, then ask your guardian for a new code."
    }

    data object BackendUnavailable : SafeTrackError {
        override val message = "SafeTrack is temporarily unavailable."
    }

    data object NotConfigured : SafeTrackError {
        override val message = "SafeTrack is not set up on this watch. Ask your guardian for help."
    }
}

sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Failed(val error: SafeTrackError) : Outcome<Nothing>
}
