package com.gpsrace.app.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.gpsrace.app.location.LocationTracker
import com.gpsrace.app.race.RaceEngine
import com.gpsrace.app.race.RaceStatus
import com.gpsrace.app.race.RaceUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the main race screen.
 *
 * Responsibilities:
 *   - Start/stop GPS location updates.
 *   - Feed location updates into [RaceEngine] during a race.
 *   - Drive a high-frequency timer for smooth UI updates.
 *   - Expose [RaceUiState] and [racePath] as StateFlows for the Compose UI.
 *
 * Architecture notes:
 *   - [RaceEngine] is a pure logic class — easy to swap for a networked version.
 *   - GPS flow is active as soon as permissions are granted (pre-race), so the
 *     user sees their position on the map before pressing START.
 *   - Timer runs at 50 ms (20 fps) for a smooth elapsed-time display.
 *
 * TODO (online multiplayer):
 *   - Inject a NetworkRaceService that sends location updates to Firebase/Node.js.
 *   - Subscribe to opponent locations from server and add to [RaceUiState].
 *   - Add a Countdown state before the race starts.
 *   - Validate final time server-side to prevent time tampering.
 *
 * TODO (matchmaking):
 *   - Add joinRoom(roomId: String) / createRoom() methods.
 *   - Expose room state (waiting players, capacity, etc.) in UI state.
 */
class RaceViewModel(application: Application) : AndroidViewModel(application) {

    // ─────────────────────────────────────────────────────────────────────────
    // Dependencies
    // ─────────────────────────────────────────────────────────────────────────

    private val locationTracker = LocationTracker(application)
    private val raceEngine = RaceEngine()

    // ─────────────────────────────────────────────────────────────────────────
    // State exposed to UI
    // ─────────────────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(RaceUiState())
    val uiState: StateFlow<RaceUiState> = _uiState.asStateFlow()

    /**
     * Ordered list of LatLng points the user has moved through during the race.
     * Rendered as a Polyline on the map.
     * Reset to empty each time a new race starts.
     */
    private val _racePath = MutableStateFlow<List<LatLng>>(emptyList())
    val racePath: StateFlow<List<LatLng>> = _racePath.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────
    // Coroutine jobs
    // ─────────────────────────────────────────────────────────────────────────

    private var locationJob: Job? = null
    private var timerJob: Job? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Public API — called by the Compose UI
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Begin receiving GPS updates.
     * Call this as soon as ACCESS_FINE_LOCATION permission is granted.
     * Safe to call multiple times — re-entrancy guard prevents double subscriptions.
     */
    fun startLocationUpdates() {
        if (locationJob?.isActive == true) return

        locationJob = viewModelScope.launch {
            locationTracker.locationFlow(
                intervalMs = 1_000L,
                fastestIntervalMs = 500L,
                minDisplacementM = 0.3f  // Small displacement threshold for race accuracy
            ).collect { location ->
                handleLocationUpdate(location)
            }
        }
    }

    /**
     * Stop GPS updates (called when permissions are revoked or app goes background).
     * The race will be automatically paused if it was in progress.
     */
    fun stopLocationUpdates() {
        locationJob?.cancel()
        locationJob = null
        _uiState.update { it.copy(isGpsAvailable = false) }
    }

    /**
     * Start a new race from the user's current GPS position.
     *
     * Preconditions:
     *   - Location permission must be granted.
     *   - At least one GPS fix must have been received ([uiState.currentLocation] != null).
     *
     * If these aren't met the call is silently ignored (UI should disable the button).
     */
    fun startRace() {
        val currentLoc = _uiState.value.currentLocation ?: return
        if (_uiState.value.status is RaceStatus.Running) return

        val now = System.currentTimeMillis()

        // Initialise the race engine with current position as origin
        raceEngine.reset()
        raceEngine.startRace(currentLoc, now)

        // Reset the path and add the starting point
        _racePath.value = listOf(LatLng(currentLoc.latitude, currentLoc.longitude))

        _uiState.update {
            it.copy(
                status = RaceStatus.Running(),
                startLocation = currentLoc,
                distanceMeters = 0f,
                currentSpeedMps = 0f,
                elapsedMs = 0L,
                ignoredPoints = 0
            )
        }

        // High-frequency timer so the clock display doesn't feel laggy
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.status is RaceStatus.Running) {
                delay(50L) // ~20 fps
                _uiState.update { state ->
                    if (state.status is RaceStatus.Running) {
                        state.copy(elapsedMs = raceEngine.getElapsedMs(System.currentTimeMillis()))
                    } else state
                }
            }
        }
    }

    /**
     * Reset everything back to Idle.
     * The GPS stream keeps running so the user sees their position.
     */
    fun resetRace() {
        timerJob?.cancel()
        timerJob = null
        raceEngine.reset()
        _racePath.value = emptyList()
        _uiState.update { state ->
            // Preserve GPS-related fields so the map stays live
            state.copy(
                status = RaceStatus.Idle,
                distanceMeters = 0f,
                currentSpeedMps = 0f,
                elapsedMs = 0L,
                startLocation = null,
                ignoredPoints = 0
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal GPS processing
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleLocationUpdate(location: Location) {
        _uiState.update { state ->
            state.copy(
                currentLocation = location,
                isGpsAvailable = true,
                gpsAccuracyMeters = location.accuracy
            )
        }

        if (_uiState.value.status !is RaceStatus.Running) return

        val accepted = raceEngine.processLocation(location)

        if (accepted) {
            _racePath.update { path ->
                path + LatLng(location.latitude, location.longitude)
            }
        }

        _uiState.update { state ->
            state.copy(
                currentSpeedMps = if (accepted) location.speed else state.currentSpeedMps,
                distanceMeters = raceEngine.totalDistanceMeters,
                ignoredPoints = raceEngine.ignoredPointCount
            )
        }

        if (raceEngine.isRaceComplete()) {
            val finalTime = raceEngine.getElapsedMs(System.currentTimeMillis())
            timerJob?.cancel()
            _uiState.update { state ->
                state.copy(
                    status = RaceStatus.Finished(finalTimeMs = finalTime),
                    elapsedMs = finalTime,
                    distanceMeters = raceEngine.totalDistanceMeters
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationJob?.cancel()
        timerJob?.cancel()
    }
}
