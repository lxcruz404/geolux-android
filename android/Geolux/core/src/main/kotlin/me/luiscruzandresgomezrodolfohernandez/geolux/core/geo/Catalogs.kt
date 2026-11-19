package me.luiscruzandresgomezrodolfohernandez.geolux.core.geo

/**
 * Catálogos de GeoLux Desktop que hacen falta para **crear** datos en el
 * teléfono: las claves y las etiquetas en español que Desktop guarda en las
 * capas (`*_label`, SPEC §5). Copiados de `core/element_types.py`,
 * `core/line_categories.py` y `core/zone_statuses.py`, en el mismo orden en
 * que Desktop los ofrece. Una clave desconocida que venga en un paquete se
 * conserva tal cual: estos catálogos solo limitan lo que se crea aquí.
 */
data class CatalogEntry(val key: String, val label: String)

object Catalogs {
    val elementTypes: List<CatalogEntry> = listOf(
        "point_of_interest" to "Punto de interés",
        "tank" to "Tanque",
        "trough" to "Bebedero",
        "corral" to "Corral",
        "house" to "Casa",
        "warehouse" to "Bodega",
        "road" to "Camino / acceso",
        "infrastructure" to "Infraestructura",
        "stable" to "Establo",
        "shed" to "Galpón",
        "chute" to "Manga de manejo",
        "well" to "Pozo",
        "pond" to "Jagüey",
        "reservoir" to "Reservorio",
        "pump" to "Bomba",
        "mill" to "Molino de viento",
        "spring" to "Fuente / nacimiento",
        "transformer" to "Transformador",
        "pole" to "Poste",
        "gate" to "Puerta / portón",
        "dock" to "Embarcadero",
        "access_point" to "Punto de acceso",
        "other" to "Otro",
    ).map { (k, v) -> CatalogEntry(k, v) }

    val lineCategories: List<CatalogEntry> = listOf(
        "fence" to "Cerca",
        "boundary" to "Lindero",
        "electric_fence" to "Cerca eléctrica",
        "division" to "División",
        "main_road" to "Camino principal",
        "internal_road" to "Camino interno",
        "highway" to "Carretera",
        "canal" to "Canal",
        "river" to "Río / arroyo",
        "pipeline" to "Tubería",
        "path" to "Sendero",
        "other" to "Otra línea",
    ).map { (k, v) -> CatalogEntry(k, v) }

    val zoneStatuses: List<CatalogEntry> = listOf(
        "active" to "Activo",
        "resting" to "En descanso",
        "inactive" to "Inactivo",
    ).map { (k, v) -> CatalogEntry(k, v) }

    const val DEFAULT_ELEMENT_TYPE = "other"
    const val DEFAULT_LINE_CATEGORY = "fence"
    const val DEFAULT_ZONE_STATUS = "active"

    fun elementTypeLabel(key: String) = elementTypes.firstOrNull { it.key == key }?.label ?: key
    fun lineCategoryLabel(key: String) = lineCategories.firstOrNull { it.key == key }?.label ?: key
    fun zoneStatusLabel(key: String) = zoneStatuses.firstOrNull { it.key == key }?.label ?: key
}
