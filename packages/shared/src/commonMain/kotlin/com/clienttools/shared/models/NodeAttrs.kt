package com.clienttools.shared.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class NodeAttrs

@Serializable
@SerialName("text")
data class TextAttrs(
    val fontSize: Float,
    val color: String,
    val fontWeight: String
) : NodeAttrs()

@Serializable
@SerialName("image")
data class ImageAttrs(
    val scaleType: String = "fitCenter"
) : NodeAttrs()

@Serializable
@SerialName("list")
data class ListAttrs(
    val itemSpacing: Float,
    val orientation: String
) : NodeAttrs()

@Serializable
@SerialName("container")
data class ContainerAttrs(
    val paddingTop: Float,
    val paddingBottom: Float,
    val paddingLeft: Float,
    val paddingRight: Float
) : NodeAttrs()
