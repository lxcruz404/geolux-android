package me.luiscruzandresgomezrodolfohernandez.geolux.core.edit

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementCodec
import me.luiscruzandresgomezrodolfohernandez.geolux.core.management.ManagementData
import me.luiscruzandresgomezrodolfohernandez.geolux.core.model.ProjectData

/**
 * Exporta desde el teléfono una finca editada como paquete `.glx`/`.geolux` v1, el
 * mismo contrato que escribe Desktop (sin sincronización: es un archivo).
 *
 * Parte del paquete importado y **no reinventa nada**: copia cada entrada tal cual
 * (productos, fotos, `project.json`, misiones…) salvo las capas editables, que van
 * con lo editado ([LayersJson]). El manifiesto conserva todo lo de Desktop y solo
 * cambia lo que debe: `package_id` nuevo (es otro paquete), `created_at`, el
 * `generator` (GeoLux Android) y, de cada capa, `size_bytes`, `sha256` y
 * `feature_count`. El manifiesto va primero en el ZIP, como lo escribe Desktop.
 */
object PackageWriter {
    private const val MANIFEST = "manifest.json"
    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    /**
     * Escribe en [out] el paquete de [data] a partir del importado [original].
     * [appVersion] identifica a GeoLux Android en el manifiesto. Devuelve el `package_id` nuevo.
     */
    fun write(
        original: File,
        data: ProjectData,
        out: OutputStream,
        appVersion: String,
        now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
        packageId: String = UUID.randomUUID().toString(),
        management: ManagementData? = null,
    ): String {
        val layers = LayersJson.write(data).mapKeys { (dataset, _) -> "layers/$dataset.geojson" }
            .mapValues { (_, text) -> text.toByteArray(Charsets.UTF_8) }
        // La gestión editada en el teléfono (contrato geolux.management/1): sustituye la que traía
        // el paquete; un conjunto que quedó vacío deja de viajar, como en Desktop.
        val managed: Map<String, ByteArray> = management?.let { m ->
            ManagementCodec.encode(m).mapKeys { ManagementCodec.pathOf(it.key) }.mapValues { it.value.toByteArray(Charsets.UTF_8) }
        }.orEmpty()
        val datasetPaths = ManagementCodec.DATASETS.map { ManagementCodec.pathOf(it) }.toSet()
        fun isManagement(path: String?) = management != null && path in datasetPaths
        val counts = mapOf(
            "layers/work_area.geojson" to (if (data.workArea != null) 1 else 0),
            "layers/zones.geojson" to data.zones.size,
            "layers/elements.geojson" to data.elements.size,
            "layers/lines.geojson" to data.lines.size,
        )
        ZipFile(original).use { zip ->
            val manifestEntry = zip.getEntry(MANIFEST) ?: throw IOException("El paquete importado no tiene manifiesto.")
            val manifest = Json.parseToJsonElement(zip.getInputStream(manifestEntry).readBytes().toString(Charsets.UTF_8)).jsonObject
            val original = (manifest["contents"] as? JsonArray ?: throw IOException("El manifiesto no lista su contenido."))
                .map { it.jsonObject }
            val present = original.mapNotNull { it["path"]?.jsonPrimitive?.content }.toSet()
            val added = managed.filterKeys { it !in present }.map { (path, bytes) ->
                JsonObject(mapOf(
                    "path" to JsonPrimitive(path),
                    "kind" to JsonPrimitive(ManagementCodec.KIND),
                    "schema" to JsonPrimitive(ManagementCodec.SCHEMA),
                    "dataset" to JsonPrimitive(path.removePrefix("management/").removeSuffix(".json")),
                    "required" to JsonPrimitive(false),
                    "media_type" to JsonPrimitive("application/json"),
                    "compression" to JsonPrimitive("deflate"),
                    "size_bytes" to JsonPrimitive(bytes.size),
                    "sha256" to JsonPrimitive(sha256(bytes)),
                ))
            }
            val contents = original
                .filter { entry -> !isManagement(entry["path"]?.jsonPrimitive?.content) || entry["path"]?.jsonPrimitive?.content in managed }
                .map { entry ->
                    val path = entry["path"]?.jsonPrimitive?.content
                    val bytes = layers[path] ?: managed[path] ?: return@map entry
                    JsonObject(
                        entry + mapOf(
                            "size_bytes" to JsonPrimitive(bytes.size),
                            "sha256" to JsonPrimitive(sha256(bytes)),
                        ) + (if ("feature_count" in entry) mapOf("feature_count" to JsonPrimitive(counts.getValue(path!!))) else emptyMap()),
                    )
                } + added
            val generator = (manifest["generator"] as? JsonObject).orEmpty() + mapOf(
                "app" to JsonPrimitive("GeoLux Android"),
                "app_version" to JsonPrimitive(appVersion),
            )
            val totals = (manifest["totals"] as? JsonObject)?.let { t ->
                val sizes = contents.map { it["size_bytes"]?.jsonPrimitive?.longOrNull ?: 0L }
                JsonObject(t + mapOf(
                    "bytes" to JsonPrimitive(sizes.sum()),
                    "files" to JsonPrimitive(sizes.size),
                    "largest_file_bytes" to JsonPrimitive(sizes.maxOrNull() ?: 0L),
                ))
            }
            val newManifest = JsonObject(
                manifest + mapOf<String, JsonElement>(
                    "package_id" to JsonPrimitive(packageId),
                    "created_at" to JsonPrimitive(now.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
                    "generator" to JsonObject(generator),
                    "contents" to JsonArray(contents),
                ) + (totals?.let { mapOf("totals" to it) } ?: emptyMap()),
            )
            ZipOutputStream(out.buffered(1 shl 20)).use { zos ->
                zos.putNextEntry(ZipEntry(MANIFEST))
                zos.write(json.encodeToString(JsonElement.serializer(), newManifest).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                for (entry in zip.entries()) {
                    if (entry.name == MANIFEST) continue
                    if (isManagement(entry.name) && entry.name !in managed) continue
                    val replaced = layers[entry.name] ?: managed[entry.name]
                    if (replaced != null) {
                        zos.putNextEntry(ZipEntry(entry.name))
                        zos.write(replaced)
                    } else {
                        // Igual que estaba: mismo método (STORED o DEFLATED), mismos bytes.
                        val copy = ZipEntry(entry.name).apply {
                            method = entry.method
                            if (entry.method == ZipEntry.STORED) {
                                size = entry.size
                                compressedSize = entry.size
                                crc = entry.crc
                            }
                        }
                        zos.putNextEntry(copy)
                        zip.getInputStream(entry).use { it.copyTo(zos, 1 shl 16) }
                    }
                    zos.closeEntry()
                }
                for (entry in added) {
                    val path = entry.getValue("path").jsonPrimitive.content
                    zos.putNextEntry(ZipEntry(path))
                    zos.write(managed.getValue(path))
                    zos.closeEntry()
                }
            }
        }
        return packageId
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
}
