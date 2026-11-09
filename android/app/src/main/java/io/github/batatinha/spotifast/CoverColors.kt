package io.github.batatinha.spotifast

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import io.github.batatinha.spotifast.ui.CoverColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The vivid and dominant colours of a cover, from a small copy of it. */
suspend fun coverColors(context: Context, url: String): CoverColors? = withContext(Dispatchers.Default) {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(96)
        .allowHardware(false)
        .build()
    val bitmap = ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        ?: return@withContext null
    val palette = Palette.from(bitmap).maximumColorCount(16).generate()
    val dominant = palette.dominantSwatch?.rgb ?: return@withContext null
    val vivid = (palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.mutedSwatch)?.rgb ?: dominant
    CoverColors(vivid = Color(vivid), dominant = Color(dominant))
}
