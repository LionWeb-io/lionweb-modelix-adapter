import io.lionweb.createSerializationChunk
import io.lionweb.lionweb.LanguageStructure
import io.lionweb.lionweb.NodeStructure
import io.lionweb.lionweb.SerializationChunk
import io.lionweb.transformToLionWebNode
import io.lionweb.transformToModelix
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.api.remove
import org.modelix.model.client.IdGenerator
import org.modelix.model.data.ModelData
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.operations.OTBranch
import org.modelix.model.persistent.MapBaseStore
import org.modelix.model.sync.bulk.ModelImporter
import java.io.File


class ConvertersKtTest {

    @get:BeforeAll
    private val jsonInstance = Json {
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    companion object {
        private lateinit var model: ModelData
        private lateinit var branch: OTBranch
        private lateinit var importer: ModelImporter

        @JvmStatic
        @BeforeAll
        fun `load and import model`() {

            // the file is outdated and needs to be updated to contain a valid LionWeb model
            val modelFileContent = this::class.java.classLoader.getResource("jsonFiles/modelix-model-data.json")?.readText()
            model = ModelData.fromJson(modelFileContent!!)

            val store = ObjectStoreCache(MapBaseStore())
            val tree = CLTree(store)
            val idGenerator = IdGenerator.getInstance(1)
            val pBranch = PBranch(tree, idGenerator)

            pBranch.runWrite {
                model.load(pBranch)
            }
            branch = OTBranch(pBranch, idGenerator, store)

            branch.runWrite {
                importer = ModelImporter(branch.getRootNode()).apply { import(model) }
            }
        }
    }

    // TODO the input data needs to be fixed to have correct roleIds, otherwise the converters will fail

//    @Test
//    fun `transform to lionweb node`() {
//        val expectedNode = model.root.children[0]
//        val modelixRootNode = branch.getRootNode()
//        lateinit var aChunk: List<NodeStructure>
//        branch.runRead {
//            aChunk = transformToLionWebNode(modelixRootNode, -1)
//            val reEncodedJson = jsonInstance.encodeToString(aChunk)
//            println(aChunk)
//            println(reEncodedJson)
//        }
//        branch.runWrite {
//            modelixRootNode.allChildren.forEach { it.remove() }
//            modelixRootNode.remove()
////            aChunk.forEach { transformToModelix(modelixRootNode, it) }
//        }
//
//        println(modelixRootNode)
//        println(aChunk)
//
//    }
//
//    @Test
//    fun `create empty SerializationChunk`() {
//        val aNewChunk: SerializationChunk = createSerializationChunk(null)
//        assertEquals("2023.1",aNewChunk.serializationFormatVersion)
//        assertEquals(emptyList<LanguageStructure>(), aNewChunk.languages)
//        assertEquals(emptyList<NodeStructure>(), aNewChunk.nodes)
//    }
}