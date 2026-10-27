package com.example.ubiquititestassignment.data.catalog

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CatalogDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun parsesOnlyNeededFieldsAndIgnoresUnknownData() {
        val products = parse("""{"version":"ignored","devices":[{"id":"a","product":{"name":"Camera","abbrev":"x"},"line":{"id":"unifi-protect","name":"UniFi Protect"},"images":{"default":"hash"},"deviceType":"camera","extra":true}]}""")
        assertEquals(listOf(CatalogProduct("a", "Camera", "unifi-protect", "UniFi Protect", "hash", "camera")), products)
    }

    @Test fun optionalFieldsMayBeMissingOrNull() {
        val products = parse("""{"devices":[{"id":"a","product":{"name":"A"}},{"id":"b","product":{"name":"B"},"line":null,"images":null,"deviceType":null}]}""")
        assertEquals(listOf(CatalogProduct("a", "A", null, null, null),
            CatalogProduct("b", "B", null, null, null)), products)
    }

    @Test fun shortnamesPreserveOrderAndDefaultToEmpty() {
        val products = parse("""{"devices":[
            {"id":"a","product":{"name":"A"},"shortnames":["AF-11FX","AF11FX"]},
            {"id":"b","product":{"name":"B"},"shortnames":null},
            {"id":"c","product":{"name":"C"}},
            {"id":"d","product":{"name":"D"},"shortnames":[]}
        ]}""")
        assertEquals(listOf("AF-11FX", "AF11FX"), products.first().shortnames)
        assertTrue(products.drop(1).all { it.shortnames.isEmpty() })
    }

    @Test fun blankOptionalValuesBecomeNull() {
        assertEquals(CatalogProduct("a", "A", null, null, null),
            parse("""{"devices":[{"id":"a","product":{"name":"A"},"line":{"id":"","name":" "},"images":{"default":""},"deviceType":" "}]}""").single())
    }

    @Test fun unknownDeviceTypeIsPreserved() {
        assertEquals("future-type", parse(
            """{"devices":[{"id":"a","product":{"name":"A"},"deviceType":"future-type"}]}"""
        ).single().deviceType)
    }

    @Test fun emptyDevicesIsValidButMissingDevicesIsNot() {
        assertTrue(parse("""{"devices":[]}""").isEmpty())
        assertThrows(SerializationException::class.java) { parse("{}") }
    }

    @Test fun malformedCatalogAndWrongTypesFail() {
        listOf(
            "{",
            """{"devices":[{"id":"a","product":{"name":"A"},"shortnames":"A"}]}""",
            """{"devices":[{"id":"a","product":{"name":"A"},"shortnames":[{}]}]}""",
            """{"devices":null}""",
            """{"devices":[{"id":"a","product":{"name":"A"},"images":[]}]}""",
            """{"devices":[{"id":"a","product":{"name":"A"},"deviceType":{}}]}""",
        ).forEach { body ->
            assertThrows(SerializationException::class.java) { parse(body) }
        }
    }

    @Test fun invalidProductsAreSkippedWithoutDroppingValidNeighbors() {
        invalidRecords.forEach { invalid ->
            val products = parse("""
                {"devices":[
                    {"id":"first","product":{"name":"First"}},
                    $invalid,
                    {"id":"last","product":{"name":"Last"}}
                ]}
            """.trimIndent())
            assertEquals(invalid, listOf("first", "last"), products.map { it.id })
        }
    }

    @Test fun duplicatesKeepFirstValidProductInSourceOrder() {
        val products = parse("""
            {"devices":[
                {"id":"a","product":{"name":" "}},
                {"id":"b","product":{"name":"B"}},
                {"id":"a","product":{"name":"First valid A"}},
                {"id":"a","product":{"name":"Duplicate A"}}
            ]}
        """.trimIndent())
        assertEquals(listOf(
            CatalogProduct("b", "B", null, null, null),
            CatalogProduct("a", "First valid A", null, null, null),
        ), products)
    }

    @Test fun catalogWithOnlyInvalidProductsIsEmpty() {
        assertTrue(parse("""{"devices":[${invalidRecords.joinToString(",")}]}""").isEmpty())
    }

    private val invalidRecords = listOf(
        """{"product":{"name":"Missing ID"}}""",
        """{"id":null,"product":{"name":"Null ID"}}""",
        """{"id":"","product":{"name":"Empty ID"}}""",
        """{"id":" ","product":{"name":"Blank ID"}}""",
        """{"id":"a"}""",
        """{"id":"a","product":null}""",
        """{"id":"a","product":{}}""",
        """{"id":"a","product":{"name":null}}""",
        """{"id":"a","product":{"name":""}}""",
        """{"id":"a","product":{"name":" "}}""",
    )

    private fun parse(body: String) = json.decodeFromString<CatalogDto>(body).toProducts()
}
