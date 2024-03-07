package io.lionweb.modelix.adapter

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

suspend fun validateCountOrFail(count: Int, call: ApplicationCall) {
    if (count <= 0) {
        call.respond(HttpStatusCode(400, "Count needs to be > 0"))
    }
}

suspend fun validateSerializationFormatVersion(serializationFormatVersion: String?, call: ApplicationCall) {
    if (serializationFormatVersion != LIONWEB_SUPPORTED_FORMAT_VERSION ){
        call.respond(
            message = "Only serializationFormatVersion '2023.1' supported",
            status = HttpStatusCode.UnprocessableEntity
        )
    }
}
suspend fun validatePartitionExists(modelixURL: String, partitionName: String, call: ApplicationCall){
    if (partitionName !in getModelClient(modelixURL).listRepositories().map { it.id }) {
        call.respond(
            message = "Partition '$partitionName' does not exist",
            status = HttpStatusCode.UnprocessableEntity
        )
    }
}
