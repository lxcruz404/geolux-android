package me.luiscruzandresgomezrodolfohernandez.geolux.data

import android.Manifest
import kotlinx.coroutines.flow.Flow

/** Posición del teléfono para el mapa (06 §4). La UI pide permisos y pinta el estado. */
interface LocationSource {
    /** Permisos que la UI debe pedir antes de esperar una posición. */
    val requiredPermissions: List<String>
        get() = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun locations(): Flow<LocationState>
}

sealed interface LocationState {
    data object NoPermission : LocationState
    data object Disabled : LocationState
    data object Searching : LocationState
    data class Fix(val lat: Double, val lon: Double, val accuracyM: Float) : LocationState
}
