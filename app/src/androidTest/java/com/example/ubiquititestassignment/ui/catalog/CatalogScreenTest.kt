package com.example.ubiquititestassignment.ui.catalog

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ubiquititestassignment.data.catalog.CatalogProduct
import com.example.ubiquititestassignment.data.catalog.CatalogRefreshFailure
import com.example.ubiquititestassignment.data.catalog.CatalogRefreshState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fixture-driven rendering and interaction checks; no repository or image requests. */
@RunWith(AndroidJUnit4::class)
class CatalogScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun categoryTabsRenderProductsByDeviceType() {
        var state by mutableStateOf(availableState(listOf(accessPoint, camera, sensor)))
        val selected = mutableListOf<CatalogCategory>()
        compose.setContent {
            MaterialTheme {
                CatalogContent(
                    state = state,
                    onCategorySelected = {
                        selected += it
                        state = state.copy(
                            filters = state.filters.copy(category = it),
                            filteredProducts = listOf(accessPoint, camera, sensor).filter(it::matches),
                        )
                    },
                    onLineToggled = {},
                    onRefresh = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("All").assertIsSelected()
        compose.onNodeWithText("Cameras").performClick().assertIsSelected()
        compose.onNodeWithText(camera.name).assertIsDisplayed()
        compose.onNodeWithText(accessPoint.name).assertDoesNotExist()
        compose.onNodeWithText(sensor.name).assertDoesNotExist()
        compose.onNodeWithText("IoT").performClick().assertIsSelected()
        compose.onNodeWithText(sensor.name).assertIsDisplayed()
        compose.onNodeWithText(accessPoint.name).assertDoesNotExist()
        compose.onNodeWithText(camera.name).assertDoesNotExist()
        compose.onNodeWithText("All").performClick().assertIsSelected()
        assertBothProductsVisible()
        compose.runOnIdle {
            assertEquals(
                listOf(CatalogCategory.Cameras, CatalogCategory.IoT, CatalogCategory.All),
                selected,
            )
        }
    }

    @Test
    fun openingProductUsesCorrectIdAndBackReturnsToList() {
        render(availableState())

        compose.onNodeWithText(camera.name).performClick()
        compose.onNodeWithContentDescription("Navigate back").assertIsDisplayed()
        compose.onNodeWithText(camera.name).assertIsDisplayed()
        compose.onNodeWithText(accessPoint.name).assertDoesNotExist()

        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithText("All").assertIsSelected()
        compose.onNodeWithContentDescription("Navigate back").assertDoesNotExist()
        assertBothProductsVisible()
    }

    @Test
    fun detailsSurviveSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                CatalogContent(
                    state = availableState(),
                    onCategorySelected = {},
                    onLineToggled = {},
                    onRefresh = {},
                    onRetry = {},
                )
            }
        }
        compose.onNodeWithText(camera.name).performClick()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithContentDescription("Navigate back").assertIsDisplayed()
        compose.onNodeWithText(camera.name).assertIsDisplayed()
        compose.onNodeWithText(accessPoint.name).assertDoesNotExist()
        compose.onNodeWithContentDescription("Navigate back").performClick()
        assertBothProductsVisible()
    }

    @Test
    fun lineFilterInvokesTheMatchingLineCallback() {
        val toggled = mutableListOf<CatalogLine>()
        render(availableState(), onLineToggled = { toggled += it })

        compose.onNodeWithContentDescription("Filter products").performClick()
        compose.onNode(hasText("UniFi Protect") and hasAnyAncestor(isPopup())).performClick()

        compose.runOnIdle { assertEquals(listOf(CatalogLine.UniFiProtect), toggled) }
    }

    @Test
    fun emptyCatalogShowsEmptyMessageAndCanRefresh() {
        var refreshCount = 0
        render(
            availableState(emptyList()),
            onRefresh = { refreshCount++ },
        )

        compose.onNodeWithText("No products found").assertIsDisplayed()
        compose.onNodeWithText(camera.name).assertDoesNotExist()
        compose.onNodeWithText("Refresh").performClick()

        compose.runOnIdle { assertEquals(1, refreshCount) }
    }

    @Test
    fun pullToRefreshPreservesFiltersAndRowsAndTracksCompletion() {
        var state by mutableStateOf(availableState(listOf(camera)).copy(
            filters = CatalogFilters(
                category = CatalogCategory.Cameras,
                lines = setOf(CatalogLine.UniFiProtect),
            ),
        ))
        var refreshCount = 0
        compose.setContent {
            MaterialTheme {
                CatalogContent(
                    state = state,
                    onCategorySelected = {},
                    onLineToggled = {},
                    onRefresh = {
                        refreshCount++
                        state = state.copy(refresh = CatalogRefreshState.Refreshing)
                    },
                    onRetry = {},
                )
            }
        }

        pullToRefresh()

        compose.onNodeWithText("Cameras").assertIsSelected()
        compose.onNodeWithText(camera.name).assertIsDisplayed()
        val spinner = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)
        compose.onNode(spinner).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, refreshCount)
            assertEquals(setOf(CatalogLine.UniFiProtect), state.filters.lines)
        }

        // Another pull during the same request must not invoke refresh again.
        pullToRefresh()
        compose.runOnIdle { assertEquals(1, refreshCount) }

        compose.runOnIdle { state = state.copy(refresh = CatalogRefreshState.Updated) }
        compose.onNode(spinner).assertDoesNotExist()
        compose.onNodeWithText(camera.name).assertIsDisplayed()

        pullToRefresh()
        compose.runOnIdle {
            assertEquals(2, refreshCount)
            state = state.copy(refresh = CatalogRefreshState.Failed(CatalogRefreshFailure.Network))
        }
        compose.onNode(spinner).assertDoesNotExist()
        compose.onNodeWithText(camera.name).assertIsDisplayed()
        compose.onNodeWithText(
            "Products could not be refreshed. Showing the saved catalog.",
        ).assertIsDisplayed()
    }

    @Test
    fun emptyCatalogCanBePulledToRefresh() {
        var refreshCount = 0
        render(availableState(emptyList()), onRefresh = { refreshCount++ })

        pullToRefresh()

        compose.onNodeWithText("No products found").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, refreshCount) }
    }

    @Test
    fun filteredOutCatalogCanBePulledToRefresh() {
        var refreshCount = 0
        render(
            availableState(listOf(accessPoint)).copy(
                filters = CatalogFilters(category = CatalogCategory.Cameras),
                filteredProducts = emptyList(),
            ),
            onRefresh = { refreshCount++ },
        )

        pullToRefresh()

        compose.onNodeWithText("Cameras").assertIsSelected()
        compose.runOnIdle { assertEquals(1, refreshCount) }
    }

    @Test
    fun pullToRefreshRestartsFailedObservation() {
        var refreshCount = 0
        var retryCount = 0
        render(
            availableState().copy(observationFailed = true),
            onRefresh = { refreshCount++ },
            onRetry = { retryCount++ },
        )

        pullToRefresh()

        assertBothProductsVisible()
        compose.runOnIdle {
            assertEquals(0, refreshCount)
            assertEquals(1, retryCount)
        }
    }

    @Test
    fun categoryWithoutMatchesShowsFilterMessageForNonemptyCatalog() {
        render(availableState(listOf(accessPoint)).copy(
            filters = CatalogFilters(category = CatalogCategory.Cameras),
            filteredProducts = emptyList(),
        ))

        compose.onNodeWithText("Try changing the category or product-line filters.").assertIsDisplayed()
        compose.onNodeWithText("The catalog is empty. Refresh to check for products.").assertDoesNotExist()
    }

    @Test
    fun unavailableCatalogShowsRetryAndInvokesRecovery() {
        var retryCount = 0
        render(
            CatalogUiState(
                catalog = CatalogAvailability.Unavailable,
                refresh = CatalogRefreshState.Failed(CatalogRefreshFailure.Network),
            ),
            onRetry = { retryCount++ },
        )

        compose.onNodeWithText("Retry").performClick()

        compose.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun failedRefreshKeepsSavedRowsAndOffersRetry() {
        var retryCount = 0
        render(
            availableState().copy(
                refresh = CatalogRefreshState.Failed(CatalogRefreshFailure.Network),
            ),
            onRetry = { retryCount++ },
        )

        assertBothProductsVisible()
        compose.onNodeWithText(
            "Products could not be refreshed. Showing the saved catalog.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()

        compose.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun initialLoadingShowsLoadingMessageWithoutEmptyOrErrorState() {
        render(CatalogUiState())

        compose.onNodeWithText("Loading products").assertIsDisplayed()
        compose.onNodeWithText("No products found").assertDoesNotExist()
        compose.onNodeWithText("Catalog unavailable").assertDoesNotExist()
        compose.onNodeWithText("Retry").assertDoesNotExist()
        compose.onNodeWithText(camera.name).assertDoesNotExist()
    }

    @Test
    fun missingProductImageShowsFallbackInDetails() {
        render(availableState())

        compose.onNodeWithText(camera.name).performClick()

        compose.onNodeWithText("No image").assertIsDisplayed()
        compose.onNodeWithText(camera.name).assertIsDisplayed()
    }

    @Test
    fun headerStaysVisibleWhileScrollingTheList() {
        val products = List(100) { index ->
            camera.copy(id = "product-$index", name = "Product $index")
        }
        var refreshCount = 0
        render(availableState(products), onRefresh = { refreshCount++ })

        compose.onNodeWithText("All").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performTouchInput {
            swipeUp(durationMillis = 600)
        }
        compose.onNodeWithText("All").assertIsDisplayed()
        compose.onNodeWithContentDescription("Filter products").assertIsDisplayed()
        compose.onNodeWithText("Product 0").assertIsNotDisplayed()

        compose.onNode(hasScrollAction()).performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 200f, durationMillis = 500)
        }
        compose.onNodeWithText("All").assertIsDisplayed()
        compose.onNodeWithContentDescription("Filter products").assertIsDisplayed()
        compose.onNodeWithText("Product 0").assertIsNotDisplayed()
        compose.runOnIdle { assertEquals(0, refreshCount) }
    }

    private fun pullToRefresh() {
        compose.onNode(hasScrollAction()).performTouchInput { swipeDown(durationMillis = 600) }
    }

    private fun render(
        state: CatalogUiState,
        onLineToggled: (CatalogLine) -> Unit = {},
        onRefresh: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme {
                CatalogContent(
                    state = state,
                    onCategorySelected = {},
                    onLineToggled = onLineToggled,
                    onRefresh = onRefresh,
                    onRetry = onRetry,
                )
            }
        }
    }

    private fun assertBothProductsVisible() {
        compose.onNodeWithText(accessPoint.name).assertIsDisplayed()
        compose.onNodeWithText(camera.name).assertIsDisplayed()
    }

    private fun availableState(products: List<CatalogProduct> = listOf(accessPoint, camera)) =
        CatalogUiState(
            catalog = CatalogAvailability.Available(products),
            filteredProducts = products,
        )

    private val accessPoint = CatalogProduct(
        id = "network-product",
        name = "Access Point fixture",
        lineId = CatalogLine.UniFi.id,
        lineName = "UniFi",
        defaultImageId = null,
        deviceType = "access-point",
    )

    private val sensor = CatalogProduct(
        id = "sensor-product",
        name = "Sensor fixture",
        lineId = CatalogLine.UniFiProtect.id,
        lineName = "UniFi Protect",
        defaultImageId = null,
        deviceType = "sensor",
    )

    private val camera = CatalogProduct(
        id = "camera-product",
        name = "Camera fixture",
        lineId = CatalogLine.UniFiProtect.id,
        lineName = "UniFi Protect",
        defaultImageId = null,
        deviceType = "camera",
    )
}
