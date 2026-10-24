package com.example.ubiquititestassignment

import android.app.Application
import com.example.ubiquititestassignment.data.catalog.CatalogRepository
import dagger.Lazy
import javax.inject.Inject
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class CatalogApplication : Application() {
    @Inject internal lateinit var repository: Lazy<CatalogRepository>

    // Lives for this process; a new process always revalidates, without a TTL.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { repository.get().refresh() }
    }
}
