package com.example.ubiquititestassignment.di

import com.example.ubiquititestassignment.data.catalog.CatalogApi
import com.example.ubiquititestassignment.data.catalog.CatalogDatabase
import com.example.ubiquititestassignment.data.catalog.CatalogDao
import com.example.ubiquititestassignment.data.catalog.CatalogRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = HiltTestApplication::class)
class NetworkInjectionTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var clients: Provider<OkHttpClient>
    @Inject lateinit var retrofits: Provider<Retrofit>
    @Inject internal lateinit var apis: Provider<CatalogApi>
    @Inject lateinit var json: Json
    @Inject internal lateinit var databases: Provider<CatalogDatabase>
    @Inject internal lateinit var daos: Provider<CatalogDao>
    @Inject lateinit var repositories: Provider<CatalogRepository>

    @Test fun existingStorageAndRepositoryAreSharedWithinTheSameGraph() {
        hiltRule.inject()
        val database = databases.get()
        try {
            assertSame(database, databases.get())
            assertSame(database.catalogDao(), daos.get())
            assertSame(daos.get(), daos.get())
            assertSame(repositories.get(), repositories.get())
        } finally {
            database.close()
        }
    }

    @Test fun hiltProvidesSharedNetworkInstancesWithExistingConfiguration() {
        hiltRule.inject()
        val retrofit = retrofits.get()
        assertSame(retrofit, retrofits.get())
        assertSame(clients.get(), clients.get())
        assertSame(apis.get(), apis.get())
        assertSame(clients.get(), retrofit.callFactory())
        assertEquals("https://static.ui.com/", retrofit.baseUrl().toString())
        assertTrue(json.configuration.ignoreUnknownKeys)
        assertNull(clients.get().cache)
    }
}
