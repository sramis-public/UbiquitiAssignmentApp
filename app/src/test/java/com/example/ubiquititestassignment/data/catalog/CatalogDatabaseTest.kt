package com.example.ubiquititestassignment.data.catalog

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CatalogDatabaseTest {
    private lateinit var database: CatalogDatabase
    private val context get() = RuntimeEnvironment.getApplication()
    private val product = CatalogProduct(
        "a", "A", "raw-line", "Raw Line", "hash", "sensor",
        shortnames = listOf("AF-11FX", "AF11FX"),
    )

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java).build()
    }

    @After fun tearDown() {
        database.close()
        context.deleteDatabase("catalog-reopen-test")
    }

    @Test fun shortnamesAreStoredAsJsonAndDecodedWithoutLosingEscapesOrOrder() = runBlocking {
        val names = listOf("First", "Quote \" and slash \\", "Line\nbreak", "日本語")
        val products = listOf(product.copy(shortnames = names), product.copy(id = "empty", shortnames = emptyList()))
        database.catalogDao().replace(products, null, 10)
        val entities = database.catalogDao().readProducts()
        assertEquals(names, kotlinx.serialization.json.Json.decodeFromString<List<String>>(entities.first().shortnamesJson))
        assertEquals("[]", entities.last().shortnamesJson)
        assertEquals(products, database.catalogDao().readRecord()!!.toSnapshot().products)
    }

    @Test fun distinguishesAbsentAndEmptySnapshot() = runBlocking {
        assertNull(database.catalogDao().observeRecord().first())
        database.catalogDao().replace(emptyList(), "empty-tag", 10)
        val record = database.catalogDao().observeRecord().first()!!
        assertEquals(CatalogSnapshot(emptyList(), 10), record.toSnapshot())
        assertEquals("empty-tag", record.metadata.etag)
    }

    @Test fun replacementPreservesSourceOrderAndStoresMetadataTogether() = runBlocking {
        val products = listOf(product.copy(id = "z"), product.copy(id = "a"))
        database.catalogDao().replace(products, """W/"exact-tag" """.trimEnd(), 20)
        val record = database.catalogDao().observeRecord().first()!!
        assertEquals(products, record.toSnapshot()!!.products)
        assertEquals("""W/"exact-tag" """.trimEnd(), record.metadata.etag)
        assertEquals(20L, record.metadata.lastValidatedAtMillis)
    }

    @Test fun failedReplacementRollsBackProductsAndMetadata() = runBlocking {
        val dao = database.catalogDao()
        dao.replace(listOf(product), "original", 10)
        val before = dao.readRecord()
        try {
            dao.replace(listOf(product.copy(name = "Changed"), product), "bad", 20)
            fail("Duplicate primary keys must abort the transaction")
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            // ABORT must also undo the DELETE that precedes insertion.
        }
        assertEquals(before, dao.readRecord())
    }

    @Test fun absentRepresentationCannotBeRevalidated() = runBlocking {
        val dao = database.catalogDao()
        dao.replace(listOf(product), "original", 10)
        withContext(Dispatchers.IO) { database.clearAllTables() }
        assertNull(dao.readRecord())
        assertFalse(dao.markValidated("new", 20))
        assertNull(dao.readRecord())
    }

    @Test fun successfulRevalidationKeepsProductsAndUpdatesOnlyMetadata() = runBlocking {
        val dao = database.catalogDao()
        dao.replace(listOf(product), "original", 10)
        assertTrue(dao.markValidated(null, 20))
        assertEquals(listOf(product), dao.readRecord()!!.toSnapshot()!!.products)
        assertEquals("original", dao.readRecord()!!.metadata.etag)
        assertEquals(20L, dao.readRecord()!!.metadata.lastValidatedAtMillis)
    }

    @Test fun namedDatabaseRetainsSnapshotAcrossReopening() = runBlocking {
        database.close()
        database = Room.databaseBuilder(context, CatalogDatabase::class.java, "catalog-reopen-test").build()
        database.catalogDao().replace(listOf(product), """W/"persisted" """.trimEnd(), 10)
        database.close()
        database = Room.databaseBuilder(context, CatalogDatabase::class.java, "catalog-reopen-test").build()
        val record = database.catalogDao().readRecord()!!
        assertEquals(CatalogSnapshot(listOf(product), 10), record.toSnapshot())
        assertEquals("""W/"persisted" """.trimEnd(), record.metadata.etag)
    }
}
