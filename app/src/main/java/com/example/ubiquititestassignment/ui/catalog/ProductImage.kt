package com.example.ubiquititestassignment.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.ubiquititestassignment.R
import com.example.ubiquititestassignment.data.catalog.CatalogProduct
import com.example.ubiquititestassignment.data.catalog.imageUrl

/** Caller supplies image bounds through [modifier]; images retain their aspect ratio. */
@Composable
fun ProductImage(
    product: CatalogProduct,
    imageWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val imageWidthPx = with(LocalDensity.current) { imageWidth.roundToPx().coerceAtLeast(1) }
    val imageUrl = remember(product.id, product.defaultImageId, imageWidthPx) {
        product.imageUrl(imageWidthPx)
    }
    var status by remember(imageUrl) {
        mutableStateOf(if (imageUrl == null) ImageStatus.Missing else ImageStatus.Loading)
    }
    val currentStatus = status
    val statusText = when (currentStatus) {
        ImageStatus.Missing -> stringResource(R.string.product_image_missing)
        ImageStatus.Failed -> stringResource(R.string.product_image_failed)
        ImageStatus.Loading, ImageStatus.Ready -> null
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = product.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onLoading = { status = ImageStatus.Loading },
                onSuccess = { status = ImageStatus.Ready },
                onError = { status = ImageStatus.Failed },
            )
        }
        when (currentStatus) {
            ImageStatus.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            ImageStatus.Missing, ImageStatus.Failed -> Text(
                text = checkNotNull(statusText),
                modifier = Modifier.padding(4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
            ImageStatus.Ready -> Unit
        }
    }
}

private enum class ImageStatus { Loading, Ready, Missing, Failed }
