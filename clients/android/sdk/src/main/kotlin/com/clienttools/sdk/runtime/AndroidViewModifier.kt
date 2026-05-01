package com.clienttools.sdk.runtime

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.clienttools.sdk.ClientToolsSDK
import com.clienttools.sdk.proto.AndroidSizeProps
import com.clienttools.sdk.proto.AndroidViewProps
import java.util.concurrent.CountDownLatch

object AndroidViewModifier {

    fun apply(viewId: String, props: AndroidViewProps): Pair<Boolean, String> {
        val activity = ClientToolsSDK.getCurrentActivity()
            ?: return Pair(false, "No activity available")

        var result: Pair<Boolean, String> = Pair(false, "unknown error")
        val latch = CountDownLatch(1)

        activity.runOnUiThread {
            result = runOnMain(viewId, props)
            latch.countDown()
        }

        latch.await()
        return result
    }

    private fun runOnMain(viewId: String, props: AndroidViewProps): Pair<Boolean, String> {
        // Phase 1: find view
        val views = ViewTreeTraversal.findViewById(viewId)
        if (views.isEmpty()) return Pair(false, "View not found: $viewId")
        val view = views[0]

        // Phase 1: validate margin
        if (props.hasMargin()) {
            val lp = view.layoutParams
            if (lp !is ViewGroup.MarginLayoutParams) {
                return Pair(false, "margin requires MarginLayoutParams, but '$viewId' has ${lp?.javaClass?.simpleName ?: "null"}")
            }
        }

        // Phase 1: validate text
        if (props.hasText()) {
            if (view !is TextView) {
                return Pair(false, "text requires TextView, but '$viewId' is ${view.javaClass.simpleName}")
            }
        }

        // Phase 2: apply
        return try {
            applyProps(view, props)
            Pair(true, "")
        } catch (e: Exception) {
            Pair(false, e.message ?: "error")
        }
    }

    private fun applyProps(view: View, props: AndroidViewProps) {
        val density = view.resources.displayMetrics.density
        val dpToPx = { dp: Float -> (dp * density).toInt() }

        // margin + size share the same layoutParams object — set once
        val lp = view.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        if (props.hasMargin()) {
            val m = props.margin
            val mlp = lp as ViewGroup.MarginLayoutParams
            mlp.setMargins(
                if (m.hasLeftDiffDp()) mlp.leftMargin + dpToPx(m.leftDiffDp.value) else mlp.leftMargin,
                if (m.hasTopDiffDp()) mlp.topMargin + dpToPx(m.topDiffDp.value) else mlp.topMargin,
                if (m.hasRightDiffDp()) mlp.rightMargin + dpToPx(m.rightDiffDp.value) else mlp.rightMargin,
                if (m.hasBottomDiffDp()) mlp.bottomMargin + dpToPx(m.bottomDiffDp.value) else mlp.bottomMargin
            )
        }

        if (props.hasSize()) {
            val s = props.size
            when (s.widthCase) {
                AndroidSizeProps.WidthCase.WIDTH_DP -> lp.width = dpToPx(s.widthDp)
                AndroidSizeProps.WidthCase.WIDTH_WRAP_CONTENT -> lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                else -> {}
            }
            when (s.heightCase) {
                AndroidSizeProps.HeightCase.HEIGHT_DP -> lp.height = dpToPx(s.heightDp)
                AndroidSizeProps.HeightCase.HEIGHT_WRAP_CONTENT -> lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                else -> {}
            }
        }

        if (props.hasMargin() || props.hasSize()) {
            view.layoutParams = lp
        }

        if (props.hasPadding()) {
            val p = props.padding
            val top    = if (p.hasTopDiffDp())    view.paddingTop    + dpToPx(p.topDiffDp.value)    else view.paddingTop
            val bottom = if (p.hasBottomDiffDp()) view.paddingBottom + dpToPx(p.bottomDiffDp.value) else view.paddingBottom
            val left   = if (p.hasLeftDiffDp())   view.paddingLeft   + dpToPx(p.leftDiffDp.value)   else view.paddingLeft
            val right  = if (p.hasRightDiffDp())  view.paddingRight  + dpToPx(p.rightDiffDp.value)  else view.paddingRight
            view.setPadding(left, top, right, bottom)
        }

        if (props.hasText() && view is TextView) {
            val t = props.text
            if (t.hasLetterSpacingEm()) view.letterSpacing = t.letterSpacingEm.value
            if (t.hasLineSpacingExtraDp()) {
                view.setLineSpacing(t.lineSpacingExtraDp.value * density, view.lineSpacingMultiplier)
            }
            if (t.hasIncludeFontPadding()) view.includeFontPadding = t.includeFontPadding.value
        }
    }
}
