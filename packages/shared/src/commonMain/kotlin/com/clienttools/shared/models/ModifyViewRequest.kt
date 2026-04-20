package com.clienttools.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class ViewProps(
    val marginTopDiffDp: Float? = null,
    val marginBottomDiffDp: Float? = null,
    val marginLeftDiffDp: Float? = null,
    val marginRightDiffDp: Float? = null,
    val paddingTopDiffDp: Float? = null,
    val paddingBottomDiffDp: Float? = null,
    val paddingLeftDiffDp: Float? = null,
    val paddingRightDiffDp: Float? = null,
    val widthDp: String? = null,
    val heightDp: String? = null
)

@Serializable
data class ModifyViewRequest(
    val id: String,
    val props: ViewProps
)
