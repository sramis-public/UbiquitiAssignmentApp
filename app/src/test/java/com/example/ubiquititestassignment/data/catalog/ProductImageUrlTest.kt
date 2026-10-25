package com.example.ubiquititestassignment.data.catalog

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProductImageUrlTest {
    private val product = CatalogProduct("product-id", "Product", null, null, "image-hash")

    @Test fun usesAssignmentServiceUrlAndRequestedWidth() {
        assertEquals(
            "https://images.svc.ui.com/?u=https%3A%2F%2Fstatic.ui.com%2Ffingerprint%2Fui%2Fimages%2Fproduct-id%2Fdefault%2Fimage-hash.png&w=128&q=75",
            product.imageUrl(128),
        )
        assertEquals("768", product.imageUrl(768)!!.toHttpUrl().queryParameter("w"))
    }

    @Test fun missingAndBlankImageIdsDoNotCreateRequests() {
        for (imageId in listOf(null, "", " \t\n")) {
            assertNull(product.copy(defaultImageId = imageId).imageUrl(128))
        }
        assertNull(product.copy(id = " ").imageUrl(128))
    }

    @Test fun encodesEachDynamicPathSegmentAndTheNestedUrl() {
        val id = "device/a b?x=1#%é"
        val imageId = "hash/a b?width=1#%é"
        val url = product.copy(id = id, defaultImageId = imageId).imageUrl(128)!!.toHttpUrl()
        val source = url.queryParameter("u")!!.toHttpUrl()

        assertEquals("images.svc.ui.com", url.host)
        assertEquals(setOf("u", "w", "q"), url.queryParameterNames)
        assertEquals("static.ui.com", source.host)
        assertEquals(
            listOf("fingerprint", "ui", "images", id, "default", "${imageId}.png"),
            source.pathSegments,
        )
        assertNull(source.query)
        assertNull(source.fragment)
    }

    @Test fun rejectsNonPositiveWidths() {
        assertThrows(IllegalArgumentException::class.java) { product.imageUrl(0) }
        assertThrows(IllegalArgumentException::class.java) { product.imageUrl(-1) }
    }
}
