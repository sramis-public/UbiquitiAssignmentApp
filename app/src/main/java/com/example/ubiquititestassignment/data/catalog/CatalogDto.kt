package com.example.ubiquititestassignment.data.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CatalogDto(val devices: List<DeviceDto>)

@Serializable
internal data class DeviceDto(
    val id: String,
    val product: ProductDto,
    val line: LineDto? = null,
    val images: ImagesDto? = null,
)

@Serializable
internal data class ProductDto(val name: String)

@Serializable
internal data class LineDto(val id: String? = null, val name: String? = null)

@Serializable
internal data class ImagesDto(@SerialName("default") val defaultImageId: String? = null)

internal class InvalidCatalogException(message: String) : Exception(message)

/** Validate the whole representation before any persistent data is replaced. */
internal fun CatalogDto.toProducts(): List<CatalogProduct> {
    val ids = HashSet<String>()
    return devices.map { device ->
        if (device.id.isBlank() || device.product.name.isBlank() || !ids.add(device.id)) {
            throw InvalidCatalogException("Product IDs and names must be nonblank; IDs must be unique")
        }
        CatalogProduct(
            id = device.id,
            name = device.product.name,
            lineId = device.line?.id?.takeIf { it.isNotBlank() },
            lineName = device.line?.name?.takeIf { it.isNotBlank() },
            defaultImageId = device.images?.defaultImageId?.takeIf { it.isNotBlank() },
        )
    }
}
