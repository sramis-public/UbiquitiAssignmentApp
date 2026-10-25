package com.example.ubiquititestassignment.data.catalog

import okhttp3.HttpUrl

/** Returns the assignment's image-service URL, or null when no image is available. */
fun CatalogProduct.imageUrl(width: Int): String? {
    require(width > 0) { "Image width must be positive." }
    val imageId = defaultImageId?.takeIf { it.isNotBlank() } ?: return null
    if (id.isBlank()) return null

    val sourceUrl = HttpUrl.Builder()
        .scheme("https")
        .host("static.ui.com")
        .addPathSegments("fingerprint/ui/images")
        .addPathSegment(id)
        .addPathSegment("default")
        .addPathSegment("${imageId}.png")
        .build()

    return HttpUrl.Builder()
        .scheme("https")
        .host("images.svc.ui.com")
        .addQueryParameter("u", sourceUrl.toString())
        .addQueryParameter("w", width.toString())
        .addQueryParameter("q", "75")
        .build()
        .toString()
}
