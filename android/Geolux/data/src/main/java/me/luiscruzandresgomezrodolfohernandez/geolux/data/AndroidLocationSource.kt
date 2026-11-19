package me.luiscruzandresgomezrodolfohernandez.geolux.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Posición real con el [LocationManager] del sistema (AND-A7): funciona sin
 * internet ni Google Play Services, como se necesita en el campo.
 *
 * El permiso se comprueba al empezar a recoger el Flow: tras concederlo, la UI
 * debe volver a recogerlo. Si el usuario apaga o enciende la ubicación, el
 * estado cambia solo.
 */
class AndroidLocationSource(context: Context) : LocationSource {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission") // Comprobado con hasPermission() justo antes.
    override fun locations(): Flow<LocationState> = callbackFlow {
        if (!hasPermission()) {
            send(LocationState.NoPermission)
            awaitClose()
            return@callbackFlow
        }
        send(if (manager.isLocationEnabled) LocationState.Searching else LocationState.Disabled)
        bestLastKnown()?.let { send(it.toFix()) }

        // Los cuatro métodos explícitos: en Android 9–10 no tienen implementación por defecto.
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toFix())
            }

            override fun onProviderEnabled(provider: String) {
                trySend(LocationState.Searching)
            }

            override fun onProviderDisabled(provider: String) {
                if (!manager.isLocationEnabled) trySend(LocationState.Disabled)
            }

            @Deprecated("Solo para Android 9–10")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { it in manager.allProviders }
        providers.forEach { manager.requestLocationUpdates(it, INTERVAL_MS, 0f, listener, Looper.getMainLooper()) }
        awaitClose { manager.removeUpdates(listener) }
    }.distinctUntilChanged()

    private fun hasPermission() = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { appContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    /** Última posición del sistema, solo si es reciente: una de hace horas no es «dónde estás». */
    @SuppressLint("MissingPermission")
    private fun bestLastKnown(): Location? = manager.allProviders
        .mapNotNull { manager.getLastKnownLocation(it) }
        .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < MAX_LAST_KNOWN_AGE_NS }
        .minByOrNull { it.accuracy }

    private fun Location.toFix() = LocationState.Fix(lat = latitude, lon = longitude, accuracyM = accuracy)

    private companion object {
        const val INTERVAL_MS = 2_000L
        const val MAX_LAST_KNOWN_AGE_NS = 2 * 60 * 1_000_000_000L
    }
}
