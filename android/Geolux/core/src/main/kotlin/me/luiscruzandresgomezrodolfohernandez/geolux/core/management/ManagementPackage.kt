package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import java.io.File
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Lee la gestión (`management/<conjunto>.json`, kind `management`) de un paquete `.glx` que
 * `GeoluxReader` ya validó (tamaños y SHA-256). Un paquete sin gestión da datos vacíos.
 */
object ManagementPackage {
    /** [validate] = false solo para pasar a Room datos ya guardados (se conservan tal cual). */
    fun read(file: File, projectId: String, validate: Boolean = true): ManagementData = ZipFile(file).use { zip ->
        val manifest = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("manifest.json")).readBytes().decodeToString()) as JsonObject
        val wanted = ManagementCodec.DATASETS.associateBy { ManagementCodec.pathOf(it) }
        val texts = mutableMapOf<String, String>()
        for (entry in (manifest["contents"] as? JsonArray).orEmpty()) {
            val o = entry as? JsonObject ?: continue
            val path = (o["path"] as? JsonPrimitive)?.content ?: continue
            val kind = (o["kind"] as? JsonPrimitive)?.content
            val dataset = wanted[path] ?: continue
            if (kind != ManagementCodec.KIND) continue
            texts[dataset] = zip.getInputStream(zip.getEntry(path)).readBytes().decodeToString()
        }
        ManagementCodec.decode(projectId, texts).also { if (validate) ManagementValidation.check(it) }
    }
}
