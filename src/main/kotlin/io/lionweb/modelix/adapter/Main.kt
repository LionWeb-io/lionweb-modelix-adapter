package io.lionweb.modelix.adapter

import com.typesafe.config.ConfigFactory
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.resources.Resources
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger
import io.lionweb.lionweb.NodeStructure
import io.lionweb.lionweb.Paths
import io.lionweb.lionweb.SerializationChunk
import org.modelix.model.api.INode
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getRoot
import org.modelix.model.api.remove
import org.modelix.model.client.IdGenerator
import org.modelix.model.client2.runWrite
import org.modelix.model.client2.runWriteOnBranch
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId

val config = ConfigFactory.load()!!

internal val LOGGER = KtorSimpleLogger("io.lionweb.adapter.modelix")

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

// we install JSON content negotiation in the client to modelix
val httpClientConfig: ((HttpClientConfig<*>) -> Unit)? = null
private val clientConfig: (HttpClientConfig<*>) -> Unit by lazy {
    {
        it.install(ContentNegotiation) {
            json()
        }
        httpClientConfig?.invoke(it)
    }
}

fun Application.module() {
    val theLogger = log
    val modelixBaseUrl = config.getString("modelix.server.baseUrl")
    val modelixPort = config.getInt("modelix.server.port")
    val modelixFullUrl = "$modelixBaseUrl:$modelixPort/v2"

    LOGGER.info("modelix adapter starting ...")

    if (config.getBoolean("modelix.server.embedded")) {
        runEmbeddedModelServer()
    }

    install(Routing)
    install(Resources)
    install(IgnoreTrailingSlash)
    install(CallLogging)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: ${cause.message}", status = HttpStatusCode.InternalServerError)
        }
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
    }
    install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
        json()
    }

    log.info("Installing routes")
    routing {
        // expose swaggerUI for the lionweb openapi
        this::class.java.classLoader.getResource("api/lionweb-bulk.yaml")?.let { swaggerUI(path = "swagger", swaggerFile = it.file) }

        post<Paths.getIds> { input ->
            // validate the input to fail early
            validateCountOrFail(input.count, call)

            val modelClient = getModelClient(modelixFullUrl)
            val range = (modelClient.getIdGenerator() as IdGenerator).generate(input.count)

            theLogger.info("Returning ${range.last.minus(range.first)} ids")
            call.respond((range.first..range.last).map { it })
        }

        get<Paths.listPartitions> {
            val nodeStructures: MutableList<NodeStructure> = mutableListOf<NodeStructure>()

            val modelClient = getModelClient(modelixFullUrl)

            modelClient.listRepositories().forEach {repositoryId: RepositoryId ->
                modelClient.runWrite(BranchReference(repositoryId = repositoryId, branchName = RepositoryId.DEFAULT_BRANCH)){
                    nodeStructures.add(
                        transformToLionWebPartition(
                            it.getRoot(),
                            repositoryId.toString()
                        )
                    )
                }
            }

            call.respond(createSerializationChunk(nodeStructures))
        }

        put<Paths.createPartitions, SerializationChunk> { _, chunk ->
            // validate the input to fail early
            validateSerializationFormatVersion(chunk.serializationFormatVersion, call)

            // do we get NodeStructures with partition = true?
            val partitionChunks = chunk.nodes?.filter {
                it.properties?.any { it2 -> (it2.property.key == "partition" && it2.value.toBoolean()) } ?: false
            } ?: emptyList()
            if (partitionChunks.isEmpty()) {
                call.respond(
                    message = "No NodeStructure provided with set partition property",
                    status = HttpStatusCode.UnprocessableEntity
                )
            }

            // execute the request
            val modelClient = getModelClient(modelixFullUrl)
            val existingPartitionNames = modelClient.listRepositories().map { it.id }

            // make sure they do not already exist
            if (existingPartitionNames.any { existingPartitionNames.any { partitionChunks.map { aChunk -> aChunk.id }.contains(it) } }) {
                call.respond(
                    message = "Partition(s) already exist: $partitionChunks",
                    status = HttpStatusCode.UnprocessableEntity
                )
            }

            partitionChunks.forEach { aNewLionPartitionRoot ->
                createRepository(modelClient, aNewLionPartitionRoot.id)
                // replicate the new repository
                modelClient.runWrite(BranchReference(repositoryId = RepositoryId(aNewLionPartitionRoot.id), branchName = RepositoryId.DEFAULT_BRANCH)){ newModelixRepositoryRoot ->
                    markRepositoryRootAsLionwebPartition(newModelixRepositoryRoot, aNewLionPartitionRoot)
                    // do NOT set children or references (partitions do not have those directly)
                    // but we do set all properties
                    addAllPropertiesFromLionNodeToModelixNode(aNewLionPartitionRoot, newModelixRepositoryRoot)
                }
            }

            theLogger.info("Added ${chunk.nodes?.size ?: 0} partitions: ${chunk.nodes?.map { it.id }}")
            call.respondText(text = "200: OK", status = HttpStatusCode.OK)
        }

        put<Paths.deletePartitions, SerializationChunk> { _, chunk ->
            validateSerializationFormatVersion(chunk.serializationFormatVersion, call)

            // get context to fail early
            getModelClient(modelixFullUrl).listRepositories().map { it.id }
            val partitionChunks = chunk.nodes?.filter {
                it.properties?.any { it2 -> (it2.property.key == "partition" && it2.value.toBoolean()) } ?: false
            }?.map { aChunk -> aChunk.id } ?: emptyList()

            // do we get node structures with partition = true?
            if (partitionChunks.isEmpty()) {
                call.respond(
                    message = "No NodeStructure provided with set partition property",
                    status = HttpStatusCode.UnprocessableEntity
                )
            }

            // todo: fail if we get any non partition=true chunks

            partitionChunks.forEach {
                validatePartitionExists(modelixFullUrl, it, call)
            }

            // actually apply changes
            val modelClient = getModelClient(modelixFullUrl)
            partitionChunks.forEach { aPartitionChunkToDelete ->
                deleteRepository(modelClient, aPartitionChunkToDelete)
            }

            theLogger.info("Deleted ${chunk.nodes?.size ?: 0} partitions: ${chunk.nodes?.map { it.id }}")
            call.respondText(text = "200: OK", status = HttpStatusCode.OK)
        }


        put<Paths.bulkStore, SerializationChunk> { input, chunk ->
            validateSerializationFormatVersion(chunk.serializationFormatVersion, call)
            validatePartitionExists(modelixFullUrl, input.partition, call)
            // todo, further check the validity of the input? use schema for this? (e.g. chunk.node.id is actually a number?

            theLogger.info("Will attempt to store ${chunk.nodes?.size ?: 0} nodes")
            val modelClient = getModelClient(modelixFullUrl)
            val branchRef = BranchReference(repositoryId = RepositoryId(input.partition), branchName = RepositoryId.DEFAULT_BRANCH)

            try {
                modelClient.runWriteOnBranch(branchRef) { branch ->
                    chunk.nodes?.forEach { aLionNode ->

                        if (aLionNode.parent.isNullOrEmpty()) {
                            throw Exception("New nodes needs an existing parent. node: ${aLionNode.id} / parent: ${aLionNode.parent}")
                        }

                        // todo
                        //fail: cannot create partition in store
                        // throw Exception("New node cannot be a partition")

                        // obtain parent
                        val modelixParentNode: INode? = resolveLionNodeById(branch, aLionNode.parent)
                        if (modelixParentNode == null || !modelixParentNode.isValid) {
                            throw Exception("New nodes needs an existing parent. node: ${aLionNode.id} / parent: ${aLionNode.parent}")
                        }

                        val modelixNode = resolveLionNodeById(branch, aLionNode.id)
                        if (modelixNode == null || !modelixNode.isValid) {
                            // create
                            theLogger.info("Creating new node ${aLionNode.id}")
                            val childRoleInParent = chunk.nodes.find { it.id == aLionNode.parent }?.containments?.find { it.children?.contains(aLionNode.id) ?: false }?.containment?.key ?: "BROKEN"
                            transformToModelix(modelixParentNode, aLionNode, childRoleInParent)

                        } else {
                            // update
                            theLogger.info("Updating existing node ${aLionNode.id}")
                            updateToModelix(modelixNode, aLionNode)
                        }
                    }
                }
            } catch (e: Exception){
                call.respond(message = e.message.toString(), status = HttpStatusCode.InternalServerError)
            }

            theLogger.info("Successfully added/updated ${chunk.nodes?.size ?: 0} nodes: ${chunk.nodes?.map { it.id }}")
            call.respondText(text = "200: OK", status = HttpStatusCode.OK)
        }


        get<Paths.bulkRetrieve> { input ->
            validatePartitionExists(modelixFullUrl, input.partition, call)

            var depth: Int = -1
            if (input.depthLimit != null) {
                depth = input.depthLimit
            }

            theLogger.info("Will attempt to retrieve ${input.nodes.size} nodes")
            val modelClient = getModelClient(modelixFullUrl)
            val branchRef = BranchReference(repositoryId = RepositoryId(input.partition), branchName = RepositoryId.DEFAULT_BRANCH)

            // target list
            val nodeStructures: MutableList<NodeStructure> = mutableListOf<NodeStructure>()

            try {
                modelClient.runWriteOnBranch(branchRef) { branch ->

                    // set of INodes we look for
                    val modelixRootsToLookFor: MutableSet<INode> = mutableSetOf()
                    // set of all nodes to be returned
                    val modelixNodesToReturn: MutableSet<INode> = mutableSetOf()

                    // iterate over requested node IDs
                    input.nodes.forEach { nodeIdToFind ->
                        // search in the replicated model
                        // try to find the node

                        val result = resolveLionNodeById(branch, nodeIdToFind)

                        if (result != null && result.isValid) {
                            // remember as top level item we look for
                            modelixRootsToLookFor.add(result)
                            // add found node and node descendants to map
                            modelixNodesToReturn.addAll(result.getDescendants(true))
                            // add node references to map
                            modelixNodesToReturn.addAll(result.getAllReferenceTargets().map { it.second })
                        }
                    }

                    // actually gather nodes from the map
                    modelixNodesToReturn.forEach { aNode ->
                        // only if depth is correct
                        if (depth <= -1 || modelixRootsToLookFor.any { aSearchedForRoot ->
                                calculateDistanceUp(
                                    aNode,
                                    aSearchedForRoot
                                ) <= depth
                            }) {
                            nodeStructures.add(transformToLionWebNodeSingle(aNode))
                        }
                    }
                }
            } catch (e: Exception){
               call.respond(message = e.message.toString(), status = HttpStatusCode.InternalServerError)
            }


            this@module.log.info("Returning ${nodeStructures.size} nodes: ${nodeStructures.map { it.id }}")
            call.respond(
                createSerializationChunk(nodeStructures)
            )
        }


        delete<Paths.bulkDelete> { input ->
            validatePartitionExists(modelixFullUrl, input.partition, call)

            theLogger.info("Will attempt to delete ${input.nodes.size} nodes")
            val modelClient = getModelClient(modelixFullUrl)
            val branchRef = BranchReference(repositoryId = RepositoryId(input.partition), branchName = RepositoryId.DEFAULT_BRANCH)

            try {
                modelClient.runWriteOnBranch(branchRef) { branch ->
                    // iterate over input
                    input.nodes.forEach { nodeIdToDelete ->
                        val result = resolveLionNodeById(branch, nodeIdToDelete)
                        if (result == null || !result.isValid) {
                            throw Exception("Node '$nodeIdToDelete' does not exist")
                        }
                        theLogger.info("Removing $nodeIdToDelete")
                        result.remove()
                    }
                }
            } catch (e: Exception){
                call.respond(message = e.message.toString(), status = HttpStatusCode.InternalServerError)
            }
            call.respondText(text = "200: OK", status = HttpStatusCode.OK)
        }
    }
}



