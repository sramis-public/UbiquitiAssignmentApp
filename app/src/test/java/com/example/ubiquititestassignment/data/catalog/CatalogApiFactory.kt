package com.example.ubiquititestassignment.data.catalog

import com.example.ubiquititestassignment.di.NetworkModule
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/** Keep fixture tests independent of Hilt while exercising the production Retrofit setup. */
internal fun createCatalogApi(baseUrl: HttpUrl, client: OkHttpClient): CatalogApi {
    val retrofit = NetworkModule.provideRetrofit(baseUrl, client, NetworkModule.provideJson())
    return NetworkModule.provideCatalogApi(retrofit)
}
