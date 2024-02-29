import io.lionweb.createSerializationChunk
import io.lionweb.lionweb.LanguageStructure
import io.lionweb.lionweb.NodeStructure
import io.lionweb.lionweb.SerializationChunk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll

class LionwebUtilsKtTest {

    @get:BeforeAll
    private val jsonInstance = Json {
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    // TODO: tests have to be crated to test the lionweb utils

    @Test
    fun `create empty SerializationChunk`() {
        val aNewChunk: SerializationChunk = createSerializationChunk(null)
        assertEquals("2023.1",aNewChunk.serializationFormatVersion)
        assertEquals(emptyList<LanguageStructure>(), aNewChunk.languages)
        assertEquals(emptyList<NodeStructure>(), aNewChunk.nodes)
    }

    // TODO
//    @Test
//    fun `create filled SerializationChunk`() {
//        val aNewChunk: SerializationChunk = createSerializationChunk(null)
//        val aChunk = jsonInstance.decodeFromString<SerializationChunk>(this.javaClass.classLoader.getResource("jsonFiles/variants/02-properties.json")!!.readText())
//
//        val aFilledChunk: SerializationChunk = createSerializationChunk(aChunk.nodes)
//
//        assertEquals("2023.1", aFilledChunk.serializationFormatVersion)
//        assertEquals(1, aFilledChunk.languages!!.size)
//        assertEquals("myLanguage", aFilledChunk.languages!!.first().key)
//        assertEquals("version", aFilledChunk.languages!!.first().version)
//        assertEquals(2, aFilledChunk.nodes!!.size)
//        assertEquals(5, aFilledChunk.nodes!!.first().properties!!.size)
//    }
//
//    @Test
//    fun calculateDistanceUp() {
//    }
}