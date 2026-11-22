package com.bunkwise.duplicatefilefinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bunkwise.duplicatefilefinder.core.domain.model.FileCategory
import com.bunkwise.duplicatefilefinder.presentation.common.CategoryUi
import java.io.File

/**
 * A file's preview: a decoded thumbnail for photos and videos (sized to the slot, so a 5k-group
 * list never decodes full-size bitmaps), the type icon for everything else.
 */
@Composable
fun Thumb(path: String, category: FileCategory, size: Dp, modifier: Modifier = Modifier) {
    val n = Df.c
    val shape = RoundedCornerShape(10.dp)
    val box = modifier.size(size).clip(shape).background(n.glass2)
    if (!CategoryUi.isVisualMedia(category)) {
        Box(box, contentAlignment = Alignment.Center) {
            Icon(CategoryUi.vector(category), null, tint = n.mu, modifier = Modifier.size(size * 0.45f))
        }
        return
    }
    val px = with(LocalDensity.current) { size.roundToPx() }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(File(path)).size(px).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = box,
    )
}
