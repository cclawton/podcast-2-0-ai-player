package com.podcast.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.util.TestTags

/**
 * A compact podcast thumbnail that shows only the podcast image.
 * Used in the Library subscriptions grid for a clean, image-focused layout.
 */
@Composable
fun PodcastThumbnail(
    podcast: Podcast,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(TestTags.PODCAST_ITEM),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        AsyncImage(
            model = podcast.imageUrl,
            contentDescription = podcast.customName ?: podcast.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .testTag(TestTags.PODCAST_IMAGE),
            contentScale = ContentScale.Crop
        )
    }
}
