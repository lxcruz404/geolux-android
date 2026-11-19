package me.luiscruzandresgomezrodolfohernandez.geolux.core.geolux

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Issue
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.MapElement
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.MapLine
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Measure
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Mission
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.MissionPath
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.OriginPoint
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Position
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Product
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Project
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Ring
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.WorkArea
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.Zone

/** Resultado de leer un paquete. [ok] = se puede importar; [data] solo viene cuando es válido. */
data class PackageReport(
    val errors: List<Issue>,
    val warnings: List<Issue>,
    val data: ProjectData?,
) {
    val ok: Boolean get() = errors.isEmpty()
}

/**
 * Lector y validador del contrato `.geolux` v1 (SPEC §2–7). Es el port en
 * Kotlin de `src/geolux/core/exchange/package.py`: mismas comprobaciones, en el
 * mismo orden y con los mismos códigos, para que los dos lectores den la misma
 * respuesta a los fixtures compartidos.
 *
 * Diferencia deliberada: además de lo que comprueba Python, una propiedad
 * obligatoria ausente (SPEC §5) rechaza el paquete con `invalid_geojson`, porque
 * aquí el resultado son modelos tipados y no diccionarios.
 */
object GeoluxReader {
    const val FORMAT = "geolux-package"
    const val READER_VERSION = 1

    private const val MANIFEST = "manifest.json"
    private const val PROJECT = "project.json"

    /** dataset → geometría, en el orden de Python (`LAYERS`). */
    private val LAYERS = linkedMapOf(
        "work_area" to "Polygon",
        "zones" to "Polygon",
        "elements" to "Point",
        "lines" to "LineString",
        "missions" to "Polygon",
        "mission_paths" to "LineString",
    )

    private fun layerPath(dataset: String) = "layers/$dataset.geojson"

    /**
     * Valida el paquete completo. Nunca lanza por un paquete malo: todo va al informe.
     * [verifyIntegrity] = false omite tamaños y SHA-256: solo para paquetes ya
     * validados al importarlos, que se vuelven a abrir desde el almacén del teléfono.
     * [onVerified] recibe los bytes ya comprobados y el total declarado: comprobar un
     * paquete de 1 GB es leerlo entero y la pantalla lo muestra como avance.
     */
    fun read(
        file: File,
        verifyIntegrity: Boolean = true,
        onVerified: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): PackageReport {
        val warnings = mutableListOf<Issue>()
        return try {
            PackageReport(emptyList(), warnings, readValid(file, warnings, verifyIntegrity, onVerified))
        } catch (reject: Reject) {
            PackageReport(listOf(reject.issue), warnings, null)
        }
    }

    /** Lo que el manifiesto declara, leído antes de copiar el paquete (ver [peekManifest]). */
    data class ManifestPeek(
        val packageId: String,
        val projectId: String,
        /** Suma de `size_bytes` de todos los archivos declarados. */
        val declaredBytes: Long,
        /** Suma de `size_bytes` de lo que hay bajo `products/`: lo que se extrae al instalar. */
        val productBytes: Long,
    )

    /**
     * Lee solo `manifest.json` de [input], por *streaming*, sin copiar el paquete:
     * Desktop lo escribe como primera entrada del ZIP (propuesta de contrato §2.3).
     * Sirve para saber cuánto espacio hará falta antes de empezar. Null si no es la
     * primera entrada o le faltan datos; no valida nada: eso lo hace [read].
     */
    fun peekManifest(input: InputStream): ManifestPeek? {
        try {
            ZipInputStream(input).use { zip ->
                if (zip.nextEntry?.name != MANIFEST) return null
                val manifest = Json.parseToJsonElement(zip.readBytes().decodeToString()) as? JsonObject ?: return null
                val project = manifest["project"] as? JsonObject ?: return null
                val sizes = (manifest["contents"] as? JsonArray ?: return null).map { entry ->
                    val obj = entry as? JsonObject ?: return null
                    (obj.str("path") ?: return null) to (obj.long("size_bytes") ?: return null)
                }
                return ManifestPeek(
                    packageId = manifest.str("package_id") ?: return null,
                    projectId = project.str("id") ?: return null,
                    declaredBytes = sizes.sumOf { it.second },
                    productBytes = sizes.filter { it.first.startsWith("products/") }.sumOf { it.second },
                )
            }
        } catch (e: Exception) { // ZIP o JSON ilegible: lo dirá read() con su código.
            return null
        }
    }

    private class Reject(val issue: Issue) : Exception(issue.message)

    private fun reject(code: String, message: String, path: String = ""): Nothing =
        throw Reject(Issue(code, message, path))

    private fun safe(name: String): Boolean {
        if (name.isEmpty() || name.startsWith("/") || '\\' in name || ':' in name) return false
        return name.split('/').all { it.isNotEmpty() && it != "." && it != ".." }
    }

    private fun readValid(
        file: File,
        warnings: MutableList<Issue>,
        verifyIntegrity: Boolean,
        onVerified: (done: Long, total: Long) -> Unit,
    ): ProjectData {
        val zip = try {
            ZipFile(file)
        } catch (e: ZipException) {
            // ponytail: Android 14+ (targetSdk ≥ 34) ya rechaza al abrir un ZIP con rutas «..»;
            // se reconoce por el mensaje. Si Android cambia el texto, saldría not_a_package.
            if (e.message?.startsWith("Invalid zip entry path") == true) {
                reject("unsafe_path", "El paquete contiene rutas no permitidas.")
            }
            reject("not_a_package", "El archivo no es un paquete GeoLux (no es un ZIP válido).")
        } catch (e: IOException) {
            reject("not_a_package", "El archivo no es un paquete GeoLux (no es un ZIP válido).")
        }
        zip.use { archive ->
            val names = archive.entries().asSequence().map { it.name }.toList()
            val lowered = HashSet<String>()
            for (name in names) {
                if (!safe(name) || !lowered.add(name.lowercase())) {
                    reject("unsafe_path", "El paquete contiene rutas no permitidas.", name)
                }
            }
            fun bytes(name: String): ByteArray = archive.getInputStream(archive.getEntry(name)).use { it.readBytes() }
            fun json(name: String, code: String): JsonElement = try {
                Json.parseToJsonElement(bytes(name).toString(Charsets.UTF_8))
            } catch (e: Exception) {
                val message = if (name == MANIFEST) "manifest.json no es JSON válido." else "$name no es un JSON válido."
                reject(code, message, name)
            }

            if (MANIFEST !in names) reject("manifest_missing", "Falta manifest.json en el paquete.")
            val manifest = json(MANIFEST, "manifest_invalid") as? JsonObject
            if (manifest == null || manifest.str("format") != FORMAT) {
                reject("wrong_format", "El archivo no es un paquete GeoLux.")
            }
            val version = manifest.int("format_version")
            val minimum = if ("min_reader_version" in manifest) manifest.int("min_reader_version") else version
            if (version == null || minimum == null) reject("manifest_invalid", "manifest.json no es válido.")
            if (minimum > READER_VERSION) {
                reject(
                    "too_new",
                    "El paquete se creó con una versión más nueva de GeoLux (formato $version); " +
                        "actualiza la aplicación para abrirlo.",
                )
            }
            val contents = manifest["contents"] as? JsonArray
            val manifestProject = manifest["project"] as? JsonObject
            if (contents == null || manifestProject == null) reject("manifest_invalid", "manifest.json no es válido.")

            val declared = LinkedHashMap<String, JsonObject>()
            for (entry in contents) {
                val entryPath = (entry as? JsonObject)?.str("path")
                if (entryPath == null || !safe(entryPath)) {
                    reject("unsafe_path", "El paquete contiene rutas no permitidas.", entryPath.toString())
                }
                declared[entryPath] = entry
            }
            val required = (listOf(PROJECT) + LAYERS.keys.map(::layerPath)).sorted()
            for (needed in required) {
                if (needed !in declared || needed !in names) {
                    reject("file_missing", "Falta el archivo obligatorio $needed.", needed)
                }
            }
            val toVerify = declared.filterKeys { it in names }.values.sumOf { it.long("size_bytes") ?: 0L }
            var verified = 0L
            for ((entryPath, entry) in declared) {
                if (entryPath !in names) {
                    if (entry.bool("required") == true) {
                        reject("file_missing", "Falta el archivo obligatorio $entryPath.", entryPath)
                    }
                    warnings += Issue("file_missing_optional", "Falta el archivo opcional $entryPath; se omite.", entryPath)
                    continue
                }
                if (!verifyIntegrity) continue
                // Por streaming: un ortomosaico real puede pesar cientos de MB.
                val (size, hash) = archive.getInputStream(archive.getEntry(entryPath)).use { input ->
                    sizeAndSha256(input) { read -> onVerified(verified + read, toVerify) }
                }
                verified += size
                if (entry.long("size_bytes") != size) {
                    reject("size_mismatch", "El archivo $entryPath está dañado o fue modificado.", entryPath)
                }
                if (entry.str("sha256") != hash) {
                    reject("hash_mismatch", "El archivo $entryPath está dañado o fue modificado.", entryPath)
                }
            }
            for (name in names) {
                if (name != MANIFEST && name !in declared) {
                    warnings += Issue("undeclared_file", "$name no está en el manifiesto; se ignora.", name)
                }
            }

            val project = json(PROJECT, "invalid_json") as? JsonObject
            if (project == null || project.str("id") != manifestProject.str("id")) {
                reject("invalid_json", "project.json no corresponde al proyecto del manifiesto.", PROJECT)
            }
            val layers = LAYERS.mapValues { (dataset, geometry) ->
                val name = layerPath(dataset)
                loadLayer(name, json(name, "invalid_json"), geometry)
            }
            val products = mutableListOf<Product>()
            for ((entryPath, entry) in declared) {
                if (entry.str("kind") == "layer" && entry.str("dataset") !in LAYERS) {
                    warnings += Issue("unknown_dataset", "Conjunto desconocido $entryPath; se ignora.", entryPath)
                }
                if (entry.str("kind") == "product_meta" && entryPath in names) {
                    val meta = json(entryPath, "invalid_json") as? JsonObject
                        ?: reject("invalid_json", "$entryPath no es un JSON válido.", entryPath)
                    val parsed = Fields(entryPath, meta).product()
                    // Propuesta de contrato §2.6: el papel de cada archivo viaja en el manifiesto.
                    val roles = parsed.files.mapNotNull { file -> declared[file]?.str("role")?.let { file to it } }.toMap()
                    val product = parsed.copy(fileRoles = roles)
                    products += product
                    if (product.isSynthetic) {
                        warnings += Issue(
                            "synthetic_product",
                            "El producto ${product.name} es sintético (solo para pruebas).",
                            entryPath,
                        )
                    }
                }
            }

            return ProjectData(
                packageId = manifest.str("package_id") ?: reject("manifest_invalid", "manifest.json no es válido."),
                packageCreatedAt = manifest.str("created_at") ?: reject("manifest_invalid", "manifest.json no es válido."),
                project = Fields(PROJECT, project).project(),
                workArea = layers.getValue("work_area").firstOrNull()?.let { (props, geometry) ->
                    props.workArea(geometry.polygon())
                },
                zones = layers.getValue("zones").map { (p, g) -> p.zone(g.polygon()) },
                elements = layers.getValue("elements").map { (p, g) -> p.element(g.position()) },
                lines = layers.getValue("lines").map { (p, g) -> p.line(g.path()) },
                missions = layers.getValue("missions").map { (p, g) -> p.mission(g.polygon()) },
                missionPaths = layers.getValue("mission_paths").map { (p, g) -> p.missionPath(g.path()) },
                products = products,
            )
        }
    }

    /**
     * Las capas editadas en el teléfono (`layers/<dataset>.geojson` de la carpeta de la
     * finca, mismo formato SPEC §5) sobre [base], la finca leída del paquete. Solo
     * cuentan las capas editables (área de trabajo, potreros, elementos y líneas);
     * una que no esté en [texts] se queda como en [base].
     * @throws IllegalArgumentException si alguna no es válida (nunca se muestra a medias).
     */
    fun readEditedLayers(texts: Map<String, String>, base: ProjectData): ProjectData = try {
        fun layer(dataset: String) = texts[dataset]?.let {
            loadLayer(layerPath(dataset), kotlinx.serialization.json.Json.parseToJsonElement(it), LAYERS.getValue(dataset))
        }
        base.copy(
            workArea = layer("work_area")?.let { l -> l.firstOrNull()?.let { (props, geometry) -> props.workArea(geometry.polygon()) } } ?: base.workArea.takeIf { "work_area" !in texts },
            zones = layer("zones")?.map { (p, g) -> p.zone(g.polygon()) } ?: base.zones,
            elements = layer("elements")?.map { (p, g) -> p.element(g.position()) } ?: base.elements,
            lines = layer("lines")?.map { (p, g) -> p.line(g.path()) } ?: base.lines,
        )
    } catch (e: Reject) {
        throw IllegalArgumentException(e.issue.message, e)
    } catch (e: kotlinx.serialization.SerializationException) {
        throw IllegalArgumentException("Una capa editada no es un GeoJSON válido.", e)
    }

    /** Valida un FeatureCollection (SPEC §5) y devuelve (propiedades con id, coordenadas) por feature. */
    private fun loadLayer(name: String, collection: JsonElement, geometryType: String): List<Pair<Fields, JsonElement>> {
        val features = ((collection as? JsonObject)?.takeIf { it.str("type") == "FeatureCollection" })
            ?.get("features") as? JsonArray
            ?: reject("invalid_geojson", "$name no es un GeoJSON válido (no es un FeatureCollection).", name)
        val seen = HashSet<String>()
        return features.map { element ->
            val feature = (element as? JsonObject)?.takeIf { it.str("type") == "Feature" }
                ?: reject("invalid_geojson", "$name no es un GeoJSON válido.", name)
            val geometry = feature["geometry"] as? JsonObject
            if (geometry?.str("type") != geometryType) {
                reject("invalid_geojson", "$name no es un GeoJSON válido (se esperaba $geometryType).", name)
            }
            val coordinates = geometry["coordinates"] ?: JsonNull
            checkCoordinates(name, geometryType, coordinates)
            val id = (feature["id"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
                ?: reject("invalid_geojson", "$name tiene un elemento sin id.", name)
            if (!seen.add(id)) reject("duplicate_id", "$name repite el identificador $id.", name)
            val properties = feature["properties"] as? JsonObject ?: JsonObject(emptyMap())
            Fields(name, JsonObject(properties + ("id" to JsonPrimitive(id)))) to coordinates
        }
    }

    private fun checkCoordinates(name: String, geometryType: String, coordinates: JsonElement) {
        fun position(value: JsonElement) {
            val pair = (value as? JsonArray)?.takeIf { it.size >= 2 }?.take(2)
                ?.map { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.doubleOrNull }
            if (pair == null || pair.any { it == null }) {
                reject("invalid_geojson", "$name no es un GeoJSON válido (coordenadas).", name)
            }
            val (lon, lat) = pair.map { it!! }
            if (!(lon.isFinite() && lat.isFinite() && lon in -180.0..180.0 && lat in -90.0..90.0)) {
                reject("coordinates_out_of_range", "$name tiene coordenadas fuera de rango.", name)
            }
        }
        when (geometryType) {
            "Point" -> position(coordinates)
            "LineString" -> {
                val points = (coordinates as? JsonArray)?.takeIf { it.size >= 2 }
                    ?: reject("invalid_geojson", "$name no es un GeoJSON válido (línea).", name)
                points.forEach(::position)
            }
            else -> {
                val rings = (coordinates as? JsonArray)?.takeIf { it.isNotEmpty() }
                    ?: reject("invalid_geojson", "$name no es un GeoJSON válido (polígono).", name)
                for (ring in rings) {
                    val points = (ring as? JsonArray)?.takeIf { it.size >= 4 && it.first() == it.last() }
                        ?: reject("invalid_geojson", "$name no es un GeoJSON válido (anillo sin cerrar).", name)
                    points.forEach(::position)
                }
            }
        }
    }

    private fun sizeAndSha256(input: InputStream, onRead: (Long) -> Unit = {}): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var size = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
            size += read
            onRead(size)
        }
        return size to digest.digest().joinToString("") { "%02x".format(it) }
    }

    // -- Lectura de campos ya validados ------------------------------------------------

    private fun JsonObject.prim(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }
    private fun JsonObject.str(key: String): String? = prim(key)?.takeIf { it.isString }?.content
    private fun JsonObject.int(key: String): Int? = prim(key)?.takeIf { !it.isString }?.intOrNull
    private fun JsonObject.long(key: String): Long? = prim(key)?.takeIf { !it.isString }?.content?.toLongOrNull()
    private fun JsonObject.bool(key: String): Boolean? = prim(key)?.booleanOrNull

    private fun JsonElement.position() = (this as JsonArray).let {
        Position(it[0].jsonPrimitive.doubleOrNull!!, it[1].jsonPrimitive.doubleOrNull!!)
    }
    private fun JsonElement.path(): List<Position> = (this as JsonArray).map { it.position() }
    private fun JsonElement.polygon(): List<Ring> = (this as JsonArray).map { it.path() }

    /** Propiedades de [file]; un campo obligatorio ausente rechaza el paquete. */
    private class Fields(val file: String, val obj: JsonObject) {
        private fun missing(key: String): Nothing =
            reject("invalid_geojson", "$file no es un GeoJSON válido (falta $key).", file)

        private fun p(key: String) = (obj[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }

        fun s(key: String): String = p(key)?.takeIf { it.isString }?.content ?: missing(key)
        fun sOpt(key: String): String? = p(key)?.takeIf { it.isString }?.content
        fun d(key: String): Double = p(key)?.takeIf { !it.isString }?.doubleOrNull ?: missing(key)
        fun dOpt(key: String): Double? = p(key)?.takeIf { !it.isString }?.doubleOrNull
        fun i(key: String): Int = d(key).toInt()
        fun obj(key: String): JsonObject? = obj[key] as? JsonObject

        fun project(): Project {
            val center = obj("center")?.let { Fields(file, it) }
            val area = obj("area")?.let { Fields(file, it) }
            return Project(
                id = s("id"),
                name = s("name"),
                description = sOpt("description") ?: "",
                location = sOpt("location") ?: "",
                farmId = sOpt("farm_id"),
                center = center?.let { Position(it.d("lon"), it.d("lat")) },
                area = area?.let { Measure(it.d("value"), it.s("unit")) },
                createdAt = s("created_at"),
                updatedAt = s("updated_at"),
            )
        }

        fun workArea(polygon: List<Ring>) = WorkArea(s("id"), polygon, d("area_m2"), d("perimeter_m"))

        fun zone(polygon: List<Ring>) = Zone(
            id = s("id"),
            name = s("name"),
            description = sOpt("description") ?: "",
            status = s("status"),
            statusLabel = s("status_label"),
            color = s("color"),
            polygon = polygon,
            areaM2 = d("area_m2"),
            perimeterM = d("perimeter_m"),
            livestockCapacity = dOpt("livestock_capacity"),
        )

        fun element(position: Position) = MapElement(
            id = s("id"),
            elementType = s("element_type"),
            typeLabel = s("type_label"),
            name = s("name"),
            description = sOpt("description") ?: "",
            position = position,
        )

        fun line(path: List<Position>) = MapLine(
            id = s("id"),
            category = s("category"),
            categoryLabel = s("category_label"),
            name = s("name"),
            description = sOpt("description") ?: "",
            lengthM = d("length_m"),
            path = path,
        )

        fun mission(area: List<Ring>) = Mission(
            id = s("id"),
            name = s("name"),
            planStatus = s("plan_status"),
            planStatusLabel = s("plan_status_label"),
            altitudeM = d("altitude_m"),
            gsdCmPx = d("gsd_cm_px"),
            speedMps = d("speed_mps"),
            frontOverlapPercent = d("front_overlap_percent"),
            sideOverlapPercent = d("side_overlap_percent"),
            photoCount = i("photo_count"),
            waypointCount = i("waypoint_count"),
            totalDistanceM = d("total_distance_m"),
            estimatedTimeS = d("estimated_time_s"),
            batteryVerdict = sOpt("battery_verdict"),
            plannedAt = s("planned_at"),
            area = area,
        )

        fun missionPath(path: List<Position>) = MissionPath(
            id = s("id"),
            missionId = s("mission_id"),
            altitudeM = d("altitude_m"),
            waypointCount = i("waypoint_count"),
            path = path,
        )

        fun product(): Product {
            val bounds = (obj["bounds"] as? JsonArray)?.map { (it as? JsonPrimitive)?.doubleOrNull ?: missing("bounds") }
                ?.takeIf { it.size == 4 } ?: missing("bounds")
            val files = (obj["files"] as? JsonArray)?.map { (it as? JsonPrimitive)?.content ?: missing("files") }
                ?: missing("files")
            val origin = obj("origin_point")?.let { Fields(file, it) }
            return Product(
                id = s("id"),
                kind = s("kind"),
                missionId = sOpt("mission_id"),
                name = s("name"),
                createdAt = s("created_at"),
                origin = s("origin"),
                crs = s("crs"),
                bounds = bounds,
                files = files,
                originPoint = origin?.let { OriginPoint(it.d("lat"), it.d("lon"), it.d("alt_m")) },
                variant = sOpt("variant"),
                sourceProductId = sOpt("source_product_id"),
                derivedFrom = (obj["derived_from"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty(),
                units = sOpt("units"),
            )
        }
    }
}
