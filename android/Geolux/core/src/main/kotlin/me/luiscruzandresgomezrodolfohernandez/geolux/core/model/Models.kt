package me.luiscruzandresgomezrodolfohernandez.geolux.core.model

// Modelos del contrato .geolux v1 (contracts/geolux-v1/SPEC.md §4–6).
// Los nombres siguen el contrato; las claves (`status`, `elementType`…) se
// guardan tal cual y las etiquetas `*Label` son el texto en español que
// muestra Desktop, para que la UI no traduzca nada.

/** Coordenada WGS84 en el orden de GeoJSON: longitud, latitud. */
data class Position(val lon: Double, val lat: Double)

/** Anillo de un polígono; el primero es el exterior. */
typealias Ring = List<Position>

data class Measure(val value: Double, val unit: String)

/** `project.json` (SPEC §4). */
data class Project(
    val id: String,
    val name: String,
    val description: String,
    val location: String,
    val farmId: String?,
    val center: Position?,
    val area: Measure?,
    val createdAt: String,
    val updatedAt: String,
)

/** `work_area.geojson`. */
data class WorkArea(
    val id: String,
    val polygon: List<Ring>,
    val areaM2: Double,
    val perimeterM: Double,
)

/** `zones.geojson`: un potrero. `status` es `active`, `resting` o `inactive`. */
data class Zone(
    val id: String,
    val name: String,
    val description: String,
    val status: String,
    val statusLabel: String,
    val color: String,
    val polygon: List<Ring>,
    val areaM2: Double,
    val perimeterM: Double,
    val livestockCapacity: Double?,
)

/** `elements.geojson`. */
data class MapElement(
    val id: String,
    val elementType: String,
    val typeLabel: String,
    val name: String,
    val description: String,
    val position: Position,
)

/** `lines.geojson`. */
data class MapLine(
    val id: String,
    val category: String,
    val categoryLabel: String,
    val name: String,
    val description: String,
    val lengthM: Double,
    val path: List<Position>,
)

/** `missions.geojson`: área de vuelo y datos del plan. */
data class Mission(
    val id: String,
    val name: String,
    val planStatus: String,
    val planStatusLabel: String,
    val altitudeM: Double,
    val gsdCmPx: Double,
    val speedMps: Double,
    val frontOverlapPercent: Double,
    val sideOverlapPercent: Double,
    val photoCount: Int,
    val waypointCount: Int,
    val totalDistanceM: Double,
    val estimatedTimeS: Double,
    val batteryVerdict: String?,
    val plannedAt: String,
    val area: List<Ring>,
)

/** `mission_paths.geojson`: ruta de waypoints en orden; `id` = id de la misión. */
data class MissionPath(
    val id: String,
    val missionId: String,
    val altitudeM: Double,
    val waypointCount: Int,
    val path: List<Position>,
)

data class OriginPoint(val lat: Double, val lon: Double, val altM: Double)

/**
 * `products/<id>/meta.json` (SPEC §6). `kind` es `orthomosaic` o `model3d`; los
 * paquetes `.glx` traen además `dsm`, `dtm`, `point_cloud` y `report`.
 * `bounds` es [oeste, sur, este, norte]. `originPoint` y `units` solo en 3D.
 * [fileRoles]: el `role` que el manifiesto da a cada archivo (`model_mobile`,
 * `model`…), vacío en los paquetes que no lo traen.
 */
data class Product(
    val id: String,
    val kind: String,
    val missionId: String?,
    val name: String,
    val createdAt: String,
    val origin: String,
    val crs: String,
    val bounds: List<Double>,
    val files: List<String>,
    val originPoint: OriginPoint? = null,
    val units: String? = null,
    val fileRoles: Map<String, String> = emptyMap(),
    /**
     * Ortomosaico completo (`full`) o recortado por Desktop (`cropped`); null en paquetes
     * anteriores (propuesta de contrato del 9 nov. 2026, campo opcional y aditivo).
     */
    val variant: String? = null,
    /** Del recortado: el id del ortomosaico original del que sale. */
    val sourceProductId: String? = null,
    /**
     * `derived_from` (contrato unificado del 11 nov. 2026): los productos del paquete de
     * los que sale este. En el recortado coincide con [sourceProductId].
     */
    val derivedFrom: List<String> = emptyList(),
) {
    /**
     * Ortomosaico recortado: `variant: cropped` o, en paquetes de la rama del 10 nov. 2026,
     * `kind: orthomosaic_crop`.
     */
    val isCropped: Boolean get() = variant == "cropped" || kind == KIND_ORTHOMOSAIC_CROP

    /** Ortomosaico, original o recortado (los dos se muestran como capas aparte). */
    val isOrthomosaic: Boolean get() = kind == KIND_ORTHOMOSAIC || kind == KIND_ORTHOMOSAIC_CROP

    /** Del recortado: el original del que sale (`source_product_id` o, si falta, `derived_from`). */
    val originalProductId: String? get() = if (isCropped) sourceProductId ?: derivedFrom.firstOrNull() else null

    /** Un producto sintético se muestra siempre marcado como tal (SPEC §6). */
    val isSynthetic: Boolean get() = origin == "synthetic"

    companion object {
        const val KIND_ORTHOMOSAIC = "orthomosaic"

        /** Solo se lee: así escribía el recortado la rama de Desktop del 10 nov. 2026. */
        const val KIND_ORTHOMOSAIC_CROP = "orthomosaic_crop"
    }
}

/** Todo lo que trae un paquete importado. */
data class ProjectData(
    val packageId: String,
    val packageCreatedAt: String,
    val project: Project,
    val workArea: WorkArea?,
    val zones: List<Zone>,
    val elements: List<MapElement>,
    val lines: List<MapLine>,
    val missions: List<Mission>,
    val missionPaths: List<MissionPath>,
    val products: List<Product>,
)

/** Lo necesario para la lista «Mis fincas». */
data class ProjectSummary(
    val id: String,
    val name: String,
    val location: String,
    val area: Measure?,
    val zoneCount: Int,
    val missionCount: Int,
    val packageCreatedAt: String,
)

/**
 * Área de la finca: la de `project.json` y, si falta (Desktop solo la guarda si
 * el usuario la escribe), la del área de trabajo dibujada, en m². La usan Mis
 * fincas y Mi finca, para que las dos digan lo mismo.
 */
fun ProjectData.totalArea(): Measure? = project.area ?: workArea?.let { Measure(it.areaM2, "m2") }

fun ProjectData.summary() = ProjectSummary(
    id = project.id,
    name = project.name,
    location = project.location,
    area = totalArea(),
    zoneCount = zones.size,
    missionCount = missions.size,
    packageCreatedAt = packageCreatedAt,
)

/** Error o advertencia de SPEC §7: `code` es la clave del contrato. */
data class Issue(val code: String, val message: String, val path: String = "")
