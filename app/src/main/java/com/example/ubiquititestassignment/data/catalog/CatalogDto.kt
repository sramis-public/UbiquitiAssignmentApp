package com.example.ubiquititestassignment.data.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CatalogDto(val devices: List<DeviceDto>)

@Serializable
internal data class DeviceDto(
    val id: String? = null,
    val product: ProductDto? = null,
    val line: LineDto? = null,
    val images: ImagesDto? = null,
    val deviceType: String? = null,
)

@Serializable
internal data class ProductDto(val name: String? = null)

@Serializable
internal data class LineDto(val id: String? = null, val name: String? = null)

@Serializable
internal data class ImagesDto(@SerialName("default") val defaultImageId: String? = null)

/** Skip invalid records and duplicate IDs, keeping the first valid product in source order. */
internal fun CatalogDto.toProducts(): List<CatalogProduct> {
    val ids = HashSet<String>()
    return devices.mapNotNull { device ->
        val id = device.id
        val name = device.product?.name
        if (id.isNullOrBlank() || name.isNullOrBlank() || !ids.add(id)) {
            // TODO: Log skipped invalid products and report them to the backend.
            return@mapNotNull null
        }
        CatalogProduct(
            id = id,
            name = name,
            lineId = device.line?.id?.takeIf { it.isNotBlank() },
            lineName = device.line?.name?.takeIf { it.isNotBlank() },
            defaultImageId = device.images?.defaultImageId?.takeIf { it.isNotBlank() },
            deviceType = device.deviceType?.takeIf { it.isNotBlank() },
        )
    }
}
