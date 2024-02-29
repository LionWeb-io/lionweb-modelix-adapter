package io.lionweb

import io.lionweb.lionweb.NodeStructure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.SimpleProperty
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.Main
import java.net.ConnectException

private val coroutineScope = CoroutineScope(Dispatchers.Default)

fun INode.getLionNodeId(): String { return getPropertyValue(SimpleProperty(LIONWEB_ID_PROPERTY_ROLE)) ?: "###ERROR###" }
fun INode.setLionNodeId(theId: String) { this.setPropertyValue(SimpleProperty(LIONWEB_ID_PROPERTY_ROLE), theId) }

fun INode.getLionClassifierKey(): String { return getPropertyValue(SimpleProperty(LIONWEB_ID_CLASSIFIER_ROLE)) ?: "###ERROR###" }
fun INode.setLionClassifierKey(classifierKey: String) { this.setPropertyValue(SimpleProperty(LIONWEB_ID_CLASSIFIER_ROLE), classifierKey) }



// always returns a new model client so that we do not have to worry about states
fun getModelClient(modelixURL: String) : ModelClientV2 {
    return runBlocking(coroutineScope.coroutineContext) {
        try {
            LOGGER.info("Connecting to $modelixURL")
            val modelClientV2 = ModelClientV2.builder().url(modelixURL).build().also { it.init() }
            return@runBlocking modelClientV2
        } catch (e: ConnectException) {
            LOGGER.info("Unable to connect: ${e.message} / ${e.cause}")
            throw e
        }
    }
}

fun runEmbeddedModelServer() {
    LOGGER.info("Running embedded in-memory model-server")
    CoroutineScope(Dispatchers.Default).launch {
        Main.main(arrayOf("-inmemory"))
    }
}

fun createRepository(modelClient: ModelClientV2, newRepositoryName: String){
    runBlocking(coroutineScope.coroutineContext) {
        modelClient.initRepository(RepositoryId(newRepositoryName))
    }
}
fun deleteRepository(modelClient: ModelClientV2, newRepositoryName: String){
    runBlocking(coroutineScope.coroutineContext) {
        modelClient.deleteRepository(RepositoryId(newRepositoryName))
    }
}

fun markRepositoryRootAsLionwebPartition(newLionRoot: INode, aNewRootNode: NodeStructure) {
    // mark the root node to make it a 'lionweb' repository
    newLionRoot.setLionNodeId(aNewRootNode.id)
    newLionRoot.setLionClassifierKey(aNewRootNode.classifier.key)
}

fun addAllPropertiesFromLionNodeToModelixNode(aNewLionPartitionRoot: NodeStructure, newModelixRepositoryRoot: INode) {
    aNewLionPartitionRoot.properties?.forEach {
        if (it.property.key.isNotEmpty()) {
            newModelixRepositoryRoot.setPropertyValue(SimpleProperty(it.property.key), it.value)
        }
    }
}

fun resolveLionNodeById(branch: IBranch, id: String): INode? {
    return branch.getRootNode().getDescendants(true).find{
        it.getLionNodeId() == id
    }
}
