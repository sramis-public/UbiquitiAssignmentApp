package com.example.ubiquititestassignment.ui.catalog

import com.example.ubiquititestassignment.data.catalog.CatalogProduct
import com.example.ubiquititestassignment.data.catalog.CatalogRefreshState

/** Categories match UIDB deviceType independently of product line. */
enum class CatalogCategory {
    All, Cameras, IoT;

    internal fun matches(product: CatalogProduct): Boolean = when (this) {
        All -> true
        Cameras -> product.deviceType == "camera"
        IoT -> when (product.deviceType) {
            "sensor", "light", "doorlock", "chime" -> true
            else -> false
        }
    }
}

/** Additional multi-select filters, matched by UIDB line.id rather than display names. */
enum class CatalogLine(val id: String) {
    UniFi("unifi-network"),
    UniFiLte("unifi-lte"),
    UniFiProtect("unifi-protect"),
    UniFiAccess("unifi-access"),
}

sealed interface CatalogAvailability {
    data object Loading : CatalogAvailability
    data object Unavailable : CatalogAvailability
    /** Empty products represent a valid empty catalog. Always contains the full catalog. */
    data class Available(val products: List<CatalogProduct>) : CatalogAvailability
}

data class CatalogFilters(
    val category: CatalogCategory = CatalogCategory.All,
    /** Empty selection imposes no line restriction, including on unknown or missing lines. */
    val lines: Set<CatalogLine> = emptySet(),
)

data class CatalogUiState(
    val catalog: CatalogAvailability = CatalogAvailability.Loading,
    val refresh: CatalogRefreshState = CatalogRefreshState.Idle,
    val filters: CatalogFilters = CatalogFilters(),
    /** Products matching the active filters in source order; catalog keeps the full snapshot. */
    val filteredProducts: List<CatalogProduct> = emptyList(),
    /** Last observed content may be stale until observation successfully resumes. */
    val observationFailed: Boolean = false,
)
