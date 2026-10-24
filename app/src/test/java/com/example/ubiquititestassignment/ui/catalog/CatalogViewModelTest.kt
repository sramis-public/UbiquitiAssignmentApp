package com.example.ubiquititestassignment.ui.catalog

import androidx.lifecycle.ViewModelStore
import com.example.ubiquititestassignment.data.catalog.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val dao = ControlledDao()
    private var calls = 0
    private var request: suspend () -> Response<CatalogDto> = { throw IOException("offline") }
    private lateinit var repository: CatalogRepository

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = CatalogRepository(object : CatalogApi {
            override suspend fun getCatalog(etag: String?): Response<CatalogDto> {
                calls++
                return request()
            }
        }, dao, dispatcher) { 1L }
    }

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel() = CatalogViewModel(repository).also { store.put("catalog", it) }

    private fun TestScope.collectUiState(
        vm: CatalogViewModel,
        onState: (CatalogUiState) -> Unit = {},
    ) = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        vm.uiState.collect { onState(it) }
    }

    private fun TestScope.observedViewModel() = viewModel().also {
        collectUiState(it)
        runCurrent()
    }

    private suspend fun TestScope.loadedViewModel(record: CatalogRecord = cached) =
        observedViewModel().also {
            dao.emit(record)
            runCurrent()
        }

    private fun pendingResponse() = CompletableDeferred<Response<CatalogDto>>().also { response ->
        request = { response.await() }
    }

    private fun product(id: String, lineId: String? = null, deviceType: String? = null) =
        ProductEntity(id, id, lineId, null, null, 0, deviceType = deviceType)

    private fun catalog(vararg products: ProductEntity) = CatalogRecord(
        CatalogMetadata(etag = null, lastValidatedAtMillis = 1L),
        products.mapIndexed { index, product -> product.copy(position = index) },
    )

    private val cached = catalog(product("id", "unresolved"))
    private val linesCatalog = catalog(
        product("network", "unifi-network").copy(lineName = "Different label"),
        product("lte", "unifi-lte"),
        product("protect", "unifi-protect", "camera"),
        product("access", "unifi-access"),
        product("other", "unifi").copy(lineName = "UniFi"),
        product("missing").copy(lineName = "UniFi Protect"),
        product("talk", "unifi-talk"),
    )

    @Test fun observationStartsWithoutUiCollectorsAndDoesNotFetch() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        assertEquals(1, dao.subscriptions)
        assertEquals(1, dao.activeSubscriptions)
        assertEquals(0, dao.recordReads)
        assertEquals(0, calls)

        dao.emit(cached)
        collectUiState(vm)
        runCurrent()
        assertEquals(CatalogAvailability.Available(cached.toSnapshot()!!.products), vm.uiState.value.catalog)
        assertEquals(1, dao.subscriptions)
    }

    @Test fun uiCollectorsShareOneCatalogObservation() = runTest(dispatcher) {
        val vm = viewModel()
        val firstStates = mutableListOf<CatalogUiState>()
        val secondStates = mutableListOf<CatalogUiState>()
        collectUiState(vm) { firstStates.add(it) }
        collectUiState(vm) { secondStates.add(it) }
        runCurrent()

        dao.emit(cached)
        runCurrent()
        val content = CatalogAvailability.Available(cached.toSnapshot()!!.products)
        assertEquals(content, firstStates.last().catalog)
        assertEquals(content, secondStates.last().catalog)
        assertEquals(1, dao.subscriptions)
        assertEquals(1, dao.activeSubscriptions)
        assertEquals(0, calls)
    }

    @Test fun clearingViewModelStopsObservationWithoutReportingFailure() = runTest(dispatcher) {
        val vm = observedViewModel()
        store.clear()
        runCurrent()
        assertEquals(0, dao.activeSubscriptions)
        assertFalse(vm.uiState.value.observationFailed)
        assertEquals(0, calls)
    }

    @Test fun catalogAndFiltersStayCurrentWhileUiIsAbsent() = runTest(dispatcher) {
        val vm = viewModel()
        val first = collectUiState(vm)
        runCurrent()
        dao.emit(linesCatalog)
        vm.selectCategory(CatalogCategory.Cameras)
        vm.toggleLine(CatalogLine.UniFiProtect)
        runCurrent()

        first.cancel()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, dao.activeSubscriptions)

        vm.toggleLine(CatalogLine.UniFiLte)
        val latest = catalog(linesCatalog.products[1])
        dao.emit(latest)
        runCurrent()
        collectUiState(vm)
        runCurrent()

        assertEquals(1, dao.subscriptions)
        assertEquals(CatalogFilters(
            category = CatalogCategory.Cameras,
            lines = setOf(CatalogLine.UniFiProtect, CatalogLine.UniFiLte),
        ), vm.uiState.value.filters)
        assertEquals(CatalogAvailability.Available(latest.toSnapshot()!!.products), vm.uiState.value.catalog)
        assertTrue(vm.uiState.value.filteredProducts.isEmpty())
        assertEquals(0, calls)
    }

    @Test fun observationFailureWhileUiIsAbsentRetainsContentUntilRetryReceivesData() = runTest(dispatcher) {
        val vm = viewModel()
        val first = collectUiState(vm)
        runCurrent()
        dao.emit(cached)
        runCurrent()
        val content = vm.uiState.value.catalog

        first.cancel()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        dao.events.emit(Result.failure(IOException("read failed while UI was absent")))
        runCurrent()

        val resumedStates = mutableListOf<CatalogUiState>()
        collectUiState(vm) { resumedStates.add(it) }
        runCurrent()
        assertEquals(content, vm.uiState.value.catalog)
        assertTrue(vm.uiState.value.observationFailed)
        assertEquals(0, dao.activeSubscriptions)
        assertEquals(1, dao.subscriptions)

        val gate = CompletableDeferred<Unit>()
        request = { gate.await(); throw IOException("offline") }
        vm.retry()
        vm.retry()
        runCurrent()
        vm.retry()
        runCurrent()
        assertEquals(2, dao.subscriptions)
        assertEquals(1, dao.activeSubscriptions)
        assertEquals(content, vm.uiState.value.catalog)
        assertTrue(vm.uiState.value.observationFailed)
        assertTrue(resumedStates.all { it.catalog == content })
        assertEquals(1, calls)

        dao.emit(cached)
        runCurrent()
        assertFalse(vm.uiState.value.observationFailed)
        assertEquals(content, vm.uiState.value.catalog)
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, calls)
    }

    @Test fun refreshCompletesWithoutUiCollectorsAndIsObservedWhenUiResumes() = runTest(dispatcher) {
        val vm = viewModel()
        val first = collectUiState(vm)
        runCurrent()
        dao.emit(cached)
        runCurrent()

        first.cancel()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, dao.activeSubscriptions)

        val gate = pendingResponse()
        vm.refresh()
        runCurrent()
        assertEquals(CatalogRefreshState.Refreshing, repository.refreshState.value)
        gate.complete(Response.success(CatalogDto(emptyList())))
        runCurrent()
        dao.emitPersisted()
        runCurrent()

        collectUiState(vm)
        runCurrent()
        assertEquals(CatalogRefreshState.Updated, vm.uiState.value.refresh)
        assertEquals(CatalogAvailability.Available(emptyList()), vm.uiState.value.catalog)
        assertEquals(1, dao.subscriptions)
        assertEquals(1, calls)
    }

    @Test fun initialLoadingWaitsForRoomAndDoesNotRefresh() = runTest(dispatcher) {
        val vm = observedViewModel()
        assertEquals(CatalogUiState(), vm.uiState.value)
        assertEquals(0, calls)
        dao.emit(cached)
        runCurrent()
        assertEquals(CatalogAvailability.Available(cached.toSnapshot()!!.products), vm.uiState.value.catalog)
    }

    @Test fun missingCacheWaitsForRefreshAndFailureIsDistinctFromValidEmptyCatalog() = runTest(dispatcher) {
        val vm = observedViewModel()
        dao.emit(null)
        runCurrent()
        assertEquals(CatalogAvailability.Loading, vm.uiState.value.catalog)
        assertTrue(vm.uiState.value.isWaitingForCatalog())

        vm.refresh()
        runCurrent()
        assertEquals(CatalogAvailability.Unavailable, vm.uiState.value.catalog)
        assertTrue(vm.uiState.value.refresh is CatalogRefreshState.Failed)
        assertFalse(vm.uiState.value.isWaitingForCatalog())
        dao.emit(catalog())
        runCurrent()
        assertEquals(CatalogAvailability.Available(emptyList()), vm.uiState.value.catalog)
    }

    @Test fun refreshAndFailurePreserveContentAndRetryDoesNotQueueRequests() = runTest(dispatcher) {
        val vm = loadedViewModel()
        val content = vm.uiState.value.catalog
        val gate = CompletableDeferred<Unit>()
        request = { gate.await(); throw IOException("offline") }
        vm.refresh()
        vm.refresh()
        runCurrent()
        assertEquals(1, calls)
        assertEquals(CatalogRefreshState.Refreshing, vm.uiState.value.refresh)
        assertEquals(content, vm.uiState.value.catalog)
        vm.retry()
        runCurrent()
        assertEquals(1, calls)
        gate.complete(Unit)
        runCurrent()
        assertEquals(CatalogRefreshFailure.Network,
            (vm.uiState.value.refresh as CatalogRefreshState.Failed).reason)
        assertEquals(content, vm.uiState.value.catalog)
        request = { Response.success(CatalogDto(emptyList())) }
        vm.retry()
        vm.retry()
        runCurrent()
        assertEquals(2, calls)
        assertEquals(CatalogRefreshState.Updated, vm.uiState.value.refresh)
        // Successful persistence has not emitted through the controlled observation yet.
        assertEquals(content, vm.uiState.value.catalog)
        dao.emit(catalog())
        runCurrent()
        assertEquals(CatalogAvailability.Available(emptyList()), vm.uiState.value.catalog)
    }

    @Test fun initialRefreshSuccessWaitsForPersistedCatalogWithoutShowingRetry() = runTest(dispatcher) {
        val gate = pendingResponse()
        backgroundScope.launch { repository.refresh() }
        runCurrent()
        val vm = viewModel()
        val states = mutableListOf<CatalogUiState>()
        collectUiState(vm) { states.add(it) }
        runCurrent()
        dao.emit(null)
        runCurrent()
        assertTrue(vm.uiState.value.isWaitingForCatalog())

        gate.complete(Response.success(CatalogDto(listOf(
            DeviceDto("first", ProductDto("First product"), deviceType = "camera"),
        ))))
        runCurrent()
        assertEquals(CatalogRefreshState.Updated, vm.uiState.value.refresh)
        // The write completed, but Room's observable query has not delivered it yet.
        assertTrue(vm.uiState.value.isWaitingForCatalog())

        dao.emitPersisted()
        runCurrent()
        assertEquals(listOf("first"), vm.uiState.value.filteredProducts.map { it.id })
        assertTrue(states.all { it.catalog is CatalogAvailability.Available || it.isWaitingForCatalog() })
        assertEquals(1, calls)
    }

    @Test fun completedStartupRefreshWaitsForFirstRoomResultIncludingEmptyCatalog() = runTest(dispatcher) {
        request = { Response.success(CatalogDto(emptyList())) }
        repository.refresh()
        val vm = observedViewModel()
        // Delay observation independently of the already committed empty catalog.
        dao.events.emit(Result.success(null))
        runCurrent()
        assertEquals(CatalogRefreshState.Updated, vm.uiState.value.refresh)
        assertTrue(vm.uiState.value.isWaitingForCatalog())

        dao.emitPersisted()
        runCurrent()
        assertEquals(CatalogAvailability.Available(emptyList()), vm.uiState.value.catalog)
        assertEquals(1, calls)
    }

    @Test fun applicationRefreshIsObservedWithoutDuplicatingIt() = runTest(dispatcher) {
        pendingResponse()
        val startup = backgroundScope.launch { repository.refresh() }
        runCurrent()
        val vm = viewModel()
        collectUiState(vm)
        vm.refresh()
        runCurrent()
        assertEquals(CatalogRefreshState.Refreshing, vm.uiState.value.refresh)
        assertEquals(CatalogAvailability.Loading, vm.uiState.value.catalog)
        assertEquals(1, calls)
        startup.cancel()
        runCurrent()
        assertEquals(CatalogRefreshState.Idle, vm.uiState.value.refresh)
    }

    @Test fun observationFailureKeepsContentAndRetryResubscribes() = runTest(dispatcher) {
        val vm = loadedViewModel()
        val content = vm.uiState.value.catalog
        dao.events.emit(Result.failure(IOException("read failed")))
        runCurrent()
        assertTrue(vm.uiState.value.observationFailed)
        assertEquals(content, vm.uiState.value.catalog)
        vm.retry()
        runCurrent()
        dao.emit(null)
        runCurrent()
        assertFalse(vm.uiState.value.observationFailed)
        assertEquals(CatalogAvailability.Unavailable, vm.uiState.value.catalog)
        assertEquals(2, dao.subscriptions)
    }

    @Test fun observationFailureBeforeFirstEmissionIsUnavailableAndCanRecover() = runTest(dispatcher) {
        val vm = observedViewModel()
        dao.events.emit(Result.failure(IllegalStateException("closed")))
        runCurrent()
        assertEquals(CatalogAvailability.Unavailable, vm.uiState.value.catalog)
        assertTrue(vm.uiState.value.observationFailed)
        vm.retry()
        vm.retry()
        runCurrent()
        assertEquals(2, dao.subscriptions)
        assertTrue(vm.uiState.value.observationFailed)
        dao.emit(cached)
        runCurrent()
        assertTrue(vm.uiState.value.catalog is CatalogAvailability.Available)
        assertFalse(vm.uiState.value.observationFailed)
        assertEquals(2, dao.subscriptions)
    }

    @Test fun lineSelectionStartsUnrestrictedAndMatchesExactUidbIds() = runTest(dispatcher) {
        val vm = loadedViewModel(linesCatalog)
        assertTrue(vm.uiState.value.filters.lines.isEmpty())
        assertEquals(linesCatalog.toSnapshot()!!.products, vm.uiState.value.filteredProducts)

        val expected = listOf(
            CatalogLine.UniFi to "network",
            CatalogLine.UniFiLte to "lte",
            CatalogLine.UniFiProtect to "protect",
            CatalogLine.UniFiAccess to "access",
        )
        for ((line, id) in expected) {
            vm.toggleLine(line)
            runCurrent()
            assertEquals(setOf(line), vm.uiState.value.filters.lines)
            assertEquals(listOf(id), vm.uiState.value.filteredProducts.map { it.id })
            vm.toggleLine(line)
            runCurrent()
        }
        assertEquals(0, calls)
        assertEquals(1, dao.subscriptions)
    }

    @Test fun multipleLinesUseUnionAndTogglingLastSelectionRestoresAllProducts() = runTest(dispatcher) {
        val vm = loadedViewModel(linesCatalog)
        val content = vm.uiState.value.catalog

        // Reverse selection order must not change the source ordering.
        vm.toggleLine(CatalogLine.UniFiProtect)
        vm.toggleLine(CatalogLine.UniFi)
        runCurrent()
        assertEquals(setOf(CatalogLine.UniFi, CatalogLine.UniFiProtect), vm.uiState.value.filters.lines)
        assertEquals(listOf("network", "protect"), vm.uiState.value.filteredProducts.map { it.id })
        assertEquals(content, vm.uiState.value.catalog)

        vm.toggleLine(CatalogLine.UniFi)
        runCurrent()
        assertEquals(listOf("protect"), vm.uiState.value.filteredProducts.map { it.id })
        vm.toggleLine(CatalogLine.UniFiProtect)
        runCurrent()
        assertTrue(vm.uiState.value.filters.lines.isEmpty())
        assertEquals(linesCatalog.toSnapshot()!!.products, vm.uiState.value.filteredProducts)
        assertEquals(0, calls)
        assertEquals(1, dao.subscriptions)
    }

    @Test fun selectionsApplyToNewSnapshotsAndDistinguishEmptyResultsFromMissingCache() = runTest(dispatcher) {
        val vm = viewModel()
        collectUiState(vm)
        vm.selectCategory(CatalogCategory.Cameras)
        runCurrent()
        assertEquals(CatalogAvailability.Loading, vm.uiState.value.catalog)
        assertTrue(vm.uiState.value.filteredProducts.isEmpty())

        dao.emit(linesCatalog)
        runCurrent()
        assertEquals(listOf("protect"), vm.uiState.value.filteredProducts.map { it.id })

        val noMatch = catalog(linesCatalog.products.first())
        dao.emit(noMatch)
        runCurrent()
        assertEquals(CatalogAvailability.Available(noMatch.toSnapshot()!!.products), vm.uiState.value.catalog)
        assertTrue(vm.uiState.value.filteredProducts.isEmpty())

        dao.emit(catalog())
        runCurrent()
        assertEquals(CatalogAvailability.Available(emptyList()), vm.uiState.value.catalog)
        assertTrue(vm.uiState.value.filteredProducts.isEmpty())

        dao.emit(null)
        runCurrent()
        assertEquals(CatalogAvailability.Unavailable, vm.uiState.value.catalog)
        assertTrue(vm.uiState.value.filteredProducts.isEmpty())
        assertEquals(CatalogCategory.Cameras, vm.uiState.value.filters.category)
        assertTrue(vm.uiState.value.filters.lines.isEmpty())
        assertEquals(0, calls)
    }

    @Test fun categoriesAndLinesFilterCachedProductsInSourceOrderWithoutFetching() = runTest(dispatcher) {
        val record = catalog(
            product("camera", "unifi-protect", "camera"),
            product("sensor", "unifi-protect", "sensor"),
            product("other-sensor", "unifi-led", "sensor"),
            product("other-camera", deviceType = "camera"),
        )
        val vm = loadedViewModel(record)
        val allProducts = record.toSnapshot()!!.products
        assertEquals(allProducts, vm.uiState.value.filteredProducts)

        vm.selectCategory(CatalogCategory.Cameras)
        runCurrent()
        assertEquals(listOf("camera", "other-camera"), vm.uiState.value.filteredProducts.map { it.id })
        vm.selectCategory(CatalogCategory.IoT)
        runCurrent()
        assertEquals(listOf("sensor", "other-sensor"), vm.uiState.value.filteredProducts.map { it.id })

        vm.toggleLine(CatalogLine.UniFiProtect)
        runCurrent()
        assertEquals(listOf("sensor"), vm.uiState.value.filteredProducts.map { it.id })
        vm.selectCategory(CatalogCategory.All)
        runCurrent()
        assertEquals(setOf(CatalogLine.UniFiProtect), vm.uiState.value.filters.lines)
        assertEquals(listOf("camera", "sensor"), vm.uiState.value.filteredProducts.map { it.id })
        vm.toggleLine(CatalogLine.UniFiProtect)
        runCurrent()
        assertEquals(allProducts, vm.uiState.value.filteredProducts)
        assertEquals(CatalogAvailability.Available(allProducts), vm.uiState.value.catalog)
        assertEquals(0, calls)
        assertEquals(0, dao.recordReads)
        assertEquals(1, dao.subscriptions)
    }

    @Test fun categoryAndLineSelectionsSurviveFailureAndApplyToRefreshedCatalog() = runTest(dispatcher) {
        val vm = loadedViewModel(linesCatalog)
        vm.selectCategory(CatalogCategory.Cameras)
        vm.toggleLine(CatalogLine.UniFiProtect)
        runCurrent()
        val filters = vm.uiState.value.filters

        vm.refresh()
        runCurrent()
        assertTrue(vm.uiState.value.refresh is CatalogRefreshState.Failed)
        assertEquals(filters, vm.uiState.value.filters)
        assertEquals(listOf("protect"), vm.uiState.value.filteredProducts.map { it.id })

        val response = pendingResponse()
        vm.retry()
        runCurrent()
        assertEquals(CatalogRefreshState.Refreshing, vm.uiState.value.refresh)
        assertEquals(filters, vm.uiState.value.filters)
        assertEquals(listOf("protect"), vm.uiState.value.filteredProducts.map { it.id })

        response.complete(Response.success(CatalogDto(listOf(
            DeviceDto("updated", ProductDto("Updated Camera"), LineDto("unifi-protect"), deviceType = "camera"),
            DeviceDto("other-line", ProductDto("Other Camera"), LineDto("unifi-network"), deviceType = "camera"),
            DeviceDto("other-type", ProductDto("Sensor"), LineDto("unifi-protect"), deviceType = "sensor"),
        ))))
        runCurrent()
        dao.emitPersisted()
        runCurrent()
        assertEquals(CatalogRefreshState.Updated, vm.uiState.value.refresh)
        assertEquals(filters, vm.uiState.value.filters)
        assertEquals(listOf("updated"), vm.uiState.value.filteredProducts.map { it.id })
    }

    @Test fun clearingViewModelCancelsRefreshWithoutReportingFailure() = runTest(dispatcher) {
        val vm = viewModel()
        collectUiState(vm)
        request = { CompletableDeferred<Response<CatalogDto>>().await() }
        vm.refresh()
        runCurrent()
        store.clear()
        runCurrent()
        assertEquals(CatalogRefreshState.Idle, repository.refreshState.value)
        assertFalse(vm.uiState.value.observationFailed)
    }

    @Test fun sourceOnlyCancellationRetainsContentAndCanBeRetried() = runTest(dispatcher) {
        val vm = loadedViewModel()
        val content = vm.uiState.value.catalog

        // The collector remains active; this exception comes only from the source.
        dao.events.emit(Result.failure(CancellationException("source cancelled")))
        runCurrent()
        assertTrue(vm.uiState.value.observationFailed)
        assertEquals(content, vm.uiState.value.catalog)
        assertEquals(0, dao.activeSubscriptions)

        vm.retry()
        runCurrent()
        assertEquals(2, dao.subscriptions)
        assertEquals(1, dao.activeSubscriptions)
        assertTrue(vm.uiState.value.observationFailed)
        assertEquals(content, vm.uiState.value.catalog)

        dao.emit(cached)
        runCurrent()
        assertFalse(vm.uiState.value.observationFailed)
        assertEquals(content, vm.uiState.value.catalog)
        assertEquals(1, calls)
    }

    /** Real repository, controlled DAO emissions and API suspensions; no HTTP or Room scheduling. */
    private class ControlledDao : CatalogDao() {
        val events = MutableSharedFlow<Result<CatalogRecord?>>()
        var subscriptions = 0
        var activeSubscriptions = 0
        var recordReads = 0
        private var record: CatalogRecord? = null
        override fun observeMetadata() = flow<CatalogMetadata?> { }
        override suspend fun readMetadata() = record?.metadata
        override suspend fun readProducts() = record?.products.orEmpty()
        override fun observeRecord() = flow {
            subscriptions++
            activeSubscriptions++
            try {
                events.collect { emit(it.getOrThrow()) }
            } finally {
                activeSubscriptions--
            }
        }
        suspend fun emit(value: CatalogRecord?) {
            record = value
            events.emit(Result.success(value))
        }
        suspend fun emitPersisted() = events.emit(Result.success(record))
        override suspend fun readRecord(): CatalogRecord? {
            recordReads++
            return record
        }
        override suspend fun deleteProducts() = Unit
        override suspend fun insertProducts(products: List<ProductEntity>) = Unit
        override suspend fun putMetadata(metadata: CatalogMetadata) = Unit
        override suspend fun replace(products: List<CatalogProduct>, etag: String?, validatedAt: Long) {
            // Intentionally delay observation independently from the completed write.
            record = CatalogRecord(CatalogMetadata(etag = etag,
                lastValidatedAtMillis = validatedAt), products.mapIndexed { index, p ->
                ProductEntity(p.id, p.name, p.lineId, p.lineName, p.defaultImageId, index, deviceType = p.deviceType)
            })
        }
    }
}
