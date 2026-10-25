package com.example.ubiquititestassignment.ui.catalog

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Keeps fixture-only UI tests independent of the production startup refresh. */
class CatalogScreenTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        classLoader: ClassLoader,
        className: String,
        context: Context,
    ): Application = super.newApplication(
        classLoader,
        Application::class.java.name,
        context,
    )
}
