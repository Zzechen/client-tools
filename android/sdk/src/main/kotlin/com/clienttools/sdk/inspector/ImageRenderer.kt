package com.clienttools.sdk.inspector

import android.graphics.BitmapFactory
import android.util.TypedValue
import android.view.View
import com.clienttools.sdk.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageRenderer(rootView: View, private val viewModel: InspectorViewModel) {

    private val imageView: FitWidthImageView = rootView.findViewById(R.id.overlay_imageview)
    private var job: Job? = null

    fun startObserving(scope: CoroutineScope) {
        job = scope.launch {
            launch {
                viewModel.image.map { it.isVisible }.collect { visible ->
                    imageView.visibility = if (visible) View.VISIBLE else View.GONE
                }
            }
            launch {
                viewModel.image.map { it.currentImage }.collect { imgInfo ->
                    if (imgInfo != null) loadImage(imgInfo.filePath)
                }
            }
            launch {
                viewModel.image.map { it.opacity }.collect { alpha ->
                    imageView.alpha = alpha
                }
            }
            launch {
                viewModel.image.map { it.offsetX to it.offsetY }.collect { (x, y) ->
                    imageView.translationX = dpToPx(x)
                    imageView.translationY = dpToPx(y)
                }
            }
        }
    }

    private suspend fun loadImage(filePath: String) {
        val screenWidth = imageView.context.resources.displayMetrics.widthPixels
        val bitmap = withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, opts)
            val sampleSize = if (opts.outWidth > 0) {
                maxOf(1, Math.ceil(opts.outWidth.toDouble() / screenWidth).toInt())
            } else 1
            BitmapFactory.decodeFile(filePath, BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            })
        }
        if (bitmap != null) imageView.setImageBitmapFitWidth(bitmap)
    }

    fun stopObserving() {
        job?.cancel()
        job = null
    }

    private fun dpToPx(dp: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        imageView.context.resources.displayMetrics
    )
}
