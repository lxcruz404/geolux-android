package me.luiscruzandresgomezrodolfohernandez.geolux.ui.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlinx.serialization.Serializable

/*
 * Rutas de la app con navigation-compose (rutas tipadas, @Serializable). Dos niveles:
 *
 * - Grafo principal: «Mis fincas» → «Explorar el mapa» o una finca abierta.
 * - Dentro de la finca (con la barra inferior): una ruta por pestaña y las
 *   pantallas de detalle que cuelgan de ellas (informe, detalle de potrero).
 *
 * Los argumentos viajan en la ruta (id de la finca, del potrero, del informe…),
 * así la pila de navegación los guarda y los recupera al girar el teléfono o al
 * volver a la app.
 */

/** «Mis fincas»: inicio de la app. */
@Serializable
data object MisFincasRoute

/** Mapa base sin abrir ninguna finca. */
@Serializable
data object ExplorarMapaRoute

/** Una finca abierta, con su barra inferior. */
@Serializable
data class FincaRoute(val projectId: String)

// ── Dentro de la finca ──────────────────────────────────────────────────────

@Serializable
data object MiFincaRoute

/** Un informe PDF de la finca (desde Mi finca). */
@Serializable
data class InformeRoute(val reportId: String)

/** [focusZoneId]: potrero que el mapa elige y encuadra al abrirse («Ver en el mapa»). */
@Serializable
data class MapaRoute(val focusZoneId: String? = null)

@Serializable
data object PotrerosRoute

@Serializable
data class PotreroDetalleRoute(val zoneId: String)

@Serializable
data object GanadoRoute

/** [zoneId] o [animalId]: actividad que se crea ya ligada a un potrero o a un animal. */
@Serializable
data class GestionRoute(val zoneId: String? = null, val animalId: String? = null)

@Serializable
data object Modelo3DRoute

/** Ruta con la que la barra inferior abre cada pestaña. */
fun FincaTab.route(): Any = when (this) {
    FincaTab.MiFinca -> MiFincaRoute
    FincaTab.Mapa -> MapaRoute()
    FincaTab.Potreros -> PotrerosRoute
    FincaTab.Ganado -> GanadoRoute
    FincaTab.Gestion -> GestionRoute()
    FincaTab.Modelo3D -> Modelo3DRoute
}

/**
 * Pestaña a la que pertenece un destino: los detalles marcan la pestaña de la que
 * cuelgan (un informe, Mi finca; el detalle de un potrero, Potreros).
 */
fun NavDestination?.fincaTab(): FincaTab? = when {
    this == null -> null
    hasRoute<MiFincaRoute>() || hasRoute<InformeRoute>() -> FincaTab.MiFinca
    hasRoute<MapaRoute>() -> FincaTab.Mapa
    hasRoute<PotrerosRoute>() || hasRoute<PotreroDetalleRoute>() -> FincaTab.Potreros
    hasRoute<GanadoRoute>() -> FincaTab.Ganado
    hasRoute<GestionRoute>() -> FincaTab.Gestion
    hasRoute<Modelo3DRoute>() -> FincaTab.Modelo3D
    else -> null
}
