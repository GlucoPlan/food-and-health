package com.glucoplan.foodhealth.ui.pans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.glucoplan.foodhealth.data.pan.PanPhotos

/** Миниатюра фото кастрюли; без фото или пока оно не скачано — значок. */
@Composable
fun PanThumbnail(photo: String?, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(8.dp)
    // Фото могло прийти с другого телефона уже после первого показа — перечитываем по счётчику
    val revision by PanPhotos.revision.collectAsState()
    val file = remember(photo, revision) { photo?.let { PanPhotos.file(context, it) }?.takeIf { it.isFile } }
    if (file != null) {
        key(revision) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier.size(size).clip(shape),
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.SoupKitchen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
