package com.example.ubiquititestassignment.di

import android.content.Context
import androidx.room.Room
import com.example.ubiquititestassignment.data.catalog.CatalogDao
import com.example.ubiquititestassignment.data.catalog.CatalogDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Bindings for the storage already used by catalog synchronization. */
@Module
@InstallIn(SingletonComponent::class)
internal object CatalogStorageModule {
    @Provides
    @Singleton
    fun provideCatalogDatabase(@ApplicationContext context: Context): CatalogDatabase =
        Room.databaseBuilder(context, CatalogDatabase::class.java, "catalog.db").build()

    @Provides
    @Singleton
    fun provideCatalogDao(database: CatalogDatabase): CatalogDao = database.catalogDao()
}
