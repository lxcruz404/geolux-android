package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

/**
 * Reglas de valores del contrato sobre datos que llegan de otro equipo (las mismas que
 * `_check` y `_check_natural_key` de Desktop, `src/geolux/core/management/exchange.py`).
 */
object ManagementValidation {
    private val attachmentPath = Regex("^adjuntos/([0-9a-f]{2})/([0-9a-f]{64})(\\.[a-z0-9]{1,10})?$")

    /** Solo `adjuntos/<sha[:2]>/<sha><.ext>` de esa misma huella: nunca una ruta fuera de la finca. */
    fun safeAttachmentPath(path: String, sha256: String): Boolean {
        val m = attachmentPath.matchEntire(path) ?: return false
        return m.groupValues[2] == sha256 && m.groupValues[1] == sha256.take(2)
    }

    private fun canonical(value: String?, what: String, allowZero: Boolean = false) {
        if (value == null || Values.decimalText(value, what, allowZero = allowZero) != value) {
            throw ValidationException("$what: «$value» no es un decimal en texto canónico.")
        }
    }

    fun check(data: ManagementData) {
        for (t in data.transactions) {
            val allowed = Catalogs.transactionType[t.type]?.second
            if (allowed == null || t.direction !in allowed) throw ValidationException("Movimiento ${t.id}: tipo o sentido no válido.")
            canonical(t.amount, "Valor")
        }
        for (a in data.activities) {
            if (a.status !in Catalogs.activityStatus || a.priority !in Catalogs.activityPriority) {
                throw ValidationException("Actividad ${a.id}: estado o prioridad no válidos.")
            }
        }
        for (i in data.purchaseItems) {
            canonical(i.quantity, "Cantidad")
            i.estimatedPrice?.let { canonical(it, "Precio", allowZero = true) }
            i.actualPrice?.let { canonical(it, "Precio", allowZero = true) }
            if (i.status !in Catalogs.purchaseItemStatus || i.unit !in Catalogs.unit) throw ValidationException("Artículo ${i.id}: estado o unidad no válidos.")
        }
        for (l in data.purchaseLists) {
            if (l.listType !in Catalogs.purchaseListType || l.status !in Catalogs.purchaseListStatus) {
                throw ValidationException("Lista ${l.id}: tipo o estado no válidos.")
            }
        }
        for (w in data.workers) if (w.status !in Catalogs.workerStatus) throw ValidationException("Trabajador ${w.id}: estado no válido.")
        for (l in data.activityLinks) if (l.entity !in Catalogs.linkEntity) throw ValidationException("Enlace ${l.id}: tipo «${l.entity}» no válido.")
        for (a in data.attachments) {
            if (!safeAttachmentPath(a.path, a.sha256)) throw ValidationException("Adjunto ${a.id}: ruta de adjunto no permitida (${a.path}).")
        }
    }

    /**
     * Tras fusionar: el mismo trabajador (código), día de una repetición o categoría no puede
     * quedar dos veces con ids distintos. Se mira el resultado, así borrar un trabajador y crear
     * otro con su código en el mismo paquete es válido (igual que Desktop).
     */
    fun checkNaturalKeys(merged: ManagementData) {
        fun clash(what: String): Nothing =
            throw ValidationException("«$what» ya existe aquí con otro registro (creado en otro equipo). Quite o renombre uno de los dos y vuelva a intentarlo.")
        merged.workers.filter { it.deletedAt == null }.groupBy { it.codeKey }.values.firstOrNull { it.size > 1 }?.let { clash(it.first().code) }
        merged.activities.filter { it.seriesId != null && it.occurrenceDate != null }.groupBy { it.seriesId to it.occurrenceDate }.values
            .firstOrNull { it.size > 1 }?.let { clash("${it.first().title} ${it.first().occurrenceDate}") }
        merged.financialCategories.groupBy { it.key }.values.firstOrNull { it.size > 1 }?.let { clash(it.first().label) }
    }
}
