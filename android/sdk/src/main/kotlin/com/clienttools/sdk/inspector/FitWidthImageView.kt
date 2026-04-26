package com.clienttools.sdk.inspector

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class FitWidthImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    private var bitmapWidth = 0
    private var bitmapHeight = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (bitmapWidth > 0 && bitmapHeight > 0) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = (bitmapHeight.toLong() * width / bitmapWidth).toInt()
            setMeasuredDimension(width, height)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    fun setImageBitmapFitWidth(bitmap: Bitmap) {
        bitmapWidth = bitmap.width
        bitmapHeight = bitmap.height
        setImageBitmap(bitmap)
        requestLayout()
    }
}
