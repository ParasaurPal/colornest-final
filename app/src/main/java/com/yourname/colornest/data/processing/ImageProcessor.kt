package com.yourname.colornest.data.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import com.yourname.colornest.data.model.Page
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ProcessedImageResult(
    val imagePath: String,
    val thumbnailPath: String?,
    val whiteBackgroundProcessed: Boolean,
    val whiteBackgroundVersion: Int
)

object ImageProcessor {
    const val WHITE_BACKGROUND_VERSION = 3
    private const val MAX_NEUTRAL_CHANNEL_SPREAD = 24
    private const val VERY_DARK_MAX_CHANNEL = 24
    private const val MIN_ALPHA = 12
    private const val THUMB_MAX_SIZE = 320

    fun needsWhiteBackgroundReprocessing(page: Page): Boolean {
        return !page.isPreviewOnly &&
            page.imageUrl.isNotBlank() &&
            (!page.whiteBackgroundProcessed || page.whiteBackgroundVersion < WHITE_BACKGROUND_VERSION)
    }

    /**
     * Converts coloring-page artwork into black line art with transparency.
     * Neutral black/gray pixels become black ink with alpha based on darkness;
     * colored pixels are discarded. Preserves antialiased edges while removing
     * white paper and colored backgrounds.
     */
    fun removeWhiteBackground(source: Bitmap): Bitmap {
        val maxDimension = max(source.width, source.height)
        val working = if (maxDimension > 2000) {
            val scale = 2000f / maxDimension
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).roundToInt().coerceAtLeast(1),
                (source.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            source
        }

        val width = working.width
        val height = working.height
        val pixels = IntArray(width * height)
        working.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val oldAlpha = AndroidColor.alpha(pixel)
            if (oldAlpha == 0) continue

            val red = AndroidColor.red(pixel)
            val green = AndroidColor.green(pixel)
            val blue = AndroidColor.blue(pixel)
            val maxChannel = max(red, max(green, blue))
            val minChannel = min(red, min(green, blue))
            val channelSpread = maxChannel - minChannel

            if (channelSpread > MAX_NEUTRAL_CHANNEL_SPREAD && maxChannel > VERY_DARK_MAX_CHANNEL) {
                pixels[i] = 0
                continue
            }

            val luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue
            val newAlpha = (oldAlpha * (1.0 - luminance / 255.0)).roundToInt().coerceIn(0, 255)
            pixels[i] = if (newAlpha < MIN_ALPHA) 0 else AndroidColor.argb(newAlpha, 0, 0, 0)
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        if (working !== source) working.recycle()
        return result
    }

    fun createThumbnailFromBitmap(
        source: Bitmap,
        thumbsDir: File,
        baseName: String,
        maxSize: Int = THUMB_MAX_SIZE
    ): String? {
        return runCatching {
            if (!thumbsDir.exists()) thumbsDir.mkdirs()
            val width = source.width
            val height = source.height
            if (width <= 0 || height <= 0) return null

            val scale = min(1f, maxSize.toFloat() / max(width, height).toFloat())
            val thumbWidth = (width * scale).roundToInt().coerceAtLeast(1)
            val thumbHeight = (height * scale).roundToInt().coerceAtLeast(1)

            val thumbBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(source, thumbWidth, thumbHeight, true)
            } else {
                source
            }

            val file = File(thumbsDir, "${baseName}_thumb_${System.currentTimeMillis()}_${(100..999).random()}.png")
            FileOutputStream(file).use { out ->
                thumbBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (thumbBitmap !== source) thumbBitmap.recycle()
            file.absolutePath
        }.getOrNull()
    }

    fun tryCreateThumbnailFromFile(
        sourceFile: File,
        thumbsDir: File,
        baseName: String = "page",
        maxSize: Int = THUMB_MAX_SIZE
    ): String? {
        if (!sourceFile.exists() || !sourceFile.isFile) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var inSampleSize = 1
            while ((bounds.outWidth / inSampleSize) > maxSize * 2 &&
                (bounds.outHeight / inSampleSize) > maxSize * 2
            ) {
                inSampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val decoded = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOpts) ?: return null
            val path = createThumbnailFromBitmap(decoded, thumbsDir, baseName, maxSize)
            decoded.recycle()
            path
        }.getOrNull()
    }

    fun processBitmapAndSave(
        bitmap: Bitmap,
        imagesDir: File,
        thumbsDir: File,
        baseName: String,
        removeWhitePaper: Boolean
    ): ProcessedImageResult {
        if (!imagesDir.exists()) imagesDir.mkdirs()
        if (removeWhitePaper) {
            val transparent = removeWhiteBackground(bitmap)
            val file = File(imagesDir, "${baseName}_${System.currentTimeMillis()}_${(100..999).random()}.png")
            FileOutputStream(file).use { out ->
                transparent.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val thumbPath = createThumbnailFromBitmap(transparent, thumbsDir, baseName)
            transparent.recycle()
            return ProcessedImageResult(
                imagePath = file.absolutePath,
                thumbnailPath = thumbPath,
                whiteBackgroundProcessed = true,
                whiteBackgroundVersion = WHITE_BACKGROUND_VERSION
            )
        } else {
            val width = bitmap.width
            val height = bitmap.height
            val maxDim = max(width, height)
            val scaled = if (maxDim > 2000) {
                val scale = 2000f / maxDim
                val matrix = Matrix().apply { postScale(scale, scale) }
                Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
            } else {
                bitmap
            }
            val file = File(imagesDir, "${baseName}_${System.currentTimeMillis()}_${(100..999).random()}.png")
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val thumbPath = createThumbnailFromBitmap(scaled, thumbsDir, baseName)
            if (scaled !== bitmap) scaled.recycle()
            return ProcessedImageResult(
                imagePath = file.absolutePath,
                thumbnailPath = thumbPath,
                whiteBackgroundProcessed = false,
                whiteBackgroundVersion = 0
            )
        }
    }

    fun reprocessExistingFile(
        sourceFile: File,
        imagesDir: File,
        thumbsDir: File
    ): ProcessedImageResult? {
        if (!sourceFile.exists() || !sourceFile.isFile) return null
        val decoded = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return null
        return try {
            val result = processBitmapAndSave(
                bitmap = decoded,
                imagesDir = imagesDir,
                thumbsDir = thumbsDir,
                baseName = "reproc",
                removeWhitePaper = true
            )
            result
        } finally {
            decoded.recycle()
        }
    }
}
