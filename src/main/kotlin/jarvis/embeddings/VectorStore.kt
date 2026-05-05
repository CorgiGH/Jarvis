package jarvis.embeddings

import jarvis.Config
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.math.sqrt

@Serializable
data class StoredEmbedding(
    val id: String,
    val text: String,
    val embedding: FloatArray,
) {
    override fun equals(other: Any?): Boolean = other is StoredEmbedding && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

@Serializable
private data class VectorStoreData(val entries: List<StoredEmbedding> = emptyList())

object VectorStore {
    private val storeFile = Config.stateDir.resolve("embeddings.json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var cache: MutableList<StoredEmbedding>? = null
    private val lock = Any()

    fun all(): List<StoredEmbedding> = ensureLoaded().toList()

    fun ids(): Set<String> = ensureLoaded().mapTo(HashSet()) { it.id }

    fun add(entry: StoredEmbedding) {
        synchronized(lock) {
            val list = ensureLoaded()
            // de-dup by id: replace existing with same id
            list.removeAll { it.id == entry.id }
            list.add(entry)
            persist(list)
        }
    }

    fun search(query: FloatArray, k: Int = 5, minScore: Float = 0f): List<Pair<StoredEmbedding, Float>> {
        val list = ensureLoaded()
        if (list.isEmpty()) return emptyList()
        return list.asSequence()
            .map { it to cosine(query, it.embedding) }
            .filter { it.second >= minScore }
            .sortedByDescending { it.second }
            .take(k)
            .toList()
    }

    private fun ensureLoaded(): MutableList<StoredEmbedding> {
        cache?.let { return it }
        synchronized(lock) {
            cache?.let { return it }
            val loaded = if (storeFile.exists()) {
                try {
                    json.decodeFromString<VectorStoreData>(storeFile.readText(Charsets.UTF_8))
                        .entries
                        .toMutableList()
                } catch (_: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }
            cache = loaded
            return loaded
        }
    }

    private fun persist(list: List<StoredEmbedding>) {
        Config.stateDir.createDirectories()
        val payload = json.encodeToString(VectorStoreData.serializer(), VectorStoreData(list))
        Files.writeString(storeFile, payload, Charsets.UTF_8)
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            val ai = a[i].toDouble()
            val bi = b[i].toDouble()
            dot += ai * bi
            na += ai * ai
            nb += bi * bi
        }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom == 0.0) 0f else (dot / denom).toFloat()
    }
}
