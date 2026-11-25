package com.pinyinpeek.camera

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pinyinpeek.dictionary.Word
import com.pinyinpeek.dictionary.WordDatabase
import com.pinyinpeek.ocr.OcrEngine
import com.pinyinpeek.pinyin.PinyinConverter
import com.pinyinpeek.translation.DictionaryEngine
import com.pinyinpeek.translation.EngineSettings
import com.pinyinpeek.translation.GoogleMtEngine
import com.pinyinpeek.translation.HanlpEngine
import com.pinyinpeek.translation.TranslationEngine
import com.pinyinpeek.translation.TranslationEngineType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class AppState(
    val isPreview: Boolean = true,
    val isProcessing: Boolean = false,
    val capturedBitmap: Bitmap? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val hasSelection: Boolean = false,
    val selLeft: Float = 0f, val selTop: Float = 0f,
    val selRight: Float = 0f, val selBottom: Float = 0f,
    val recognizedText: String = "",
    val charPinyins: List<PinyinConverter.PinyinChar> = emptyList(),
    val translation: String = "",
    val saved: Boolean = false,
    val engineType: TranslationEngineType = TranslationEngineType.GOOGLE_MT,
    val engineError: String? = null,
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WordDatabase.get(application)
    private val settings = EngineSettings(application)

    private val engines: Map<TranslationEngineType, TranslationEngine> by lazy {
        val dict = DictionaryEngine(application)
        mapOf(
            TranslationEngineType.DICTIONARY to dict,
            TranslationEngineType.GOOGLE_MT to GoogleMtEngine(),
            TranslationEngineType.HANLP to HanlpEngine(dict),
        )
    }

    private val _state = MutableStateFlow(AppState(engineType = settings.engineType))
    val state = _state.asStateFlow()

    private val _showDict = MutableStateFlow(false)
    val showDict = _showDict.asStateFlow()

    private fun currentEngine(): TranslationEngine = engines[_state.value.engineType]!!

    fun setEngine(type: TranslationEngineType) {
        settings.engineType = type
        _state.value = _state.value.copy(engineType = type)
        Log.d("PinyinPeek", "engine switched to " + type.id)
        // Re-translate the current selection with the new engine, if any.
        val text = _state.value.recognizedText
        if (text.isNotEmpty()) translate(text)
    }

    /** Runs the current engine on [text] and writes the result into state. */
    private var translateJob: Job? = null
    private fun translate(text: String) {
        translateJob?.cancel()
        translateJob = viewModelScope.launch {
            val engine = _state.value.engineType
            val translation = try {
                currentEngine().translate(text)
            } catch (e: Exception) {
                Log.w("PinyinPeek", "engine " + engine.id + " failed: " + e.message)
                ""
            }
            _state.value = _state.value.copy(translation = translation)
        }
    }

    fun captureFrame(imageProxy: ImageProxy) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) { imageProxyToBitmap(imageProxy) }
            if (bitmap == null) return@launch
            _state.value = _state.value.copy(isPreview = false, capturedBitmap = bitmap,
                imageWidth = bitmap.width, imageHeight = bitmap.height)
        }
    }

    fun onSelection(vl: Float, vt: Float, vr: Float, vb: Float, vw: Int, vh: Int) {
        val s = _state.value; val bmp = s.capturedBitmap ?: return
        val scale = minOf(vw.toFloat() / s.imageWidth, vh.toFloat() / s.imageHeight)
        val ox = (vw - s.imageWidth * scale) / 2f; val oy = (vh - s.imageHeight * scale) / 2f
        val il = ((vl - ox) / scale).coerceIn(0f, s.imageWidth.toFloat())
        val it = ((vt - oy) / scale).coerceIn(0f, s.imageHeight.toFloat())
        val ir = ((vr - ox) / scale).coerceIn(0f, s.imageWidth.toFloat())
        val ib = ((vb - oy) / scale).coerceIn(0f, s.imageHeight.toFloat())
        _state.value = s.copy(hasSelection = true, selLeft = il, selTop = it, selRight = ir, selBottom = ib, isProcessing = true)

        viewModelScope.launch {
            try {
                val cropped = withContext(Dispatchers.Default) {
                    Bitmap.createBitmap(bmp, il.toInt(), it.toInt(),
                        (ir - il).toInt().coerceAtLeast(1), (ib - it).toInt().coerceAtLeast(1))
                }
                val result = OcrEngine.recognize(cropped)
                val text = result.text
                if (text.isBlank()) {
                    _state.value = s.copy(isProcessing = false, hasSelection = true,
                        selLeft = il, selTop = it, selRight = ir, selBottom = ib)
                    return@launch
                }
                val charPinyins = PinyinConverter.toPinyinChars(text)
                val exists = db.wordDao().exists(text)
                _state.value = s.copy(isProcessing = false, hasSelection = true,
                    selLeft = il, selTop = it, selRight = ir, selBottom = ib,
                    recognizedText = text, charPinyins = charPinyins,
                    saved = exists)
                // Translation fills in asynchronously; re-runs on engine switch.
                translate(text)
            } catch (e: Exception) {
                Log.e("PinyinPeek", "onSelection failed: " + e.message)
                _state.value = s.copy(isProcessing = false)
            }
        }
    }

    fun reselect() {
        val s = _state.value
        _state.value = s.copy(hasSelection = false,
            recognizedText = "", charPinyins = emptyList(), translation = "", saved = false)
    }

    fun saveWord() {
        val s = _state.value; if (s.recognizedText.isBlank() || s.saved) return
        viewModelScope.launch {
            val fp = s.charPinyins.joinToString(" ") { it.pinyin }
            db.wordDao().insert(Word(chinese = s.recognizedText, pinyin = fp,
                pinyinPlain = PinyinConverter.stripTones(fp), english = s.translation))
            _state.value = s.copy(saved = true)
        }
    }

    fun openInPleco() { _state.value.recognizedText.ifBlank { return }.also { com.pinyinpeek.pleco.PlecoIntegration.lookUp(getApplication(), it) } }
    fun retake() { _state.value = AppState(engineType = _state.value.engineType) }
    fun toggleDict() { _showDict.value = !_showDict.value }

    private fun imageProxyToBitmap(proxy: ImageProxy): Bitmap? {
        val rot = proxy.imageInfo.rotationDegrees
        return try {
            val raw = if (proxy.format == ImageFormat.JPEG) {
                val buf = proxy.planes[0].buffer; val bytes = ByteArray(buf.remaining()).also { buf.get(it) }; proxy.close()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else {
                val y = proxy.planes[0]; val u = proxy.planes[1]; val v = proxy.planes[2]
                val yb = y.buffer; val ub = u.buffer; val vb = v.buffer
                val ys = yb.remaining(); val us = ub.remaining(); val vs = vb.remaining()
                val nv21 = ByteArray(ys + us + vs); yb.get(nv21, 0, ys)
                if (u.pixelStride == 1) { ub.get(nv21, ys, us); vb.get(nv21, ys + us, vs) }
                else for (r in 0 until proxy.height / 2) for (c in 0 until proxy.width / 2) {
                    val idx = r * u.rowStride + c * u.pixelStride
                    nv21[ys + r * proxy.width + c * 2] = vb.get(idx)
                    nv21[ys + r * proxy.width + c * 2 + 1] = ub.get(idx)
                }
                proxy.close()
                val yuv = android.graphics.YuvImage(nv21, ImageFormat.NV21, proxy.width, proxy.height, null)
                val out = ByteArrayOutputStream()
                yuv.compressToJpeg(android.graphics.Rect(0, 0, proxy.width, proxy.height), 90, out)
                BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
            } ?: return null
            if (rot == 0) raw else {
                val m = Matrix().apply { postRotate(rot.toFloat()) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { raw.recycle() }
            }
        } catch (e: Exception) { try { proxy.close() } catch (_: Exception) {}; null }
    }

    override fun onCleared() {
        super.onCleared()
        engines.values.forEach { it.close() }
    }
}