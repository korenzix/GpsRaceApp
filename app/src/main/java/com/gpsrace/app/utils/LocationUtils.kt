package com.gpsrace.app.utils

import android.location.Location

/**
 * Pure utility functions for location math and formatting.
 * No Android context dependency — easy to unit test.
 */
object LocationUtils {

    // ─────────────────────────────────────────────────────────────────────────
    // Distance
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Distance in meters between two GPS coordinates.
     * Uses Android's Location.distanceTo() which implements the WGS84 ellipsoid
     * model — accurate to ~0.5% which is more than sufficient for a 100m race.
     *
     * Note: For distances < 1km, the flat-earth approximation is ~equally
     * accurate but this is simpler and already available on the Location object.
     */
    fun distanceBetween(from: Location, to: Location): Float {
        return from.distanceTo(to)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Anti-cheat checks
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Check whether the GPS-reported speed is physically possible for a human.
     *
     * Reference speeds:
     *   - Usain Bolt peak sprint: ~12.4 m/s
     *   - Safe upper bound with GPS jitter buffer: 15 m/s (~54 km/h)
     *
     * Future improvement: tighten this to ~6 m/s for a walking-only race mode.
     *
     * @param speedMps Speed in metres per second.
     */
    fun isSpeedReasonable(speedMps: Float): Boolean {
        return speedMps in 0f..15f
    }

    /**
     * Check whether a GPS position jump is physically possible given the time elapsed.
     *
     * A sudden 50m jump in 200ms implies 250 m/s — clearly a GPS error.
     * We use a generous 20 m/s limit here (slightly above the speed check) to
     * account for brief GPS inaccuracy spikes without over-filtering.
     *
     * @param distanceMeters Distance between the two points, in meters.
     * @param timeDeltaMs    Time between the two readings, in milliseconds.
     */
    fun isJumpReasonable(distanceMeters: Float, timeDeltaMs: Long): Boolean {
        if (timeDeltaMs <= 0L) return false
        val impliedSpeedMps = distanceMeters / (timeDeltaMs / 1000f)
        return impliedSpeedMps <= 20f
    }

    /**
     * Check GPS horizontal accuracy.
     * Readings worse than 20m are noisy enough to skew distance accumulation.
     * Relax this threshold if GPS takes too long to get a good fix in testing.
     *
     * @param accuracyMeters Location.getAccuracy() value (1-sigma radius in meters).
     */
    fun isAccuracyAcceptable(accuracyMeters: Float): Boolean {
        return accuracyMeters <= 20f
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Formatting helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Format elapsed milliseconds as M:SS.mmm
     * Example: 75_432L → "1:15.432"
     */
    fun formatTime(elapsedMs: Long): String {
        val totalSeconds = elapsedMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = elapsedMs % 1000
        return if (minutes > 0) {
            "%d:%02d.%03d".format(minutes, seconds, millis)
        } else {
            "%d.%03d".format(seconds, millis)
        }
    }

    /**
     * Format speed from m/s to a user-facing km/h string.
     * Example: 3.5f → "12.6 km/h"
     */
    fun formatSpeedKmh(speedMps: Float): String {
        return "%.1f km/h".format(speedMps * 3.6f)
    }

    /**
     * Format distance for display.
     * Example: 47.3f → "47.3 m"
     */
    fun formatDistance(meters: Float): String {
        return "%.1f m".format(meters)
    }
}
