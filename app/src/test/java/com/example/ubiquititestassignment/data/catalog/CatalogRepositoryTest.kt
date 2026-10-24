package com.example.ubiquititestassignment.data.catalog

import android.app.Application
import androidx.room.Room
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CatalogRepositoryTest {
    private lateinit var database: CatalogDatabase
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var repository: CatalogRepository
    private val dao get() = database.catalogDao()
    private val oldProduct = CatalogProduct("old", "Old product", null, null, null, "sensor")
    private val oldEtag = "W/\"old-tag\""

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), CatalogDatabase::class.java,
        ).build()
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .callTimeout(5, TimeUnit.SECONDS)
            .cache(null)
            .build()
        repository = newRepository()
    }

    @After fun tearDown() {
        database.close()
        server.shutdown()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    @Test fun initialFetchIsUnconditionalAndPersistsProductsAndExactEtag() = runBlocking {
        server.enqueue(ok("new", "W/\"new-tag\""))
        repository.refresh()

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/fingerprint/ui/public.json", request.path)
        assertNull(request.getHeader("If-None-Match"))
        assertEquals(CatalogRefreshState.Updated, repository.refreshState.value)
        val record = dao.readRecord()!!
        assertEquals("W/\"new-tag\"", record.metadata.etag)
        assertEquals(listOf("new"), record.toSnapshot()!!.products.map { it.id })
        assertEquals("camera", record.toSnapshot()!!.products.single().deviceType)
        assertEquals(200L, record.metadata.lastValidatedAtMillis)
    }

    @Test fun conditional304PreservesProductsAndRecordsSuccessfulRevalidation() = runBlocking {
        seed()
        server.enqueue(MockResponse().setResponseCode(304))
        repository.refresh()

        assertEquals(oldEtag, server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("If-None-Match"))
        val record = dao.readRecord()!!
        assertEquals(listOf(oldProduct), record.toSnapshot()!!.products)
        assertEquals(oldEtag, record.metadata.etag)
        assertEquals(200L, record.metadata.lastValidatedAtMillis)
        assertEquals(CatalogRefreshState.NotModified, repository.refreshState.value)
    }

    @Test fun response200ReplacesProductsAndValidatorTogether() = runBlocking {
        seed()
        server.enqueue(ok("new", "\"new-tag\""))
        repository.refresh()
        assertEquals(oldEtag, server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("If-None-Match"))
        assertEquals(listOf("new"), dao.readRecord()!!.toSnapshot()!!.products.map { it.id })
        assertEquals("\"new-tag\"", dao.readRecord()!!.metadata.etag)

        server.enqueue(MockResponse().setResponseCode(304).setHeader("ETag", "W/\"revalidated\""))
        repository.refresh()
        assertEquals("\"new-tag\"", server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("If-None-Match"))
        assertEquals("W/\"revalidated\"", dao.readRecord()!!.metadata.etag)
        assertEquals(listOf("new"), repository.catalog.first()!!.products.map { it.id })
    }

    @Test fun networkFailurePreservesEntireSnapshotAndExposesFailure() = runBlocking {
        seed()
        val before = dao.readRecord()
        val failingApi = object : CatalogApi {
            override suspend fun getCatalog(etag: String?): Response<CatalogDto> {
                assertEquals(oldEtag, etag)
                throw IOException("Connection unavailable")
            }
        }
        val failingRepository = newRepository(failingApi)
        failingRepository.refresh()
        assertEquals(before, dao.readRecord())
        assertFailure(CatalogRefreshFailure.Network, failingRepository)
        assertEquals(listOf(oldProduct), failingRepository.catalog.first()!!.products)
    }

    @Test fun unexpectedApiFailureIsReportedAndRetryRecoversWithOrWithoutCache() = runBlocking {
        for (hasCache in listOf(false, true)) {
            withContext(Dispatchers.IO) { database.clearAllTables() }
            if (hasCache) seed()
            val before = dao.readRecord()
            val failure = IllegalStateException("Unexpected API failure")
            var shouldFail = true
            val api = object : CatalogApi {
                override suspend fun getCatalog(etag: String?): Response<CatalogDto> {
                    if (shouldFail) throw failure
                    return Response.success(CatalogDto(listOf(DeviceDto("recovered", ProductDto("Recovered")))))
                }
            }
            val target = newRepository(api)

            // Also verifies that a startup caller does not receive an uncaught exception.
            target.refresh()
            assertFailure(CatalogRefreshFailure.Unexpected, target)
            assertSame(failure, (target.refreshState.value as CatalogRefreshState.Failed).cause)
            assertEquals(before, dao.readRecord())

            shouldFail = false
            withTimeout(5_000) { target.refresh() }
            assertEquals(CatalogRefreshState.Updated, target.refreshState.value)
            assertEquals(listOf("recovered"), target.catalog.first()!!.products.map { it.id })
        }
    }

    @Test fun httpErrorPreservesCacheAndLaterRefreshRecovers() = runBlocking {
        seed()
        val before = dao.readRecord()
        server.enqueue(MockResponse().setResponseCode(503).setBody("Unavailable"))
        repository.refresh()
        assertEquals(before, dao.readRecord())
        assertFailure(CatalogRefreshFailure.Http(503))
        server.enqueue(ok("recovered", "recovered-tag"))
        repository.refresh()
        assertEquals(CatalogRefreshState.Updated, repository.refreshState.value)
        assertEquals(listOf("recovered"), repository.catalog.first()!!.products.map { it.id })
    }

    @Test fun malformedCatalogNeverReplacesCacheOrEtag() = runBlocking {
        seed()
        val before = dao.readRecord()
        listOf(
            "{",
            "{}",
            """{"devices":null}""",
            """{"devices":[{"id":"x","product":[]}]}""",
        ).forEach { body ->
            server.enqueue(MockResponse().setBody(body).setHeader("ETag", "bad-tag"))
            repository.refresh()
            assertFailure(CatalogRefreshFailure.InvalidData)
            assertEquals(before, dao.readRecord())
        }
    }

    @Test fun validProductsFromMixedCatalogArePersistedAndRevalidated() = runBlocking {
        seed()
        server.enqueue(MockResponse().setHeader("ETag", "filtered-tag").setBody("""
            {"devices":[
                {"id":"missing-product"},
                {"id":"first","product":{"name":" "}},
                {"id":"first","product":{"name":"First"}},
                {"id":"first","product":{"name":"Duplicate"}},
                {"id":"last","product":{"name":"Last"}}
            ]}
        """.trimIndent()))
        repository.refresh()
        server.takeRequest(5, TimeUnit.SECONDS)
        assertEquals(CatalogRefreshState.Updated, repository.refreshState.value)
        val record = dao.readRecord()!!
        assertEquals(listOf("first", "last"), record.toSnapshot()!!.products.map { it.id })
        assertEquals(listOf("First", "Last"), record.products.map { it.name })
        assertEquals("filtered-tag", record.metadata.etag)

        server.enqueue(MockResponse().setResponseCode(304))
        repository.refresh()
        assertEquals("filtered-tag", server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("If-None-Match"))
        assertEquals(CatalogRefreshState.NotModified, repository.refreshState.value)
        assertEquals(record, dao.readRecord())
    }

    @Test fun missingResponseBodyDoesNotReplaceCache() = runBlocking {
        seed()
        val before = dao.readRecord()
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "empty-body"))
        repository.refresh()
        assertFailure(CatalogRefreshFailure.InvalidData)
        assertEquals(before, dao.readRecord())
    }

    @Test fun nullBodyFromApiIsRejected() = runBlocking {
        seed()
        val before = dao.readRecord()
        val nullBodyApi = object : CatalogApi {
            override suspend fun getCatalog(etag: String?): Response<CatalogDto> =
                Response.success(null)
        }
        val nullBodyRepository = newRepository(nullBodyApi)
        nullBodyRepository.refresh()
        assertFailure(CatalogRefreshFailure.InvalidData, nullBodyRepository)
        assertEquals(before, dao.readRecord())
    }

    @Test fun responseWithoutEtagClearsOldValidatorAndNextRequestIsUnconditional() = runBlocking {
        seed()
        server.enqueue(ok("new", null))
        repository.refresh()
        server.takeRequest(5, TimeUnit.SECONDS)
        assertNull(dao.readRecord()!!.metadata.etag)
        server.enqueue(ok("next", null))
        repository.refresh()
        assertNull(server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("If-None-Match"))
        assertEquals(listOf("next"), repository.catalog.first()!!.products.map { it.id })
    }

    @Test fun emptyCatalogIsPersistedAndCanBeConditionallyRevalidated() = runBlocking {
        seed()
        server.enqueue(MockResponse().setBody("""{"devices":[]}""").setHeader("ETag", "empty"))
        repository.refresh()
        server.takeRequest(5, TimeUnit.SECONDS)
        assertEquals(emptyList<CatalogProduct>(), repository.catalog.first()!!.products)
        server.enqueue(MockResponse().setResponseCode(304))
        repository.refresh()
        assertEquals("empty", server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("If-None-Match"))
        assertEquals(CatalogRefreshState.NotModified, repository.refreshState.value)
        assertEquals(emptyList<ProductEntity>(), dao.readRecord()!!.products)
    }

    @Test fun absentSnapshotNeverSendsStoredValidator() = runBlocking {
        seed()
        withContext(Dispatchers.IO) { database.clearAllTables() }
        assertNull(repository.catalog.first())
        server.enqueue(ok("new", "new"))
        repository.refresh()
        assertNull(server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("If-None-Match"))
        assertEquals(listOf("new"), repository.catalog.first()!!.products.map { it.id })
    }

    @Test fun snapshotLostDuringConditionalRequestTriggersUnconditionalRecovery() = runBlocking {
        seed()
        val realApi = createCatalogApi(server.url("/"), client)
        val calls = AtomicInteger()
        val invalidatingApi = object : CatalogApi {
            override suspend fun getCatalog(etag: String?): Response<CatalogDto> {
                val response = realApi.getCatalog(etag)
                if (calls.getAndIncrement() == 0) database.clearAllTables()
                return response
            }
        }
        server.enqueue(MockResponse().setResponseCode(304))
        server.enqueue(ok("recovered", "fresh-tag"))
        val recoveringRepository = newRepository(invalidatingApi)
        recoveringRepository.refresh()
        assertEquals(oldEtag, server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("If-None-Match"))
        assertNull(server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("If-None-Match"))
        assertEquals(CatalogRefreshState.Updated, recoveringRepository.refreshState.value)
        assertEquals(listOf("recovered"), recoveringRepository.catalog.first()!!.products.map { it.id })
        assertEquals("fresh-tag", dao.readRecord()!!.metadata.etag)
    }

    @Test fun unexpected304RetriesOnceWithoutValidator() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(304))
        server.enqueue(ok("recovered", "new"))
        repository.refresh()
        repeat(2) {
            assertNull(server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("If-None-Match"))
        }
        assertEquals(CatalogRefreshState.Updated, repository.refreshState.value)
        assertEquals(listOf("recovered"), repository.catalog.first()!!.products.map { it.id })
    }

    @Test fun repeatedUnexpected304FailsWithoutCreatingSnapshotOrRetryingForever() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(304))
        server.enqueue(MockResponse().setResponseCode(304))
        repository.refresh()
        assertFailure(CatalogRefreshFailure.Http(304))
        assertNull(dao.readRecord())
        assertEquals(2, server.requestCount)
    }

    @Test fun overlappingRefreshIsIgnoredAndLaterRefreshUsesCommittedValidator() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val validators = mutableListOf<String?>()
        val api = object : CatalogApi {
            override suspend fun getCatalog(etag: String?): Response<CatalogDto> {
                validators.add(etag)
                maxActive.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                return try {
                    if (calls.incrementAndGet() == 1) {
                        entered.complete(Unit)
                        release.await()
                    }
                    Response.success(
                        CatalogDto(listOf(DeviceDto("new", ProductDto("New")))),
                        okhttp3.Headers.headersOf("ETag", "committed"),
                    )
                } finally {
                    active.decrementAndGet()
                }
            }
        }
        val immediateDao = ImmediateCatalogDao()
        val target = CatalogRepository(api, immediateDao, Dispatchers.Unconfined) { 200L }
        val first = async(start = CoroutineStart.UNDISPATCHED) { target.refresh() }
        withTimeout(5_000) { entered.await() }
        val second = async(start = CoroutineStart.UNDISPATCHED) { target.refresh() }
        // The second call finishes without waiting for the active refresh.
        assertTrue(second.isCompleted)
        second.await()
        assertEquals(1, calls.get())
        assertEquals(CatalogRefreshState.Refreshing, target.refreshState.value)
        release.complete(Unit)
        withTimeout(5_000) { first.await() }
        assertEquals(1, calls.get())
        assertEquals(CatalogRefreshState.Updated, target.refreshState.value)

        // A later explicit refresh is still allowed and uses the saved validator.
        withTimeout(5_000) { target.refresh() }
        assertEquals(2, calls.get())
        assertEquals(1, maxActive.get())
        assertEquals(listOf(null, "committed"), validators)
        assertEquals("committed", immediateDao.readRecord()!!.metadata.etag)
        assertEquals(1, immediateDao.readRecord()!!.products.size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun overlappingRefreshIsIgnoredBeforeIoStarts() = runTest {
        var calls = 0
        val api = object : CatalogApi {
            override suspend fun getCatalog(etag: String?): Response<CatalogDto> {
                calls++
                return Response.success(CatalogDto(emptyList()))
            }
        }
        val target = CatalogRepository(
            api, ImmediateCatalogDao(), StandardTestDispatcher(testScheduler),
        ) { 200L }
        val first = async(start = CoroutineStart.UNDISPATCHED) { target.refresh() }
        val second = async(start = CoroutineStart.UNDISPATCHED) { target.refresh() }
        assertEquals(0, calls)
        assertFalse(first.isCompleted)
        assertTrue(second.isCompleted)
        assertEquals(CatalogRefreshState.Refreshing, target.refreshState.value)

        runCurrent()
        first.await()
        second.await()
        assertEquals(1, calls)
        assertEquals(CatalogRefreshState.Updated, target.refreshState.value)
    }

    @Test fun cachedDataIsObservableWhileRefreshWaitsAndCancellationPropagates() = runBlocking {
        seed()
        val before = dao.readRecord()
        val entered = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val waitingApi = object : CatalogApi {
            override suspend fun getCatalog(etag: String?): Response<CatalogDto> {
                if (calls.incrementAndGet() == 1) {
                    entered.complete(Unit)
                    awaitCancellation()
                }
                return Response.success(CatalogDto(emptyList()))
            }
        }
        val waitingRepository = newRepository(waitingApi)
        val refresh = launch { waitingRepository.refresh() }
        withTimeout(5_000) { entered.await() }
        assertEquals(CatalogRefreshState.Refreshing, waitingRepository.refreshState.value)
        val snapshot = withTimeout(5_000) { waitingRepository.catalog.first() }
        assertEquals(listOf(oldProduct), snapshot!!.products)
        refresh.cancelAndJoin()
        assertTrue(refresh.isCancelled)
        assertEquals(CatalogRefreshState.Idle, waitingRepository.refreshState.value)
        assertEquals(before, dao.readRecord())

        // The same repository must release its mutex and accept a subsequent refresh.
        withTimeout(5_000) { waitingRepository.refresh() }
        assertTrue(repository.catalog.first()!!.products.isEmpty())
    }

    @Test fun storageFailureIsExposedBeforeMakingHttpRequest() = runBlocking {
        database.close()
        repository.refresh()
        assertFailure(CatalogRefreshFailure.Storage)
        assertEquals(0, server.requestCount)
    }

    @Test fun storageWriteFailureRollsBackAndIsExposed() = runBlocking {
        seed()
        val before = dao.readRecord()
        withContext(Dispatchers.IO) {
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_metadata_insert BEFORE INSERT ON catalog_metadata " +
                    "BEGIN SELECT RAISE(ABORT, 'Simulated storage failure'); END",
            )
        }
        server.enqueue(ok("new", "new-tag"))
        repository.refresh()
        assertFailure(CatalogRefreshFailure.Storage)
        assertEquals(before, dao.readRecord())
    }

    @Test fun httpCacheHeadersDoNotSuppressExplicitRevalidation() = runBlocking {
        server.enqueue(ok("new", "tag").setHeader("Cache-Control", "max-age=86400"))
        repository.refresh()
        server.takeRequest(5, TimeUnit.SECONDS)
        server.enqueue(MockResponse().setResponseCode(304))
        repository.refresh()
        assertEquals("tag", server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("If-None-Match"))
        assertEquals(2, server.requestCount)
        assertEquals(CatalogRefreshState.NotModified, repository.refreshState.value)
    }

    /** Synchronous storage fixture isolates mutex behavior from Room's own scheduling. */
    private class ImmediateCatalogDao : CatalogDao() {
        private val state = MutableStateFlow<CatalogRecord?>(null)
        private var products = emptyList<ProductEntity>()

        override fun observeMetadata() = flowOf<CatalogMetadata?>(null)
        override suspend fun readMetadata() = state.value?.metadata
        override suspend fun readProducts() = products
        override fun observeRecord() = state
        override suspend fun readRecord() = state.value
        override suspend fun deleteProducts() { products = emptyList() }
        override suspend fun insertProducts(products: List<ProductEntity>) {
            this.products = products
        }
        override suspend fun putMetadata(metadata: CatalogMetadata) {
            state.value = CatalogRecord(metadata, products)
        }
    }

    private fun newRepository(api: CatalogApi = createCatalogApi(server.url("/"), client)) =
        CatalogRepository(api, dao, Dispatchers.IO) { 200L }

    private suspend fun seed() = dao.replace(listOf(oldProduct), oldEtag, 100L)

    private fun ok(id: String, etag: String?): MockResponse =
        MockResponse().setBody("""{"devices":[{"id":"$id","product":{"name":"Product $id"},"deviceType":"camera"}]}""")
            .also { if (etag != null) it.setHeader("ETag", etag) }

    private fun assertFailure(
        reason: CatalogRefreshFailure,
        target: CatalogRepository = repository,
    ) {
        assertEquals(reason, (target.refreshState.value as CatalogRefreshState.Failed).reason)
    }
}
