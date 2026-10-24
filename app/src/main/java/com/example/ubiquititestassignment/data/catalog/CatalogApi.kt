package com.example.ubiquititestassignment.data.catalog

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

internal interface CatalogApi {
    @GET("fingerprint/ui/public.json")
    suspend fun getCatalog(@Header("If-None-Match") etag: String?): Response<CatalogDto>
}
