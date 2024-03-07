package io.lionweb.modelix.adapter

import io.lionweb.lionweb.NodeStructure
import io.lionweb.lionweb.SerializationChunk
import org.modelix.model.api.INode

const val LIONWEB_SUPPORTED_FORMAT_VERSION = "2023.1"
const val LIONWEB_ID_PROPERTY_ROLE = "###lionwebid###"
const val LIONWEB_ID_CLASSIFIER_ROLE = "###lionwebclassifierkey###"

fun createSerializationChunk(nodeStructures: List<NodeStructure>?): SerializationChunk =
    SerializationChunk(
        languages = emptyList(),
        nodes = nodeStructures ?: emptyList()
    )

fun calculateDistanceUp(bottom: INode, top: INode): Int {
    var counter = 0
    var current = bottom

    while (true) {
        when (top) {
            current -> return counter
            current.parent -> return counter.plus(1)
            else -> {
                counter += 1
                if (current.parent == null) {
                    break
                } else {
                    current = current.parent!!
                }
            }
        }
    }
    return counter
}
