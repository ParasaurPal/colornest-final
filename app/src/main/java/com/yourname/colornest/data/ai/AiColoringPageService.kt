package com.yourname.colornest.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.max

/**
 * Uses Gemini image editing to redraw a source photograph/illustration as printable
 * coloring-book line art. This is intentionally separate from the pixel-based
 * transparent-paper processor: the model interprets and redraws the scene.
 */
object AiColoringPageService {
    private const val MODEL_NAME = "gemini-nano-banana-2.1"
    private const val TIMEOUT_MS = 180_000L
    private const val MAX_INPUT_DIMENSION = 1536

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    suspend fun createColoringPage(source: Bitmap): Bitmap = withContext(Dispatchers.IO) {
        require(source.width > 0 && source.height > 0) { "The selected image is empty." }
        val input = resizeForModel(source)
        try {
            val prompt = content {
                image(input)
                text(
                    """
                    Transform the supplied image into a finished, high-quality printable coloring-book page.
                    Create a NEW black-and-white line-art illustration by understanding and carefully redrawing
                    the actual subjects in the reference image. This is an image-to-image illustration task,
                    NOT an edge-detection filter, threshold effect, photo negative, or simple outline trace.

                    Preserve the important subject matter, recognizable features, pose, composition, and spatial
                    relationships from the reference. Simplify photographic detail into attractive, intentional
                    coloring-book shapes. Use clean, confident, smooth black contour lines with varied but
                    consistent line weight. Close shapes wherever practical so every major area can be colored.
                    Include useful interior details and clear separations between overlapping objects.

                    The page must have a pure white background and crisp solid black lines only. No color,
                    gray pixels, gradients, shadows, lighting, halftones, crosshatching, pencil texture,
                    filled black regions except tiny necessary details, borders, captions, labels, watermarks,
                    or extra objects. Do not leave photographic texture. Do not crop off the main subject.
                    Keep the artwork centered and balanced with comfortable white margins, like a professionally
                    illustrated adult coloring book page. Output the finished coloring page image now.
                    """.trimIndent()
                )
            }

            val response = withTimeout(TIMEOUT_MS) { model.generateContent(prompt) }
            response.candidates.firstOrNull()?.content?.parts
                ?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
                ?.image
                ?: throw IllegalStateException(
                    "Gemini did not return a coloring-page image. Please try again with a different photo."
                )
        } finally {
            if (input !== source) input.recycle()
        }
    }

    private fun resizeForModel(source: Bitmap): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= MAX_INPUT_DIMENSION) {
            return if (source.config == Bitmap.Config.ARGB_8888) source
            else source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val scale = MAX_INPUT_DIMENSION.toFloat() / longest.toFloat()
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val resized = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resized)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, null, android.graphics.Rect(0, 0, width, height), null)
        return resized
    }
}
