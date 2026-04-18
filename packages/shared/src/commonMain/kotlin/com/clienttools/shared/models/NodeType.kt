package com.clienttools.shared.models

import kotlinx.serialization.Serializable

@Serializable
enum class NodeType {
    TEXT, IMAGE, LIST, CONTAINER
}
