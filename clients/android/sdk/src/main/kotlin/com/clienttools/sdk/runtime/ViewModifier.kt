package com.clienttools.sdk.runtime

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.clienttools.sdk.ClientToolsSDK
import com.clienttools.sdk.proto.ViewProps

object ViewModifier {
    fun apply(viewId: String, props: ViewProps): Boolean {
        val views = ViewTreeTraversal.findViewById(viewId)
        return if (views.isEmpty()) false else {
            views.forEach { view ->
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    modify(view, props)
                } else {
                    val activity = ClientToolsSDK.getCurrentActivity()
                    activity?.runOnUiThread { modify(view, props) }
                }
            }
            true
        }
    }

    fun click(viewId: String): Boolean {
        val views = ViewTreeTraversal.findViewById(viewId)
        return if (views.isEmpty()) false else {
            views.forEach { view ->
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    view.performClick()
                } else {
                    val activity = ClientToolsSDK.getCurrentActivity()
                    activity?.runOnUiThread { view.performClick() }
                }
            }
            true
        }
    }

    fun scroll(viewId: String, dxDp: Float, dyDp: Float): Boolean {
        val views = ViewTreeTraversal.findViewById(viewId)
        return if (views.isEmpty()) false else {
            views.forEach { view ->
                val density = view.context.resources.displayMetrics.density
                val dxPx = (dxDp * density).toInt()
                val dyPx = (dyDp * density).toInt()
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    view.scrollBy(dxPx, dyPx)
                } else {
                    val activity = ClientToolsSDK.getCurrentActivity()
                    activity?.runOnUiThread { view.scrollBy(dxPx, dyPx) }
                }
            }
            true
        }
    }

    private fun resolveDimension(value: String?, density: Float): Int? {
        if (value == null) return null
        if (value == "wrap_content") return ViewGroup.LayoutParams.WRAP_CONTENT
        val dp = value.toFloatOrNull() ?: return null
        return (dp * density).toInt()
    }

    private fun modify(view: View, props: ViewProps) {
        val density = view.resources.displayMetrics.density
        val dpToPx = { dp: Float -> (dp * density).toInt() }

        val layoutParams = view.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        if (layoutParams is ViewGroup.MarginLayoutParams) {
            val top = if (props.hasMarginTopDiffDp()) layoutParams.topMargin + dpToPx(props.marginTopDiffDp.value) else layoutParams.topMargin
            val bottom = if (props.hasMarginBottomDiffDp()) layoutParams.bottomMargin + dpToPx(props.marginBottomDiffDp.value) else layoutParams.bottomMargin
            val left = if (props.hasMarginLeftDiffDp()) layoutParams.leftMargin + dpToPx(props.marginLeftDiffDp.value) else layoutParams.leftMargin
            val right = if (props.hasMarginRightDiffDp()) layoutParams.rightMargin + dpToPx(props.marginRightDiffDp.value) else layoutParams.rightMargin
            layoutParams.setMargins(left, top, right, bottom)
        }

        if (props.hasWidthDp()) resolveDimension(props.widthDp.value, density)?.let { layoutParams.width = it }
        if (props.hasHeightDp()) resolveDimension(props.heightDp.value, density)?.let { layoutParams.height = it }

        view.layoutParams = layoutParams

        if (props.hasPaddingTopDiffDp()) {
            val extra = dpToPx(props.paddingTopDiffDp.value)
            view.setPadding(view.paddingLeft, view.paddingTop + extra, view.paddingRight, view.paddingBottom)
        }
        if (props.hasPaddingBottomDiffDp()) {
            val extra = dpToPx(props.paddingBottomDiffDp.value)
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom + extra)
        }
        if (props.hasPaddingLeftDiffDp()) {
            val extra = dpToPx(props.paddingLeftDiffDp.value)
            view.setPadding(view.paddingLeft + extra, view.paddingTop, view.paddingRight, view.paddingBottom)
        }
        if (props.hasPaddingRightDiffDp()) {
            val extra = dpToPx(props.paddingRightDiffDp.value)
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight + extra, view.paddingBottom)
        }

        if (view is TextView) {
            if (props.hasLetterSpacingEm()) view.letterSpacing = props.letterSpacingEm.value
            if (props.hasLineSpacingExtraDp()) {
                view.setLineSpacing(props.lineSpacingExtraDp.value * density, view.lineSpacingMultiplier)
            }
            if (props.hasIncludeFontPadding()) view.includeFontPadding = props.includeFontPadding.value
        }
    }
}
