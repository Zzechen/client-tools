package com.clienttools.shared.validation

import com.clienttools.shared.models.DesignDocument
import com.clienttools.shared.models.NodeType
import com.clienttools.shared.models.TextAttrs
import com.clienttools.shared.models.ImageAttrs
import com.clienttools.shared.models.ListAttrs
import com.clienttools.shared.models.ContainerAttrs

data class ValidationError(
    val field: String,
    val message: String
)

object DesignDocumentValidator {

    fun validate(document: DesignDocument): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // 检查锚点存在性
        val anchorNode = document.nodes.find { it.id == document.anchorNodeId }
        if (anchorNode == null) {
            errors.add(ValidationError("anchorNodeId", "Anchor node with id '${document.anchorNodeId}' not found"))
        } else {
            // 检查锚点坐标
            if (anchorNode.screenX != 0f || anchorNode.screenY != 0f) {
                errors.add(ValidationError("anchorNodeId", "Anchor node must have screenX=0 and screenY=0, but got screenX=${anchorNode.screenX}, screenY=${anchorNode.screenY}"))
            }
        }

        // 检查节点 ID 唯一性
        val nodeIds = document.nodes.map { it.id }
        val duplicates = nodeIds.groupingBy { it }.eachCount().filter { it.value > 1 }
        duplicates.forEach { (id, count) ->
            errors.add(ValidationError("nodes", "Duplicate node id '$id' found $count times"))
        }

        // 检查每个节点的有效性
        document.nodes.forEach { node ->
            if (node.widthDp <= 0) {
                errors.add(ValidationError("nodes[${node.id}].widthDp", "Width must be positive, but got ${node.widthDp}"))
            }
            if (node.heightDp <= 0) {
                errors.add(ValidationError("nodes[${node.id}].heightDp", "Height must be positive, but got ${node.heightDp}"))
            }

            // 检查类型与属性匹配
            when (node.type) {
                NodeType.TEXT -> {
                    if (node.attrs !is TextAttrs && node.attrs != null) {
                        errors.add(ValidationError("nodes[${node.id}].attrs", "TEXT node should have TextAttrs"))
                    }
                }
                NodeType.IMAGE -> {
                    if (node.attrs !is ImageAttrs && node.attrs != null) {
                        errors.add(ValidationError("nodes[${node.id}].attrs", "IMAGE node should have ImageAttrs"))
                    }
                }
                NodeType.LIST -> {
                    if (node.attrs !is ListAttrs && node.attrs != null) {
                        errors.add(ValidationError("nodes[${node.id}].attrs", "LIST node should have ListAttrs"))
                    }
                }
                NodeType.CONTAINER -> {
                    if (node.attrs !is ContainerAttrs && node.attrs != null) {
                        errors.add(ValidationError("nodes[${node.id}].attrs", "CONTAINER node should have ContainerAttrs"))
                    }
                }
            }
        }

        return errors
    }

    fun isValid(document: DesignDocument): Boolean = validate(document).isEmpty()
}
