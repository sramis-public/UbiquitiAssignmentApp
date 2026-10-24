package com.example.ubiquititestassignment.data.catalog

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

sealed interface CatalogRefreshFailure {
    data object Network : CatalogRefreshFailure
    data class Http(val statusCode: Int) : CatalogRefreshFailure
    data object InvalidData : CatalogRefreshFailure
    data object Storage : CatalogRefreshFailure
    data object Unexpected : CatalogRefreshFailure
}

sealed interface CatalogRefreshState {
    data object Idle : CatalogRefreshState
    data object Refreshing : CatalogRefreshState
    data object Updated : CatalogRefreshState
    data object NotModified : CatalogRefreshState
    data class Failed(
        val reason: CatalogRefreshFailure,
        val cause: Exception? = null,
    ) : CatalogRefreshState
}

/**
 * The application owns one repository. All catalog writes go through its refresh mutex.
 * Collection reads Room immediately and never waits for a network refresh.
 */
@Singleton
class CatalogRepository internal constructor(
    private val api: CatalogApi,
    private val dao: CatalogDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    @Inject
    internal constructor(api: CatalogApi, dao: CatalogDao) :
        this(api, dao, Dispatchers.IO, System::currentTimeMillis)

    val catalog: Flow<CatalogSnapshot?> = dao.observeRecord()
        .map { it?.toSnapshot() }
        .distinctUntilChanged()

    private val mutableRefreshState = MutableStateFlow<CatalogRefreshState>(CatalogRefreshState.Idle)
    val refreshState: StateFlow<CatalogRefreshState> = mutableRefreshState.asStateFlow()

    private val refreshMutex = Mutex()

    /** Ignores overlapping requests; failures are reported as state, while cancellation propagates. */
    suspend fun refresh() {
        // Claim the refresh before dispatching IO so other callers cannot queue another request.
        if (!refreshMutex.tryLock()) return
        try {
            mutableRefreshState.value = CatalogRefreshState.Refreshing
            mutableRefreshState.value = withContext(ioDispatcher) {
                try {
                    synchronize()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: RefreshException) {
                    CatalogRefreshState.Failed(failure.reason, failure.cause as? Exception)
                } catch (failure: Exception) {
                    CatalogRefreshState.Failed(CatalogRefreshFailure.Unexpected, failure)
                }
            }
        } catch (cancelled: CancellationException) {
            mutableRefreshState.value = CatalogRefreshState.Idle
            throw cancelled
        } finally {
            refreshMutex.unlock()
        }
    }

    private suspend fun synchronize(): CatalogRefreshState {
        val etag = storage { dao.readMetadata()?.etag }
        val firstResult = requestAndApply(etag)
        if (firstResult != null) return firstResult

        // The server returned 304 without a usable cached representation.
        // Retry once without a validator so it can send the full catalog.
        return requestAndApply(null)
            ?: throw RefreshException(CatalogRefreshFailure.Http(304))
    }

    /** Null means a 304 needs one unconditional retry. */
    private suspend fun requestAndApply(etag: String?): CatalogRefreshState? {
        val response = try {
            api.getCatalog(etag)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (invalid: SerializationException) {
            throw RefreshException(CatalogRefreshFailure.InvalidData, invalid)
        } catch (network: IOException) {
            throw RefreshException(CatalogRefreshFailure.Network, network)
        }

        try {
            return when (response.code()) {
                200 -> {
                    val dto = response.body()
                        ?: throw RefreshException(CatalogRefreshFailure.InvalidData)
                    val products = dto.toProducts()
                    // A 200 without an ETag must clear any obsolete validator.
                    storage { dao.replace(products, response.headers()["ETag"], nowMillis()) }
                    CatalogRefreshState.Updated
                }
                304 -> {
                    if (etag != null && storage {
                        dao.markValidated(response.headers()["ETag"], nowMillis())
                    }) CatalogRefreshState.NotModified else null
                }
                else -> throw RefreshException(CatalogRefreshFailure.Http(response.code()))
            }
        } finally {
            response.errorBody()?.close()
        }
    }

    private suspend fun <T> storage(block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        // Room can cancel its own scope when closed while the refresh caller is still active.
        currentCoroutineContext().ensureActive()
        throw RefreshException(CatalogRefreshFailure.Storage, cancelled)
    } catch (failure: Exception) {
        throw RefreshException(CatalogRefreshFailure.Storage, failure)
    }

    private class RefreshException(
        val reason: CatalogRefreshFailure,
        cause: Exception? = null,
    ) : Exception(cause)
}
