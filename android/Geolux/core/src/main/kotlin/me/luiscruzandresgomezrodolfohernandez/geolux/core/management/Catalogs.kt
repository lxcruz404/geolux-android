package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

/**
 * Catálogos de la gestión de la finca (contrato `geolux.management/1`).
 *
 * La fuente única es `contracts/geolux-management-v1/catalogs.json`; estas constantes
 * deben ser exactamente ese archivo (lo comprueba `CatalogsContractTest`, y Desktop
 * comprueba lo mismo en `tests/core/test_farm_management.py`). Las claves no cambian;
 * las etiquetas pueden mejorar.
 */
object Catalogs {
    val activityStatus = linkedMapOf(
        "pending" to "Pendiente", "in_progress" to "En proceso", "done" to "Completada", "cancelled" to "Cancelada",
    )
    val openActivityStatus = setOf("pending", "in_progress")

    val activityPriority = linkedMapOf("low" to "Baja", "medium" to "Media", "high" to "Alta", "urgent" to "Urgente")

    val activityCategory = linkedMapOf(
        "fence_repair" to "Reparar cercas",
        "wire_purchase" to "Comprar alambre",
        "grass_cutting" to "Cortar pasto",
        "trough_cleaning" to "Limpiar bebederos",
        "tank_check" to "Revisar tanques",
        "pipe_repair" to "Reparar tuberías",
        "corral_maintenance" to "Mantenimiento de corrales",
        "vaccination" to "Vacunación",
        "treatment" to "Tratamiento veterinario",
        "weighing" to "Pesaje",
        "livestock_move" to "Traslado de ganado",
        "agriculture" to "Actividad agrícola",
        "purchase" to "Compra",
        "payment" to "Pago",
        "household" to "Actividad doméstica",
        "other" to "Otra",
    )

    val recurrenceFrequency = linkedMapOf("daily" to "Diaria", "weekly" to "Semanal", "monthly" to "Mensual")

    val linkEntity = linkedMapOf(
        "animal" to "Animal",
        "livestock_lot" to "Lote",
        "zone" to "Potrero",
        "map_line" to "Cerca o línea",
        "map_element" to "Elemento (tanque, bebedero…)",
        "work_area" to "Área de trabajo",
        "purchase_list" to "Lista de compra",
        "purchase_item" to "Artículo de compra",
        "worker" to "Trabajador",
    )

    val purchaseListType = linkedMapOf(
        "workers_groceries" to "Mercado de trabajadores",
        "livestock_supplies" to "Insumos ganaderos",
        "medicines" to "Medicamentos",
        "construction" to "Materiales de construcción",
        "hardware" to "Ferretería",
        "agricultural_supplies" to "Insumos agrícolas",
        "tools" to "Herramientas",
        "general" to "Compras generales",
    )

    val purchaseListStatus = linkedMapOf("open" to "Abierta", "done" to "Completada", "cancelled" to "Cancelada")

    val purchaseItemStatus = linkedMapOf(
        "pending" to "Pendiente", "bought" to "Comprado", "partial" to "Parcialmente comprado", "cancelled" to "Cancelado",
    )

    val unit = linkedMapOf(
        "unit" to "unidad", "kg" to "kg", "g" to "g", "lb" to "lb", "l" to "L", "ml" to "mL", "m" to "m",
        "box" to "caja", "roll" to "rollo", "bag" to "bulto", "dose" to "dosis",
    )

    val workerStatus = linkedMapOf("active" to "Activo", "inactive" to "Inactivo")

    /** Tipo → (etiqueta, sentidos permitidos). `none` no cuenta en el balance. */
    val transactionType = linkedMapOf(
        "income" to ("Ingreso" to listOf("in")),
        "expense" to ("Gasto" to listOf("out")),
        "purchase" to ("Compra" to listOf("out")),
        "worker_payment" to ("Pago a trabajador" to listOf("out")),
        "worker_advance" to ("Anticipo a trabajador" to listOf("out")),
        "transfer" to ("Transferencia" to listOf("none")),
        "other" to ("Otro movimiento" to listOf("in", "out")),
    )
    val workerTransactionTypes = setOf("worker_payment", "worker_advance")

    val paymentMethod = linkedMapOf(
        "cash" to "Efectivo", "transfer" to "Transferencia", "card" to "Tarjeta", "credit" to "Crédito", "other" to "Otro",
    )

    val financialCategory = linkedMapOf(
        "workers_food" to "Alimentación de trabajadores",
        "livestock" to "Ganadería",
        "veterinary" to "Veterinaria",
        "maintenance" to "Mantenimiento",
        "fences" to "Cercas",
        "infrastructure" to "Infraestructura",
        "agriculture" to "Agricultura",
        "transport" to "Transporte",
        "labor" to "Mano de obra",
        "services" to "Servicios",
        "general_purchases" to "Compras generales",
        "other" to "Otros",
    )

    const val CUSTOM_PREFIX = "custom:"

    /** Una clave del catálogo o, si [custom], `custom:<texto>`. */
    fun isValidKey(key: String, catalog: Map<String, *>, custom: Boolean = false): Boolean =
        key in catalog || (custom && key.startsWith(CUSTOM_PREFIX) && key.length > CUSTOM_PREFIX.length)
}
