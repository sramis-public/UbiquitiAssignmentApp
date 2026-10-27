package com.example.ubiquititestassignment.ui.catalog

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ubiquititestassignment.R
import com.example.ubiquititestassignment.data.catalog.CatalogProduct
import com.example.ubiquititestassignment.ui.theme.CatalogMuted
import com.example.ubiquititestassignment.ui.theme.UbiquitiTestAssignmentTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProductDetailsScreen(
    productId: String,
    state: CatalogUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    // Resolve from the complete snapshot, independently of any selected line filters.
    val catalog = state.catalog as? CatalogAvailability.Available
    val product = catalog?.products?.firstOrNull { it.id == productId }
    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = product?.name ?: stringResource(R.string.product_details_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (product != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding),
                contentPadding = PaddingValues(
                    start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
            ) {
                item {
                    Surface(shape = MaterialTheme.shapes.large) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                                ProductImage(
                                    product,
                                    imageWidth = maxWidth,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Column(modifier = Modifier.padding(top = 24.dp)) {
                                ProductDetailRow(
                                    label = stringResource(R.string.product_name_label),
                                    values = listOf(product.name),
                                )
                                ProductDetailRow(
                                    label = stringResource(R.string.product_line_label),
                                    values = listOf(product.lineName?.takeIf { it.isNotBlank() }
                                        ?: stringResource(R.string.product_detail_unknown)),
                                )
                                ProductDetailRow(
                                    label = stringResource(R.string.product_short_names_label),
                                    values = product.shortnames,
                                )
                            }
                        }
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
                if (catalog == null && state.isWaitingForCatalog()) {
                    CatalogLoading()
                } else {
                    CatalogMessage(
                        title = stringResource(R.string.product_unavailable),
                        message = stringResource(
                            if (catalog == null) R.string.catalog_unavailable_description
                            else R.string.product_removed_description,
                        ),
                        actionLabel = if (catalog == null) stringResource(R.string.catalog_retry) else null,
                        onAction = onRetry,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductDetailRow(label: String, values: List<String>) {
    val colors = MaterialTheme.colorScheme
    val valueColor = if (colors.surface.luminance() > 0.5f) CatalogMuted else colors.onSurfaceVariant
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val displayedValues = values.ifEmpty {
                    listOf(stringResource(R.string.product_detail_unknown))
                }
                displayedValues.forEach { value ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        color = valueColor,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Preview(name = "Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProductDetailsPreview() {
    val product = CatalogProduct("camera", "Camera G6 Bullet", "unifi-protect", "UniFi Protect", null)
    UbiquitiTestAssignmentTheme {
        ProductDetailsScreen(
            product.id,
            CatalogUiState(catalog = CatalogAvailability.Available(listOf(product))),
            onBack = {},
            onRetry = {},
        )
    }
}
