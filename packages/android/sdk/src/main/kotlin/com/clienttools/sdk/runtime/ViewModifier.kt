package com.clienttools.sdk.runtime

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.clienttools.sdk.ClientToolsSDK
import com.clienttools.sdk.models.ViewProps

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
        val displayMetrics = view.resources.displayMetrics
        val density = displayMetrics.density
        val dpToPx = { dp: Float -> (dp * density).toInt() }

        val layoutParams = view.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        if (layoutParams is ViewGroup.MarginLayoutParams) {
            val top = props.marginTopDiffDp?.let { layoutParams.topMargin + dpToPx(it) } ?: layoutParams.topMargin
            val bottom = props.marginBottomDiffDp?.let { layoutParams.bottomMargin + dpToPx(it) } ?: layoutParams.bottomMargin
            val left = props.marginLeftDiffDp?.let { layoutParams.leftMargin + dpToPx(it) } ?: layoutParams.leftMargin
            val right = props.marginRightDiffDp?.let { layoutParams.rightMargin + dpToPx(it) } ?: layoutParams.rightMargin
            layoutParams.setMargins(left, top, right, bottom)
        }

        resolveDimension(props.widthDp, density)?.let { layoutParams.width = it }
        resolveDimension(props.heightDp, density)?.let { layoutParams.height = it }

        view.layoutParams = layoutParams

        props.paddingTopDiffDp?.let {
            view.setPadding(view.paddingLeft, view.paddingTop + dpToPx(it), view.paddingRight, view.paddingBottom)
        }
        props.paddingBottomDiffDp?.let {
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom + dpToPx(it))
        }
        props.paddingLeftDiffDp?.let {
            view.setPadding(view.paddingLeft + dpToPx(it), view.paddingTop, view.paddingRight, view.paddingBottom)
        }
        props.paddingRightDiffDp?.let {
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight + dpToPx(it), view.paddingBottom)
        }

        if (view is TextView) {
            props.letterSpacingEm?.let { view.letterSpacing = it }
            props.lineSpacingExtraDp?.let { extra ->
                view.setLineSpacing(extra * density, view.lineSpacingMultiplier)
            }
            props.includeFontPadding?.let { view.includeFontPadding = it }
        }
    }
}
