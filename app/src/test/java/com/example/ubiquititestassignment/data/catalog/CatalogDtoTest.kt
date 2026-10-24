package com.example.ubiquititestassignment.data.catalog

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CatalogDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun parsesOnlyNeededFieldsAndIgnoresUnknownData() {
        val products = parse("""{"version":"ignored","devices":[{"id":"a","product":{"name":"Camera","abbrev":"x"},"line":{"id":"unifi-protect","name":"UniFi Protect"},"images":{"default":"hash"},"extra":true}]}""")
        assertEquals(listOf(CatalogProduct("a", "Camera", "unifi-protect", "UniFi Protect", "hash")), products)
    }

    @Test fun optionalFieldsMayBeMissingOrNull() {
        val products = parse("""{"devices":[{"id":"a","product":{"name":"A"}},{"id":"b","product":{"name":"B"},"line":null,"images":null}]}""")
        assertEquals(listOf(CatalogProduct("a", "A", null, null, null),
            CatalogProduct("b", "B", null, null, null)), products)
    }

    @Test fun blankOptionalValuesBecomeNull() {
        assertEquals(CatalogProduct("a", "A", null, null, null),
            parse("""{"devices":[{"id":"a","product":{"name":"A"},"line":{"id":"","name":" "},"images":{"default":""}}]}""").single())
    }

    @Test fun emptyDevicesIsValidButMissingDevicesIsNot() {
        assertTrue(parse("""{"devices":[]}""").isEmpty())
        assertThrows(SerializationException::class.java) { parse("{}") }
    }

    @Test fun missingRequiredFieldsAndWrongTypesFail() {
        listOf(
            """{"devices":[{"product":{"name":"A"}}]}""",
            """{"devices":[{"id":"a","product":{}}]}""",
            """{"devices":null}""",
            """{"devices":[{"id":"a","product":{"name":"A"},"images":[]}]}""",
        ).forEach { body ->
            assertThrows(SerializationException::class.java) { parse(body) }
        }
    }

    @Test fun blankIdsNamesAndDuplicateIdsFail() {
        listOf(
            """{"devices":[{"id":" ","product":{"name":"A"}}]}""",
            """{"devices":[{"id":"a","product":{"name":" "}}]}""",
            """{"devices":[{"id":"a","product":{"name":"A"}},{"id":"a","product":{"name":"B"}}]}""",
        ).forEach { body ->
            assertThrows(InvalidCatalogException::class.java) { parse(body) }
        }
    }

    private fun parse(body: String) = json.decodeFromString<CatalogDto>(body).toProducts()
}
