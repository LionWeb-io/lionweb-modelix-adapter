package io.lionweb.modelix.adapter

import io.lionweb.lionweb.MetaPointer
import io.lionweb.lionweb.NodeStructure
import io.lionweb.lionweb.NodeStructureContainmentsInner
import io.lionweb.lionweb.NodeStructurePropertiesInner
import io.lionweb.lionweb.NodeStructureReferencesInner
import io.lionweb.lionweb.NodeStructureReferencesInnerTargetsInner
import org.modelix.model.api.INode
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.ReferenceLinkFromName
import org.modelix.model.api.SimpleProperty
import org.modelix.model.api.addNewChild
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getNode

fun transformToLionWebNode(modelixNode: INode, depthLimit: Int): List<NodeStructure> {
    val nodeStructureList: MutableList<NodeStructure> = mutableListOf()

    // we add the start node in any case
    nodeStructureList.add(transformToLionWebNodeSingle(modelixNode))

    // we add descendants, if they are not out of depth range. this might cause performance issues for large models.
    modelixNode.getDescendants(false).forEach { it ->
        if (depthLimit <= -1 || calculateDistanceUp(it, modelixNode) <= depthLimit) {
            nodeStructureList.add(transformToLionWebNodeSingle(modelixNode))
        }
    }

    return nodeStructureList
}

fun transformToLionWebPartition(modelixNode: INode, repositoryName: String): NodeStructure {
    return transformToLionWebNodeSingle(modelixNode, repositoryName, true)
}

fun transformToLionWebNodeSingle(
    modelixNode: INode,
    idOverride: String? = null,
    isPartition: Boolean = false
): NodeStructure {
    LOGGER.debug("Transforming node lion->modelix: ${(modelixNode as PNodeAdapter).nodeId} / ${modelixNode.getLionNodeId()} / $idOverride / $isPartition")

    val theProperties = modelixNode.getAllProperties().filter {
        it.first.getSimpleName() !in listOf(LIONWEB_ID_PROPERTY_ROLE, LIONWEB_ID_CLASSIFIER_ROLE)
    }.map {
        NodeStructurePropertiesInner(
            property = MetaPointer(
                language = null,
                version = null,
                key = it.first.getSimpleName()
            ),
            value = it.second
        )
    }

    val containmentsToBe = mutableListOf<NodeStructureContainmentsInner>()
    modelixNode.allChildren.forEach {aChild ->

        val existingContainment = containmentsToBe.find { it.containment!!.key == aChild.roleInParent }

        if (existingContainment == null){
            containmentsToBe.add(
                NodeStructureContainmentsInner(
                    containment = MetaPointer(
                        language = null,
                        version = null,
                        // we assume this to be set, otherwise garbage was stored
                        key = aChild.roleInParent!!,
                    ),
                    children = listOf(aChild.getLionNodeId())
                )
            )
        } else {
            containmentsToBe.remove(existingContainment)
            containmentsToBe.add(
                NodeStructureContainmentsInner(
                    containment = MetaPointer(
                        language = null,
                        version = null,
                        // we assume this to be set, otherwise garbage was stored
                        key = aChild.roleInParent!!,
                    ),
                    children = existingContainment.children?.plus(aChild.getLionNodeId())
                )
            )
        }
    }


    return NodeStructure(
        id = (idOverride ?: modelixNode.getLionNodeId()).toString(),
        classifier = MetaPointer(key = modelixNode.getLionClassifierKey()),
        properties = theProperties,
        // todo: this might have to be re-organized to yield all children of one parent role together?
        // todo: use getDecendants here to obtain a flat list of children
        containments = containmentsToBe,
        references = modelixNode.getAllReferenceTargets().map {
            NodeStructureReferencesInner(
                reference = MetaPointer(
                    language = null,
                    version = null,
                    key = it.first.getSimpleName(),
                ),
                targets = listOf(
                    NodeStructureReferencesInnerTargetsInner(
                        resolveInfo = null,
                        reference = it.second.getLionNodeId(),
                    )
                )
            )
        },
        annotations = emptyList(),
        parent = modelixNode.parent.let {
            // the modelix root is a lionweb 'partition' and thus not returned
            if (modelixNode.parent == null || (modelixNode.parent as PNodeAdapter).nodeId == 1L) {
                null
            } else {
                (modelixNode.parent as INode).getLionNodeId()
            }
        },
    )
}


fun updateToModelix(modelixNode: INode, aNode: NodeStructure) {
    // TODO: we need to handle a change in the parent here

    // we blindly override everything for now. to be more efficient, we would have to only apply needed changes
    changeModelixNode(modelixNode, aNode)
}

fun transformToModelix(modelixParentNode: INode, aLionNode: NodeStructure, containmentRole: String) {
    val newModelixChild = modelixParentNode.addNewChild(containmentRole)
    changeModelixNode(newModelixChild, aLionNode)
}

fun changeModelixNode(mxNode: INode, lionNode: NodeStructure) {
    // clear existing properties
    mxNode.getAllProperties().forEach { mxNode.setPropertyValue(it.first, null) }

    // set special lionweb properties
    mxNode.setLionNodeId(lionNode.id)
    mxNode.setLionClassifierKey(lionNode.classifier.key)

    // set all other properties
    lionNode.properties?.forEach {
        mxNode.setPropertyValue(SimpleProperty(it.property.key), it.value)
    }

    // clear existing references
    mxNode.getReferenceLinks().forEach {
        mxNode.removeReference(it)
    }

    // set all references
    lionNode.references?.forEach {
        // TODO we do not support reference cardinality > 1 thus
        //      the current implementation will always only set the last 'target'
        it.targets?.forEach { aTarget ->
            val target = (mxNode as PNodeAdapter).branch.getNode(aTarget.reference!!.toLong())
            mxNode.setReferenceTarget(ReferenceLinkFromName(it.reference?.key!!), target)
        }
    }
}