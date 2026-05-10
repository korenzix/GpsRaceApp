package com.gpsrace.app.race

import android.location.Location

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

/** Target race distance in meters. Change this to support different distances. */
const val RACE_DISTANCE_METERS = 100f

// ─────────────────────────────────────────────────────────────────────────────
// Race Status — sealed hierarchy keeps state transitions explicit and safe
// ─────────────────────────────────────────────────────────────────────────────

/**
 * All possible states of a race session.
 *
 * Future states to add:
 *   - WaitingForPlayers(playerCount: Int, capacity: Int)  — matchmaking lobby
 *   - Countdown(secondsRemaining: Int)                    — 3-2-1 countdown
 *   - Syncing                                             — reconnecting to server
 *   - Disqualified(reason: String)                        — anti-cheat violation
 */
sealed class RaceStatus {

    /** App open, waiting for user to press START. GPS may or may not be ready. */
    object Idle : RaceStatus()

    /**
     * Race in progress.
     * [playerRank] reserved for multiplayer: 1 = currently leading, etc.
     */
    data class Running(
        val playerRank: Int = 1 // TODO: populate from server in multiplayer
    ) : RaceStatus()

    /**
     * User crossed the finish line.
     * @param finalTimeMs Total elapsed race time in milliseconds.
     * @param position    Finish position (1st, 2nd, …) — always 1 in single-player.
     */
    data class Finished(
        val finalTimeMs: Long,
        val position: Int = 1  // TODO: populate from server in multiplayer
    ) : RaceStatus()
}

// ─────────────────────────────────────────────────────────────────────────────
// UI State — single source of truth for the main screen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Immutable snapshot of everything the UI needs to render.
 * Emitted by RaceViewModel as a StateFlow<RaceUiState>.
 *
 * Design note: keeping all UI state in one data class makes it trivial
 * to add fields (e.g. opponent positions) without changing the flow contract.
 */
data class RaceUiState(

    // ── Race state ──────────────────────────────────────────────────────────
    val status: RaceStatus = RaceStatus.Idle,

    /** Cumulative GPS distance covered since START, in meters. */
    val distanceMeters: Float = 0f,

    /** GPS-reported speed in m/s at last valid reading. */
    val currentSpeedMps: Float = 0f,

    /** Elapsed time since START button press, in milliseconds. */
    val elapsedMs: Long = 0L,

    // ── Location ─────────────────────────────────────────────────────────────
    /** Latest GPS fix — used for map camera and current-position marker. */
    val currentLocation: Location? = null,

    /** Location when START was pressed — used for start marker on map. */
    val startLocation: Location? = null,

    /** GPS fix accuracy radius in meters (smaller = better). */
    val gpsAccuracyMeters: Float = 0f,

    /** True once at least one GPS fix has been received. */
    val isGpsAvailable: Boolean = false,

    // ── Anti-cheat diagnostics (shown in debug mode) ──────────────────────
    /** How many location updates were discarded by anti-cheat rules. */
    val ignoredPoints: Int = 0,

    // ── Future: Multiplayer ───────────────────────────────────────────────
    // val opponents: List<OpponentState> = emptyList()
    // val roomId: String? = null
    // val countdown: Int? = null
)
