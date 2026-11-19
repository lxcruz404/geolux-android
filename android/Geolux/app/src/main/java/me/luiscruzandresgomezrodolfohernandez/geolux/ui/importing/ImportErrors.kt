package me.luiscruzandresgomezrodolfohernandez.geolux.ui.importing

/**
 * Agrupa los códigos de SPEC §7 (y los propios del teléfono) en los casos que
 * ve el usuario. La UI nunca muestra el código ni rutas internas del paquete
 * (DESIGN.md §10): dice qué pasó y qué hacer.
 */
enum class ImportErrorKind {
    /** No es un `.geolux`. */
    NotAPackage,

    /** Es un `.geolux` pero está incompleto, alterado o mal formado. */
    Damaged,

    /** Lo creó una versión más nueva de GeoLux: hay que actualizar la app. */
    TooNew,

    /** El teléfono no pudo leer el archivo elegido. */
    ReadFailed,

    /** El paquete era válido pero no se pudo guardar; la finca anterior sigue igual. */
    InstallFailed,

    /** El paquete no cabe en el teléfono. */
    NoSpace,

    Other,
}

fun importErrorKind(code: String): ImportErrorKind = when (code) {
    "not_a_package", "wrong_format", "manifest_missing" -> ImportErrorKind.NotAPackage
    "manifest_invalid", "unsafe_path", "file_missing", "hash_mismatch", "size_mismatch",
    "invalid_json", "invalid_geojson", "coordinates_out_of_range", "duplicate_id",
    -> ImportErrorKind.Damaged
    "too_new" -> ImportErrorKind.TooNew
    "read_failed" -> ImportErrorKind.ReadFailed
    "install_failed" -> ImportErrorKind.InstallFailed
    "no_space" -> ImportErrorKind.NoSpace
    else -> ImportErrorKind.Other
}

/** Advertencias de SPEC §7 que se informan como «archivos que esta versión no usa». */
fun isIgnoredContentWarning(code: String): Boolean =
    code == "undeclared_file" || code == "unknown_dataset" || code == "file_missing_optional"
