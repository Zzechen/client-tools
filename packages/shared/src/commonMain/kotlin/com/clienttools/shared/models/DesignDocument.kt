package com.clienttools.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class DesignDocument(
    val version: String = "1.0",
    val metadata: DocumentMetadata,
    val anchorNodeId: String,
    val nodes: List<Node>
)
