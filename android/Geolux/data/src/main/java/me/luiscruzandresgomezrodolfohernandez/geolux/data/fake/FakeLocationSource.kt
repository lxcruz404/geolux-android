package me.luiscruzandresgomezrodolfohernandez.geolux.data.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import me.luiscruzandresgomezrodolfohernandez.geolux.data.LocationSource
import me.luiscruzandresgomezrodolfohernandez.geolux.data.LocationState

/** Emite [states] en orden; por defecto, buscando y luego una posición dentro de finca-demo. */
class FakeLocationSource(
    private val states: List<LocationState> = listOf(
        LocationState.Searching,
        LocationState.Fix(lat = 6.301, lon = -70.1985, accuracyM = 5f),
    ),
) : LocationSource {
    override fun locations(): Flow<LocationState> = flowOf(*states.toTypedArray())
}
