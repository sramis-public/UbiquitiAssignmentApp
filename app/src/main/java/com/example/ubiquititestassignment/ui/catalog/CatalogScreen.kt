package com.example.ubiquititestassignment.ui.catalog

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ubiquititestassignment.R
import com.example.ubiquititestassignment.data.catalog.CatalogProduct
import com.example.ubiquititestassignment.data.catalog.CatalogRefreshState
import com.example.ubiquititestassignment.ui.theme.UbiquitiTestAssignmentTheme

@Composable
fun CatalogRoute(viewModel: CatalogViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CatalogContent(
        state = state,
        onCategorySelected = viewModel::selectCategory,
        onLineToggled = viewModel::toggleLine,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
    )
}

/** Two destinations share the same catalog; navigation and tab taps never fetch data. */
@Composable
fun CatalogContent(
    state: CatalogUiState,
    onCategorySelected: (CatalogCategory) -> Unit,
    onLineToggled: (CatalogLine) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
) {
    var selectedProductId by rememberSaveable { mutableStateOf<String?>(null) }
    // Keep this outside the destination branch so details/back retains the list position.
    val listState = rememberLazyListState()
    BackHandler(enabled = selectedProductId != null) { selectedProductId = null }

    AnimatedContent(
        targetState = selectedProductId,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val direction = if (targetState != null) {
                AnimatedContentTransitionScope.SlideDirection.Left
            } else {
                AnimatedContentTransitionScope.SlideDirection.Right
            }
            slideIntoContainer(direction, animationSpec = tween(300)) togetherWith
                slideOutOfContainer(direction, animationSpec = tween(300))
        },
        label = "Catalog navigation",
    ) { productId ->
        if (productId == null) {
            CatalogScreen(
                state = state,
                listState = listState,
                onCategorySelected = onCategorySelected,
                onLineToggled = onLineToggled,
                onRefresh = onRefresh,
                onRetry = onRetry,
                onProductSelected = { selectedProductId = it.id },
            )
        } else {
            ProductDetailsScreen(
                productId = productId,
                state = state,
                onBack = { selectedProductId = null },
                onRetry = onRetry,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogScreen(
    state: CatalogUiState,
    listState: LazyListState,
    onCategorySelected: (CatalogCategory) -> Unit,
    onLineToggled: (CatalogLine) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onProductSelected: (CatalogProduct) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    expandedHeight = 48.dp,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    title = {
                        PrimaryTabRow(
                            selectedTabIndex = state.filters.category.ordinal,
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.background,
                        ) {
                            CatalogCategory.entries.forEach { category ->
                                Tab(
                                    selected = state.filters.category == category,
                                    onClick = { onCategorySelected(category) },
                                    text = { Text(category.label()) },
                                    selectedContentColor = MaterialTheme.colorScheme.primary,
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = { LineFilterMenu(state.filters.lines, onLineToggled) },
                )
            }
        },
    ) { innerPadding ->
        if (state.catalog is CatalogAvailability.Available) {
            PullToRefreshBox(
                isRefreshing = state.refresh == CatalogRefreshState.Refreshing,
                onRefresh = if (state.observationFailed) onRetry else onRefresh,
                modifier = Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    if (state.observationFailed || state.refresh is CatalogRefreshState.Failed) {
                        item(key = "catalog-status", contentType = "status") {
                            RecoveryNotice(state.observationFailed, onRetry)
                        }
                    }
                    if (state.filteredProducts.isEmpty()) {
                        item(key = "catalog-empty", contentType = "empty") {
                            CatalogMessage(
                                title = stringResource(R.string.catalog_empty),
                                message = stringResource(
                                    if (state.catalog.products.isEmpty()) R.string.catalog_empty_description
                                    else R.string.catalog_filters_empty_description,
                                ),
                                actionLabel = stringResource(R.string.catalog_refresh),
                                onAction = onRefresh,
                                actionEnabled = state.refresh != CatalogRefreshState.Refreshing,
                            )
                        }
                    }
                    items(state.filteredProducts, key = { it.id }, contentType = { "product" }) { product ->
                        ListItem(
                            headlineContent = {
                                Text(product.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = product.lineName?.let { line ->
                                { Text(line, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            },
                            leadingContent = {
                                ProductImage(product, imageWidth = 48.dp, modifier = Modifier.size(48.dp))
                            },
                            trailingContent = {
                                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
                            },
                            modifier = Modifier.clickable { onProductSelected(product) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isWaitingForCatalog()) {
                    CatalogLoading()
                } else {
                    CatalogMessage(
                        title = stringResource(R.string.catalog_unavailable),
                        message = stringResource(R.string.catalog_unavailable_description),
                        actionLabel = stringResource(R.string.catalog_retry),
                        onAction = onRetry,
                    )
                }
            }
        }
    }
}

@Composable
private fun LineFilterMenu(selected: Set<CatalogLine>, onLineToggled: (CatalogLine) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
        ) {
            Icon(
                painterResource(R.drawable.ic_filter_list),
                contentDescription = stringResource(R.string.catalog_filter),
                tint = if (selected.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CatalogLine.entries.forEach { line ->
                DropdownMenuItem(
                    text = { Text(line.label()) },
                    onClick = { onLineToggled(line) },
                    leadingIcon = { Checkbox(checked = line in selected, onCheckedChange = null) },
                )
            }
        }
    }
}

@Composable
private fun RecoveryNotice(observationFailed: Boolean, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(
                    if (observationFailed) R.string.catalog_observation_failed
                    else R.string.catalog_refresh_failed,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.catalog_retry)) }
        }
    }
}

internal fun CatalogUiState.isWaitingForCatalog(): Boolean =
    !observationFailed && refresh !is CatalogRefreshState.Failed &&
        (catalog == CatalogAvailability.Loading || refresh == CatalogRefreshState.Refreshing)

@Composable
internal fun CatalogLoading() {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.catalog_loading))
    }
}

@Composable
internal fun CatalogMessage(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    actionEnabled: Boolean = true,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            Button(onClick = onAction, enabled = actionEnabled) { Text(actionLabel) }
        }
    }
}

@Composable
private fun CatalogCategory.label(): String = stringResource(
    when (this) {
        CatalogCategory.All -> R.string.catalog_all
        CatalogCategory.Cameras -> R.string.catalog_cameras
        CatalogCategory.IoT -> R.string.catalog_iot
    },
)

@Composable
private fun CatalogLine.label(): String = stringResource(
    when (this) {
        CatalogLine.UniFi -> R.string.catalog_line_unifi
        CatalogLine.UniFiLte -> R.string.catalog_line_lte
        CatalogLine.UniFiProtect -> R.string.catalog_line_protect
        CatalogLine.UniFiAccess -> R.string.catalog_line_access
    },
)

@Preview(name = "Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CatalogScreenPreview() {
    val products = listOf(
        CatalogProduct("camera", "Camera G6 Bullet", "unifi-protect", "UniFi Protect", null),
        CatalogProduct("sensor", "Protect Sensor", "unifi-protect", "UniFi Protect", null),
        CatalogProduct("access", "Access Reader Pro", "unifi-access", "UniFi Access", null),
    )
    UbiquitiTestAssignmentTheme {
        CatalogContent(
            state = CatalogUiState(
                catalog = CatalogAvailability.Available(products),
                filteredProducts = products,
            ),
            onCategorySelected = {},
            onLineToggled = {},
            onRefresh = {},
            onRetry = {},
        )
    }
}
