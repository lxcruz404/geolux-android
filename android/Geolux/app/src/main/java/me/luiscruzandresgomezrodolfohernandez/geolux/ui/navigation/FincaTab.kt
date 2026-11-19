package me.luiscruzandresgomezrodolfohernandez.geolux.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import me.luiscruzandresgomezrodolfohernandez.geolux.R

/**
 * Pestañas de la barra inferior dentro de una finca (DESIGN.md §8).
 * Las misiones no tienen pestaña propia (06_ANDROID_SPEC §8b).
 */
enum class FincaTab(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
) {
    MiFinca(
        label = R.string.tab_mi_finca,
        icon = R.drawable.ic_symbol_landscape,
        selectedIcon = R.drawable.ic_symbol_landscape_filled,
    ),
    Mapa(
        label = R.string.tab_mapa,
        icon = R.drawable.ic_symbol_map,
        selectedIcon = R.drawable.ic_symbol_map_filled,
    ),
    Potreros(
        label = R.string.tab_potreros,
        icon = R.drawable.ic_symbol_grid_view,
        selectedIcon = R.drawable.ic_symbol_grid_view_filled,
    ),
    /** Software ganadero: resumen, animales, lotes, registros e informes (separado de Gestión). */
    Ganado(
        label = R.string.tab_ganado,
        icon = R.drawable.ic_cattle,
        selectedIcon = R.drawable.ic_cattle_filled,
    ),
    Modelo3D(
        label = R.string.tab_3d,
        icon = R.drawable.ic_symbol_view_in_ar,
        selectedIcon = R.drawable.ic_symbol_view_in_ar_filled,
    ),
    /** Gestión administrativa: actividades, compras, trabajadores y dinero (contrato geolux.management/1). */
    Gestion(
        label = R.string.tab_gestion,
        icon = R.drawable.ic_check_circle,
        selectedIcon = R.drawable.ic_check_circle,
    ),
}
