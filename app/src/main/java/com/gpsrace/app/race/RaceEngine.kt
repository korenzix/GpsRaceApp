package com.gpsrace.app.race

import android.location.Location
import com.gpsrace.app.utils.LocationUtils

/**
 * Core race logic — pure business logic with no Android UI/framework dependencies.
 *
 * Responsibilities:
 *   1. Track cumulative GPS distance since START.
 *   2. Apply anti-cheat filters on each incoming location update.
 *   3. Determine when the race is complete.
 *   4. Provide elapsed time calculation.
 *
 * Design principle:
 *   This class knows nothing about coroutines, ViewModel, or Compose.
 *   That makes it straightforward to unit-test and also to swap for a
 *   network-synced version (e.g. where distance is validated server-side).
 *
 * TODO (multiplayer):
 *   - Add a NetworkRaceEngine that submits location updates to a WebSocket / Firebase
 *     and receives authoritative distance from the server instead of computing locally.
 *   - Add server-side anti-cheat validation as a second layer on top of client checks.
 */
class RaceEngine {

    // ─────────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────────

    private var startTimeMs: Long = 0L
    private var lastValidLocation: Location? = null

    private var _totalDistanceMeters: Float = 0f
    private var _ignoredPointCount: Int = 0
    private var _acceptedPointCount: Int = 0

    // Read-only accessors
    val totalDistanceMeters: Float get() = _totalDistanceMeters
    val ignoredPointCount: Int      get() = _ignoredPointCount
    val acceptedPointCount: Int     get() = _acceptedPointCount

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Initialise the engine for a new race.
     * Call this when the user presses START and you have the first GPS fix.
     *
     * @param startLocation  The GPS coordinate at the moment START is pressed.
     * @param startTimeMs    System.currentTimeMillis() at race start.
     */
    fun startRace(startLocation: Location, startTimeMs: Long) {
        this.startTimeMs = startTimeMs
        lastValidLocation = startLocation
        _totalDistanceMeters = 0f
        _ignoredPointCount = 0
        _acceptedPointCount = 0
    }

    /**
     * Process a new GPS location update during a race.
     *
     * Anti-cheat pipeline (applied in order, each can reject the point):
     *   1. Accuracy check  — ignore noisy readings (>20m horizontal accuracy).
     *   2. Jump check      — ignore physically impossible position jumps.
     *   3. Speed check     — cross-validate GPS-reported speed.
     *
     * If the point passes all checks, its distance is added to the total.
     *
     * @param newLocation   The incoming GPS location.
     * @return true if the point was accepted, false if rejected by anti-cheat.
     */
    fun processLocation(newLocation: Location): Boolean {
        val last = lastValidLocation
            ?: return false // No start point yet — shouldn't happen if used correctly

        val distance = LocationUtils.distanceBetween(last, newLocation)
        val timeDeltaMs = newLocation.time - last.time

        // ── Anti-cheat filter 1: GPS accuracy ──────────────────────────────
        // Skip if the device itself reports a poor fix.
        if (!LocationUtils.isAccuracyAcceptable(newLocation.accuracy)) {
            _ignoredPointCount++
            return false
        }

        // ── Anti-cheat filter 2: position jump ─────────────────────────────
        // Reject teleportation (GPS signal dropout → sudden large jump).
        if (!LocationUtils.isJumpReasonable(distance, timeDeltaMs)) {
            _ignoredPointCount++
            return false
        }

        // ── Anti-cheat filter 3: speed ─────────────────────────────────────
        // The GPS chipset reports speed independently of position delta.
        // If it says the user is moving faster than physically possible, reject.
        if (newLocation.hasSpeed() && !LocationUtils.isSpeedReasonable(newLocation.speed)) {
            _ignoredPointCount++
            return false
        }

        // ── Accept this point ──────────────────────────────────────────────
        _totalDistanceMeters += distance
        lastValidLocation = newLocation
        _acceptedPointCount++
        return true
    }

    /**
     * Whether the user has covered the full race distance.
     */
    fun isRaceComplete(): Boolean = _totalDistanceMeters >= RACE_DISTANCE_METERS

    /**
     * Elapsed time in milliseconds since startRace() was called.
     * @param currentTimeMs  Pass System.currentTimeMillis().
     */
    fun getElapsedMs(currentTimeMs: Long): Long = currentTimeMs - startTimeMs

    /**
     * Reset engine to pristine state (call before re-using for a new race).
     */
    fun reset() {
        startTimeMs = 0L
        lastValidLocation = null
        _totalDistanceMeters = 0f
        _ignoredPointCount = 0
        _acceptedPointCount = 0
    }
}
