package io.lionweb.modelix.adapter

import io.lionweb.lionweb.SerializationChunk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JsonTests {

    private val jsonInstance = Json {
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    @Test
    fun `validate minimal round trip`() {
        val (fileContent, reEncodedJson) = doReEncoding("jsonFiles/variants/01-minimal.json")
        Assertions.assertEquals(fileContent, reEncodedJson)
    }

    @Test
    fun `validate minimal node round trip`() {
        val (fileContent, reEncodedJson) = doReEncoding("jsonFiles/variants/01-minimal-node.json")
        Assertions.assertEquals(fileContent, reEncodedJson)
    }

    @Test
    fun `validate properties round trip`() {
        val (fileContent, reEncodedJson) = doReEncoding("jsonFiles/variants/02-properties.json")
        Assertions.assertEquals(fileContent, reEncodedJson)
    }

    @Test
    fun `validate containment round trip`() {
        val (fileContent, reEncodedJson) = doReEncoding("jsonFiles/variants/03-containment.json")
        Assertions.assertEquals(fileContent, reEncodedJson)
    }

    @Test
    fun `validate reference round trip`() {
        val (fileContent, reEncodedJson) = doReEncoding("jsonFiles/variants/04-reference.json")
        Assertions.assertEquals(fileContent, reEncodedJson)
    }

    @Test
    fun `validate annotation round trip`() {
        val (fileContent, reEncodedJson) = doReEncoding("jsonFiles/variants/05-annotation.json")
        Assertions.assertEquals(fileContent, reEncodedJson)
    }

    private fun doReEncoding(path: String): Pair<String, String> {
        val fileContent = this.javaClass.classLoader.getResource(path)!!.readText()
        val aChunk = jsonInstance.decodeFromString<SerializationChunk>(fileContent)
        val reEncodedJson = jsonInstance.encodeToString(aChunk)
        return Pair(fileContent, reEncodedJson)
    }
}