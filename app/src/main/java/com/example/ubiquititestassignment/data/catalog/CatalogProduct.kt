package com.example.ubiquititestassignment.data.catalog

data class CatalogProduct(
    val id: String,
    val name: String,
    val lineId: String?,
    val lineName: String?,
    val defaultImageId: String?,
    val deviceType: String? = null,
    val shortnames: List<String> = emptyList(),
)

/** Null snapshot means no usable cache; an empty products list is a valid catalog. */
data class CatalogSnapshot(
    val products: List<CatalogProduct>,
    val lastValidatedAtMillis: Long,
)
