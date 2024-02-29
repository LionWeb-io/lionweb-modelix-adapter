import com.typesafe.config.ConfigFactory
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import io.lionweb.config
import io.lionweb.lionweb.SerializationChunk
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.modelix.model.server.Main
import kotlin.test.Test
import org.modelix.model.sleep
import kotlin.test.assertEquals


class LionWebAPITest {

    @get:BeforeAll
    private val jsonInstance = Json {
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    @BeforeEach
    fun setupTest(): Unit {
        if (!ConfigFactory.load().getBoolean("modelix.server.embedded")) {
            // start a model-server in background
            CoroutineScope(Dispatchers.Default).launch {
                Main.main(arrayOf("-inmemory"))
            }
        }
    }

    // TODO: use parameterized tests

    @Test
    fun `get zero IDs`() {
        testApplication {
            // read what we expect
            val fileContent = this.javaClass.classLoader.getResource("jsonFiles/test-list-partition-empty.json")?.readText()
            // obtain server response
            val response = client.post("/ids") {
                contentType(ContentType.Application.Json)
                parameter("count", 0)
                setBody(fileContent)
            }

            // ensure correctness
            assertEquals(HttpStatusCode.BadRequest, response.status)
      }
    }
    @Test
    fun `get single IDs`() {
        testApplication {
            // read what we expect
            val fileContent = this.javaClass.classLoader.getResource("jsonFiles/test-list-partition-empty.json")?.readText()
            // obtain server response
            val response = client.post("/ids") {
                contentType(ContentType.Application.Json)
                parameter("count", 1)
                setBody(fileContent)
            }

            // ensure correctness
            assertEquals(HttpStatusCode.OK, response.status)
            assert(response.bodyAsText().removeSurrounding("[", "]").split(",").map { it.toLong() }.size == 1)
            assert(response.bodyAsText().removeSurrounding("[", "]").split(",").map { it.toLong() }.all { it > 0L })
        }
    }

    @Test
    fun `get multiple IDs`() {
        testApplication {
            // read what we expect
            val fileContent = this.javaClass.classLoader.getResource("jsonFiles/test-list-partition-empty.json")?.readText()
            // obtain server response
            val response = client.post("/ids") {
                contentType(ContentType.Application.Json)
                parameter("count", 5)
                setBody(fileContent)
            }

            // ensure correctness
            assertEquals(HttpStatusCode.OK, response.status)
            assert(response.bodyAsText().removeSurrounding("[", "]").split(",").map { it.toLong() }.size == 5)
            assert(response.bodyAsText().removeSurrounding("[", "]").split(",").map { it.toLong() }.all { it > 0L })
        }
    }

    @Test
    fun `list partitions empty`() {
        testApplication {
            val fileContent = this.javaClass.classLoader.getResource("jsonFiles/test-list-partition-empty.json")!!.readText()
            val response = client.get("/listPartitions")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(fileContent.replace("\n", "").replace(" ", ""), response.bodyAsText())
        }
    }

    @Test
    fun `create partition`() {
        testApplication {
            val fileContent =
                this.javaClass.classLoader.getResource("jsonFiles/test-create-partition.json")!!.readText()
            val fileChunk = jsonInstance.decodeFromString<SerializationChunk>(fileContent)

            val responsePut = client.put("/createPartitions") {
                contentType(ContentType.Application.Json)
                setBody(fileContent)
            }

            assertEquals(HttpStatusCode.OK, responsePut.status)

            val responseList = client.get("/listPartitions")
            val responseChunk = jsonInstance.decodeFromString<SerializationChunk>(responseList.body())

            assertEquals(HttpStatusCode.OK, responseList.status)
            assertEquals(fileChunk, responseChunk)
        }
    }
}
