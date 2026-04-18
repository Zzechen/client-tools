package com.clienttools.sdk.runtime

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.clienttools.sdk.ClientToolsSDK
import com.clienttools.shared.models.ViewProps

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

    private fun modify(view: View, props: ViewProps) {
        val displayMetrics = view.resources.displayMetrics
        val dpToPx = { dp: Float -> (dp * displayMetrics.density).toInt() }

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

        props.widthDp?.let {
            layoutParams.width = dpToPx(it)
        }
        props.heightDp?.let {
            layoutParams.height = dpToPx(it)
        }

        view.layoutParams = layoutParams

        props.paddingTopDiffDp?.let {
            view.setPadding(
                view.paddingLeft,
                view.paddingTop + dpToPx(it),
                view.paddingRight,
                view.paddingBottom
            )
        }
    }
}
