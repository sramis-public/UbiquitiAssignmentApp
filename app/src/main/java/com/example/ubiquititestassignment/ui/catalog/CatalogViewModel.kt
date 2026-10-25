package com.example.ubiquititestassignment.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ubiquititestassignment.data.catalog.CatalogRefreshState
import com.example.ubiquititestassignment.data.catalog.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository,
) : ViewModel() {
    private val filters = MutableStateFlow(CatalogFilters())
    private val observation = MutableStateFlow(CatalogObservation())
    private var observationJob: Job? = null

    val uiState = combine(observation, repository.refreshState, filters) { observed, refresh, selection ->
        observed.toUiState(refresh, selection)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = CatalogUiState(refresh = repository.refreshState.value),
    )

    init {
        observeCatalog()
    }

    // Room observation lasts until this ViewModel is cleared; it never triggers networking.
    private fun observeCatalog() {
        if (observationJob?.isActive == true) return
        observationJob = viewModelScope.launch {
            try {
                repository.catalog.collect { snapshot ->
                    observation.update {
                        CatalogObservation(
                            catalog = snapshot?.let {
                                CatalogAvailability.Available(it.products.toList())
                            } ?: CatalogAvailability.Unavailable,
                        )
                    }
                }
            } catch (failure: Exception) {
                // Propagate collector cancellation; source-only cancellation is a read failure.
                if (failure is CancellationException) currentCoroutineContext().ensureActive()
                observation.update {
                    it.copy(
                        catalog = if (it.catalog == CatalogAvailability.Loading) {
                            CatalogAvailability.Unavailable
                        } else it.catalog,
                        failed = true,
                    )
                }
            }
        }
    }

    fun selectCategory(category: CatalogCategory) {
        filters.update { it.copy(category = category) }
    }

    fun toggleLine(line: CatalogLine) {
        filters.update {
            it.copy(lines = if (line in it.lines) it.lines - line else it.lines + line)
        }
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun retry() {
        observeCatalog()
        refresh()
    }

    private data class CatalogObservation(
        val catalog: CatalogAvailability = CatalogAvailability.Loading,
        val failed: Boolean = false,
    ) {
        fun toUiState(refresh: CatalogRefreshState, filters: CatalogFilters): CatalogUiState {
            val products = (catalog as? CatalogAvailability.Available)?.products.orEmpty()
            val lineIds = filters.lines.mapTo(HashSet()) { it.id }
            return CatalogUiState(
                catalog = catalog,
                refresh = refresh,
                filters = filters,
                filteredProducts = products.filter {
                    lineIds.isEmpty() || it.lineId in lineIds
                },
                observationFailed = failed,
            )
        }
    }
}
