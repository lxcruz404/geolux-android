package me.luiscruzandresgomezrodolfohernandez.geolux.core.management

import java.io.File
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Contrato de gestión compartido con Desktop: los catálogos son exactamente
 * `catalogs.json` y las reglas dan exactamente los resultados de `vectors.json`
 * (Desktop: `tests/core/test_farm_management.py` y `tests/core/test_management_vectors.py`).
 */
class ManagementContractTest {
    private val dir = File(System.getProperty("geolux.managementContract")!!)
    private fun json(name: String): JsonObject = Json.parseToJsonElement(File(dir, name).readText()).jsonObject

    private fun pairs(contract: JsonObject, name: String) =
        contract.getValue(name).jsonArray.map { it.jsonObject }.map {
            it.getValue("key").jsonPrimitive.content to it.getValue("label").jsonPrimitive.content
        }

    @Test
    fun losCatalogosSonLosDelContrato() {
        val contract = json("catalogs.json")
        val simple = mapOf(
            "activity_status" to Catalogs.activityStatus,
            "activity_priority" to Catalogs.activityPriority,
            "activity_category" to Catalogs.activityCategory,
            "recurrence_frequency" to Catalogs.recurrenceFrequency,
            "link_entity" to Catalogs.linkEntity,
            "purchase_list_type" to Catalogs.purchaseListType,
            "purchase_list_status" to Catalogs.purchaseListStatus,
            "purchase_item_status" to Catalogs.purchaseItemStatus,
            "unit" to Catalogs.unit,
            "worker_status" to Catalogs.workerStatus,
            "payment_method" to Catalogs.paymentMethod,
            "financial_category" to Catalogs.financialCategory,
        )
        for ((name, constants) in simple) assertEquals(name, pairs(contract, name), constants.toList())
        val types = contract.getValue("transaction_type").jsonArray.map { it.jsonObject }.map { o ->
            o.getValue("key").jsonPrimitive.content to
                (o.getValue("label").jsonPrimitive.content to o.getValue("directions").jsonArray.map { it.jsonPrimitive.content })
        }
        assertEquals(types, Catalogs.transactionType.toList())
    }

    @Test
    fun vectoresDeDecimales() {
        for (case in json("vectors.json").getValue("decimal_text").jsonArray.map { it.jsonObject }) {
            val input = case.getValue("in").jsonPrimitive.content
            if (case["error"]?.jsonPrimitive?.booleanOrNull == true) {
                try {
                    Values.decimalText(input, "Valor")
                    fail("«$input» debía rechazarse")
                } catch (e: ValidationException) {
                    // esperado
                }
            } else {
                assertEquals(input, case.getValue("out").jsonPrimitive.content, Values.decimalText(input, "Valor"))
            }
        }
        for (case in json("vectors.json").getValue("code_key").jsonArray.map { it.jsonObject }) {
            assertEquals(case.getValue("out").jsonPrimitive.content, Values.codeKey(case.getValue("in").jsonPrimitive.content))
        }
    }

    @Test
    fun vectoresDeRepeticion() {
        for (case in json("vectors.json").getValue("recurrence").jsonArray.map { it.jsonObject }) {
            val r = case.getValue("rule").jsonObject
            val rule = RecurrenceRule(
                frequency = r.getValue("frequency").jsonPrimitive.content,
                start = LocalDate.parse(r.getValue("start").jsonPrimitive.content),
                interval = r["interval"]?.jsonPrimitive?.intOrNull ?: 1,
                weekdays = r["weekdays"]?.jsonArray?.map { it.jsonPrimitive.content.toInt() }.orEmpty(),
                until = r["until"]?.jsonPrimitive?.content?.let(LocalDate::parse),
                count = r["count"]?.jsonPrimitive?.intOrNull,
            )
            val from = LocalDate.parse(case.getValue("from").jsonPrimitive.content)
            val to = LocalDate.parse(case.getValue("to").jsonPrimitive.content)
            if (case["error"]?.jsonPrimitive?.booleanOrNull == true) {
                try {
                    rule.occurrences(from, to)
                    fail("$r debía rechazarse")
                } catch (e: ValidationException) {
                    // esperado
                }
            } else {
                assertEquals(r.toString(), case.getValue("out").jsonArray.map { it.jsonPrimitive.content },
                    rule.occurrences(from, to).map { it.toString() })
            }
        }
    }

    @Test
    fun vectoresDeTotales() {
        for (case in json("vectors.json").getValue("finance_summary").jsonArray.map { it.jsonObject }) {
            val rows = case.getValue("transactions").jsonArray.map { it.jsonObject }.map { t ->
                fun s(k: String) = t.getValue(k).jsonPrimitive.content
                FinancialTransaction(
                    id = s("id"), projectId = "p", type = s("type"), direction = s("direction"), date = "2026-11-01",
                    concept = "c", category = s("category"), amount = s("amount"), currency = s("currency"),
                    voidsTransactionId = t["voids_transaction_id"]?.jsonPrimitive?.content,
                )
            }
            val result = Finance.summary(rows, case.getValue("currency").jsonPrimitive.content)
            val out = case.getValue("out").jsonObject
            fun o(k: String) = out.getValue(k).jsonPrimitive.content
            assertEquals(o("income"), result.income)
            assertEquals(o("expenses"), result.expenses)
            assertEquals(o("balance"), result.balance)
            assertEquals(out.getValue("by_category").jsonObject.mapValues { it.value.jsonPrimitive.content }, result.byCategory)
            assertEquals(o("worker_payments"), result.workerPayments)
            assertEquals(o("count").toInt(), result.count)
        }
    }

    @Test
    fun validacionDeMovimientos() {
        val cats = Catalogs.financialCategory.keys
        assertEquals("out", Finance.validate("expense", null, "100", null, cats, "fences"))
        for (bad in listOf<() -> Unit>(
            { Finance.validate("other", null, "1", null, cats, "other") },
            { Finance.validate("worker_payment", null, "1", null, cats, "labor") },
            { Finance.validate("expense", null, "1", null, cats, "inventada") },
            { Finance.validate("expense", "in", "1", null, cats, "fences") },
        )) {
            try {
                bad()
                fail("debía rechazarse")
            } catch (e: ValidationException) {
                // esperado
            }
        }
    }
}
