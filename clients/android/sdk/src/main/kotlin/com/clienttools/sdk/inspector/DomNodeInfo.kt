package com.clienttools.sdk.inspector

data class DomNodeInfo(
    val id: String,
    val tagName: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val text: String
)
