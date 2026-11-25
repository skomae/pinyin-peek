package com.pinyinpeek.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** One recognised block of Chinese text. */
data class TextBlock(
    val text: String,
    /** Bounding box in image pixels. */
    val boundingBox: Rect,
)

/** Result of an OCR pass. */
data class OcrResult(
    val blocks: List<TextBlock>,
    /** Full joined text, same as blocks.map { it.text }.joinToString("") */
    val text: String,
) {
    companion object {
        val EMPTY = OcrResult(emptyList(), "")
    }
}

/** On-device Chinese text recognition via ML Kit. */
object OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /** Recognises Chinese text in [bitmap]. */
    suspend fun recognize(bitmap: Bitmap): OcrResult = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val blocks = visionText.textBlocks.mapNotNull { block ->
                    val box = block.boundingBox ?: return@mapNotNull null
                    val text = block.text?.trim() ?: return@mapNotNull null
                    if (text.isEmpty()) return@mapNotNull null
                    TextBlock(text, box)
                }
                val full = blocks.joinToString("") { it.text }
                cont.resume(OcrResult(blocks, full))
            }
            .addOnFailureListener {
                cont.resume(OcrResult.EMPTY)
            }
    }
}