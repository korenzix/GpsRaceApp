package com.gpsrace.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.gpsrace.app.race.RACE_DISTANCE_METERS
import com.gpsrace.app.race.RaceStatus
import com.gpsrace.app.utils.LocationUtils

// ─────────────────────────────────────────────────────────────────────────────
// Entry point — permission gate wraps the actual race screen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Root composable for the app's single screen.
 *
 * Handles the permission lifecycle:
 *   1. Show rationale / request dialog if not granted.
 *   2. Show the race UI once permission is granted.
 *
 * Uses Accompanist Permissions library for clean permission state management.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(viewModel: RaceViewModel = viewModel()) {

    val locationPermission = rememberPermissionState(
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    // Start GPS as soon as permission is confirmed
    LaunchedEffect(locationPermission.status) {
        if (locationPermission.status.isGranted) {
            viewModel.startLocationUpdates()
        }
    }

    when {
        locationPermission.status.isGranted -> {
            RaceScreen(viewModel = viewModel)
        }

        locationPermission.status.shouldShowRationale -> {
            // User previously denied — show a clear rationale
            PermissionRationaleScreen(onRequest = { locationPermission.launchPermissionRequest() })
        }

        else -> {
            // First time asking or permanently denied
            PermissionDeniedScreen(onRequest = { locationPermission.launchPermissionRequest() })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main race screen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The single race screen: Map on top, stats + controls below.
 *
 * Layout:
 *   ┌────────────────────────────────┐
 *   │                                │
 *   │         Google Map             │  ← weight(1f) — takes remaining space
 *   │                                │
 *   ├────────────────────────────────┤
 *   │  Distance | Speed | Timer      │  ← stats row
 *   ├────────────────────────────────┤
 *   │         [  START  ]            │  ← action button
 *   │         status text            │
 *   └────────────────────────────────┘
 */
@Composable
private fun RaceScreen(viewModel: RaceViewModel) {

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val racePath by viewModel.racePath.collectAsStateWithLifecycle()

    // Default camera — will auto-move once we get a GPS fix
    val defaultLatLng = LatLng(32.0853, 34.7818) // Tel Aviv as default
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 18f)
    }

    // Animate camera to user's position whenever it changes
    val currentLocation = state.currentLocation
    LaunchedEffect(currentLocation) {
        currentLocation?.let { loc ->
            val target = LatLng(loc.latitude, loc.longitude)
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(target, 18f),
                durationMs = 800
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Map ──────────────────────────────────────────────────────────────
        Box(modifier = Modifier
            .fillMaxWidth()
            .weight(1f)) {

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    myLocationButtonEnabled = false // We handle camera manually
                ),
                properties = MapProperties(isMyLocationEnabled = true)
            ) {
                // Draw the path taken during the race
                if (racePath.size >= 2) {
                    Polyline(
                        points = racePath,
                        color = Color(0xFF4CAF50),
                        width = 8f
                    )
                }

                // Mark where the race started
                state.startLocation?.let { start ->
                    Marker(
                        state = MarkerState(LatLng(start.latitude, start.longitude)),
                        title = "Start"
                    )
                }
            }

            // GPS accuracy badge — top-right corner of map
            if (state.isGpsAvailable) {
                val accuracyColor = when {
                    state.gpsAccuracyMeters <= 5f  -> Color(0xFF4CAF50)  // Green: great
                    state.gpsAccuracyMeters <= 15f -> Color(0xFFFFC107)  // Amber: ok
                    else                           -> Color(0xFFF44336)  // Red: poor
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "GPS ±%.0fm".format(state.gpsAccuracyMeters),
                        color = accuracyColor,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // ── Stats panel ───────────────────────────────────────────────────────
        StatsRow(state = state)

        // ── Progress bar ──────────────────────────────────────────────────────
        LinearProgressIndicator(
            progress = { (state.distanceMeters / RACE_DISTANCE_METERS).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surface
        )

        // ── Controls ──────────────────────────────────────────────────────────
        ControlsSection(
            state = state,
            onStart = { viewModel.startRace() },
            onReset = { viewModel.resetRace() }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stats row: distance / speed / timer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(state: com.gpsrace.app.race.RaceUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(
            label = "Distance",
            value = "%.1f / %.0fm".format(state.distanceMeters, RACE_DISTANCE_METERS)
        )
        VerticalDivider()
        StatItem(
            label = "Speed",
            value = LocationUtils.formatSpeedKmh(state.currentSpeedMps)
        )
        VerticalDivider()
        StatItem(
            label = "Time",
            value = LocationUtils.formatTime(state.elapsedMs),
            valueColor = if (state.status is RaceStatus.Running) Color(0xFF4CAF50) else Color.White
        )
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.Gray,
            letterSpacing = 0.5.sp
        )
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = valueColor
        )
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(Color.DarkGray)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Controls: START / RESET button + status message
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ControlsSection(
    state: com.gpsrace.app.race.RaceUiState,
    onStart: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (val status = state.status) {
            is RaceStatus.Idle -> {
                Button(
                    onClick = onStart,
                    enabled = state.isGpsAvailable && state.currentLocation != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (state.isGpsAvailable) "START RACE" else "Waiting for GPS…",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                if (!state.isGpsAvailable) {
                    Text(
                        text = "Make sure GPS is enabled on your device",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is RaceStatus.Running -> {
                // No START button during race — show live status instead
                Surface(
                    color = Color(0xFF1B5E20).copy(alpha = 0.8f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🏃 RACE IN PROGRESS",
                        modifier = Modifier.padding(vertical = 14.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF69F0AE),
                        letterSpacing = 1.sp
                    )
                }
                // Allow abandoning the race
                TextButton(onClick = onReset) {
                    Text("Cancel race", color = Color.Gray, fontSize = 12.sp)
                }
            }

            is RaceStatus.Finished -> {
                // ── WIN screen ───────────────────────────────────────────────
                Surface(
                    color = Color(0xFFFFF9C4),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (status.position == 1) "🏆 YOU WIN!" else "🎉 FINISHED!",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF333300)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Time: ${LocationUtils.formatTime(status.finalTimeMs)}",
                            fontSize = 22.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF555500)
                        )
                        Text(
                            text = "Distance: 100 m",
                            fontSize = 14.sp,
                            color = Color(0xFF888800)
                        )
                        // TODO: Show leaderboard position once server exists
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onReset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("RACE AGAIN", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Permission screens
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionRationaleScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📍", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Location Permission Required",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "GPS Race needs precise location to measure your distance and speed during the race. " +
                    "Your location is only used while the app is open.",
            textAlign = TextAlign.Center,
            color = Color.Gray,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
            Text("Grant Permission")
        }
    }
}

@Composable
private fun PermissionDeniedScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🚫", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "GPS Access Needed",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "This app cannot work without GPS. Please allow location access when prompted.",
            textAlign = TextAlign.Center,
            color = Color.Gray,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
            Text("Allow Location Access")
        }
    }
}
