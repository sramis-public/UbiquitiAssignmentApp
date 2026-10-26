package com.example.ubiquititestassignment.ui.catalog

import com.example.ubiquititestassignment.data.catalog.CatalogProduct
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogCategoryTest {
    @Test fun categoriesUseExactDeviceTypesIndependentlyOfLine() {
        val cases = listOf(
            "camera" to CatalogCategory.Cameras,
            "sensor" to CatalogCategory.IoT,
            "light" to CatalogCategory.IoT,
            "doorlock" to CatalogCategory.IoT,
            "chime" to CatalogCategory.IoT,
            "console" to null,
            "accessory" to null,
            "access-point" to null,
            "future-type" to null,
            "Camera" to null,
            "" to null,
            null to null,
        )
        for ((deviceType, category) in cases) {
            for (lineId in listOf("unifi-protect", "unifi-network", "future-line", null)) {
                val product = CatalogProduct("id", "Product", lineId, null, null, deviceType)
                for (filter in CatalogCategory.entries) {
                    assertEquals("type=$deviceType, line=$lineId, filter=$filter",
                        filter == CatalogCategory.All || filter == category, filter.matches(product))
                }
            }
        }
    }
}
