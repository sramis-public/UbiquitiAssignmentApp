package com.example.ubiquititestassignment.data.catalog

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "catalog_products")
internal data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lineId: String?,
    val lineName: String?,
    val defaultImageId: String?,
    val position: Int,
    val deviceType: String? = null,
    val shortnamesJson: String = "[]",
) {
    fun toProduct() = CatalogProduct(
        id, name, lineId, lineName, defaultImageId, deviceType,
        shortnames = Json.decodeFromString<List<String>>(shortnamesJson),
    )
}

@Entity(tableName = "catalog_metadata")
internal data class CatalogMetadata(
    @PrimaryKey val id: Int = 1,
    val etag: String?,
    val lastValidatedAtMillis: Long,
)

internal data class CatalogRecord(
    val metadata: CatalogMetadata,
    val products: List<ProductEntity>,
) {
    fun toSnapshot() = CatalogSnapshot(
        products.map { it.toProduct() },
        metadata.lastValidatedAtMillis,
    )
}

@Dao
internal abstract class CatalogDao {
    @Query("SELECT * FROM catalog_metadata WHERE id = 1")
    abstract fun observeMetadata(): Flow<CatalogMetadata?>

    @Query("SELECT * FROM catalog_metadata WHERE id = 1")
    abstract suspend fun readMetadata(): CatalogMetadata?

    @Query("SELECT * FROM catalog_products ORDER BY position")
    abstract suspend fun readProducts(): List<ProductEntity>

    /** Every catalog write changes metadata, which triggers a fresh transactional read. */
    open fun observeRecord(): Flow<CatalogRecord?> =
        observeMetadata().map { readRecord() }

    @Transaction
    open suspend fun readRecord(): CatalogRecord? =
        readMetadata()?.let { CatalogRecord(it, readProducts()) }

    @Query("DELETE FROM catalog_products")
    abstract suspend fun deleteProducts()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertProducts(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putMetadata(metadata: CatalogMetadata)

    @Transaction
    open suspend fun replace(products: List<CatalogProduct>, etag: String?, validatedAt: Long) {
        deleteProducts()
        insertProducts(products.mapIndexed { index, product ->
            ProductEntity(
                product.id, product.name, product.lineId, product.lineName,
                product.defaultImageId, index, deviceType = product.deviceType,
                shortnamesJson = Json.encodeToString(product.shortnames),
            )
        })
        putMetadata(CatalogMetadata(etag = etag, lastValidatedAtMillis = validatedAt))
    }

    /** A 304 is useful only while the cached representation still exists. */
    @Transaction
    open suspend fun markValidated(etag: String?, validatedAt: Long): Boolean {
        val metadata = readMetadata() ?: return false
        putMetadata(metadata.copy(
            etag = etag ?: metadata.etag,
            lastValidatedAtMillis = validatedAt,
        ))
        return true
    }
}

@Database(entities = [ProductEntity::class, CatalogMetadata::class], version = 1, exportSchema = true)
internal abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
}
