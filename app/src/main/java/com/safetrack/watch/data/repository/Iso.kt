package com.safetrack.watch.data.repository

import java.time.Instant
import java.time.temporal.ChronoUnit

internal object Iso {
    /** UTC, millisecond precision, e.g. 2026-09-24T08:15:30.123Z. */
    fun format(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).truncatedTo(ChronoUnit.MILLIS).toString()
}
