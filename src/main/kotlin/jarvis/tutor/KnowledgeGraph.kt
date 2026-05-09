package jarvis.tutor

import jarvis.Config
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines

/**
 * Tutor task-context V1 (Stage B) — section-based knowledge graph.
 *
 * Council 2026-05-09 (council-1778333402-tutor-next.md) verdict on
 * proposed Graphify-style tree-sitter extractor: WRONG — tree-sitter
 * parses code grammars, not Romanian-language prose PDFs. So this
 * builder uses a DIFFERENT deterministic-first split: section-based
 * markdown extraction (`^## ` heading = node, body lines = node text,
 * inter-section "see X" / wikilink-style refs = edges).
 *
 * Output shape mirrors Graphify's MCP tool surface so the same 4 tools
 * the LLM expects — query_graph / get_node / get_neighbors /
 * shortest_path — work identically.
 *
 * Persisted at: <archivalRoot>/_extras/graph.json (filtered out of
 * ConceptCatalog by the `_` prefix rule per Config.archivalDir hygiene).
 *
 * Edge types:
 *   - mentions: nodeA mentions nodeB by name in body (substring match,
 *     ≥4 chars to dampen short-token noise — matches ConceptCatalog
 *     heuristic).
 *   - sibling: same source file, adjacent ## sections.
 *   - subject: same subject (first archival path component).
 *
 * Cheap to build (linear in markdown lines + N² substring per node
 * pair within a subject), single-user scale stays under 1s for ~300
 * concepts. Rebuilt on demand via `gradle ingestCorpus`.
 */
@Serializable
data class KgNode(
    val id: String,           // "<subject>/<concept>" (matches ConceptCatalog name)
    val subject: String,
    val concept: String,
    val sourcePath: String,   // archival-relative path
    val snippet: String,      // first ~600 chars of body (PII-scrubbed at write-time)
)

@Serializable
data class KgEdge(
    val from: String,         // node id
    val to: String,
    val kind: String,         // "mentions" | "sibling" | "subject"
)

@Serializable
data class KnowledgeGraph(
    val builtAt: String,
    val nodes: List<KgNode>,
    val edges: List<KgEdge>,
)

object KnowledgeGraphBuilder {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    fun graphFile(archivalRoot: Path = Config.archivalDir): Path =
        archivalRoot.resolve("_extras").resolve("graph.json")

    /** Walk archival markdown, extract nodes by `## ` heading, build
     *  edges. PII filter via CoreMemory.scanTextForPii drops any node
     *  whose snippet trips a finding. */
    fun build(archivalRoot: Path = Config.archivalDir): KnowledgeGraph {
        val nodes = mutableListOf<KgNode>()
        if (!archivalRoot.exists()) {
            return KnowledgeGraph(java.time.Instant.now().toString(), emptyList(), emptyList())
        }
        Files.walk(archivalRoot).use { stream ->
            stream.filter { it.isRegularFile() && it.extension.equals("md", true) }
                .forEach { p ->
                    val rel = runCatching { archivalRoot.relativize(p).toString().replace('\\', '/') }
                        .getOrElse { return@forEach }
                    if (!ConceptCatalogFilter.isCatalogPath(rel)) return@forEach
                    val subject = ConceptCatalogFilter.subjectFor(rel) ?: return@forEach
                    extractFromFile(rel, subject, p, nodes)
                }
        }
        val edges = buildEdges(nodes)
        return KnowledgeGraph(
            builtAt = java.time.Instant.now().toString(),
            nodes = nodes.distinctBy { it.id },
            edges = edges,
        )
    }

    fun persist(graph: KnowledgeGraph, archivalRoot: Path = Config.archivalDir): Path {
        val path = graphFile(archivalRoot)
        path.parent?.createDirectories()
        Files.writeString(
            path,
            json.encodeToString(KnowledgeGraph.serializer(), graph),
            Charsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE,
        )
        return path
    }

    fun load(archivalRoot: Path = Config.archivalDir): KnowledgeGraph? {
        val path = graphFile(archivalRoot)
        if (!path.exists()) return null
        return try {
            json.decodeFromString(KnowledgeGraph.serializer(), Files.readString(path))
        } catch (_: Exception) {
            null
        }
    }

    private fun extractFromFile(rel: String, subject: String, p: Path, out: MutableList<KgNode>) {
        val lines = try { p.readLines(Charsets.UTF_8) } catch (_: Exception) { return }
        val pending = StringBuilder()
        var currentName: String? = null
        for (line in lines) {
            if (line.startsWith("## ")) {
                flushNode(currentName, subject, rel, pending, out)
                currentName = line.removePrefix("## ").trim().take(120)
                pending.clear()
            } else if (currentName != null) {
                pending.append(line).append('\n')
            }
        }
        flushNode(currentName, subject, rel, pending, out)
    }

    private fun flushNode(
        name: String?, subject: String, rel: String,
        body: StringBuilder, out: MutableList<KgNode>,
    ) {
        if (name.isNullOrBlank()) return
        val snippet = body.toString().lineSequence()
            .filter { it.isNotBlank() }.take(8).joinToString("\n").take(600)
        // PII filter — drop the node if identifier-shaped tokens appear
        // anywhere in the snippet (defense-in-depth; matches the
        // TaskHeader scrub policy).
        if (jarvis.CoreMemory.scanTextForPii(snippet).isNotEmpty()) {
            System.err.println("[KnowledgeGraph] WARN dropping node $subject/$name — PII in snippet")
            return
        }
        out += KgNode(
            id = "$subject/$name",
            subject = subject,
            concept = name,
            sourcePath = rel,
            snippet = snippet,
        )
    }

    private fun buildEdges(nodes: List<KgNode>): List<KgEdge> {
        val out = mutableListOf<KgEdge>()

        // Sibling — adjacent `##` sections in same file.
        val bySource = nodes.groupBy { it.sourcePath }
        for ((_, group) in bySource) {
            for (i in 0 until group.size - 1) {
                out += KgEdge(group[i].id, group[i + 1].id, "sibling")
            }
        }

        // Subject — same first-component subject.
        val bySubject = nodes.groupBy { it.subject }
        for ((_, group) in bySubject) {
            for (i in group.indices) for (j in i + 1 until group.size) {
                out += KgEdge(group[i].id, group[j].id, "subject")
            }
        }

        // Mentions — concept name appears (substring, lowercased, ≥4
        // chars) in another node's snippet within the same subject.
        for ((_, group) in bySubject) {
            val byName = group.associateBy { it.concept.lowercase() }
            for (n in group) {
                val low = n.snippet.lowercase()
                for ((nameLower, target) in byName) {
                    if (target.id == n.id) continue
                    if (nameLower.length < 4) continue
                    if (low.contains(nameLower)) {
                        out += KgEdge(n.id, target.id, "mentions")
                    }
                }
            }
        }

        return out.distinctBy { Triple(it.from, it.to, it.kind) }
    }
}

/** Graph-specific path filter. Wider than ConceptCatalog.isCatalogPath
 *  because the graph wants to index ALL subject material, including the
 *  `_extras/<subject>/` tree where most VPS content actually lives.
 *  Subject inference handles both `<subject>/...` (catalog-canonical) and
 *  `_extras/<subject>/...` (raw / curated) layouts. */
private object ConceptCatalogFilter {
    fun isCatalogPath(rel: String): Boolean {
        // Reject pdf/image/binary paths only via extension at caller; here
        // we only block the truly internal namespaces.
        val parts = rel.split('/')
        if (parts.firstOrNull() == "_curriculum") return false
        if (parts.firstOrNull() == "_RC_FOLDED") return false
        // Allow `_extras` + `_wiki` (Karpathy LLM Wiki dir we own).
        return true
    }

    /** Returns the subject id for [rel]:
     *   `PA/notes.md`               → PA
     *   `_extras/PA/courses/x.md`   → PA
     *   `_wiki/PA/greedy.md`        → PA
     *   `study-guide/...`           → null (skip — meta content)
     *   `second-brain/...`          → null (skip — user notes)
     */
    fun subjectFor(rel: String): String? {
        val parts = rel.split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        // Skip meta dirs entirely.
        if (parts[0] in setOf("study-guide", "second-brain")) return null
        if (parts[0].startsWith("_")) {
            return parts.getOrNull(1)?.takeIf { !it.startsWith("_") && it.length <= 16 }
        }
        return parts[0].takeIf { it.length <= 16 }
    }
}

/** Read-side helpers used by both JarvisToolDefs and tests. */
object KnowledgeGraphQuery {

    /** Substring match on concept name AND snippet, case-insensitive,
     *  ranked by name-prefix > snippet-hit-count. */
    fun query(graph: KnowledgeGraph, q: String, k: Int = 5): List<KgNode> {
        val needle = q.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return graph.nodes
            .map { node ->
                val nameHit = if (node.concept.lowercase().contains(needle)) 100 else 0
                val snippetHits = node.snippet.lowercase().windowed(needle.length, 1)
                    .count { it == needle }
                node to (nameHit + snippetHits)
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(k)
            .map { it.first }
    }

    fun getNode(graph: KnowledgeGraph, id: String): KgNode? =
        graph.nodes.firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun neighbors(graph: KnowledgeGraph, id: String, kind: String? = null): List<Pair<KgNode, String>> {
        val target = id.lowercase()
        // Edges are stored undirected (one entry per pair, see KnowledgeGraphBuilder).
        // Match either endpoint so neighbor traversal is symmetric — otherwise the
        // result depends on Files.walk() ordering during build, which differs across
        // OSes (Windows NTFS alphabetic vs Linux ext4 creation-order).
        return graph.edges
            .filter { (kind == null || it.kind == kind) &&
                (it.from.equals(target, ignoreCase = true) || it.to.equals(target, ignoreCase = true)) }
            .mapNotNull { e ->
                val otherId = if (e.from.equals(target, ignoreCase = true)) e.to else e.from
                graph.nodes.firstOrNull { it.id.equals(otherId, ignoreCase = true) }?.let { it to e.kind }
            }
    }

    /** BFS shortest path between two nodes; returns the node-id chain
     *  including endpoints, or empty when no path exists / endpoints
     *  missing. */
    fun shortestPath(graph: KnowledgeGraph, from: String, to: String, maxDepth: Int = 6): List<String> {
        if (from.equals(to, ignoreCase = true)) return listOf(from)
        val nodeIds = graph.nodes.map { it.id.lowercase() }.toSet()
        val src = from.lowercase(); val dst = to.lowercase()
        if (src !in nodeIds || dst !in nodeIds) return emptyList()
        val adj = mutableMapOf<String, MutableList<String>>()
        for (e in graph.edges) {
            adj.getOrPut(e.from.lowercase()) { mutableListOf() } += e.to.lowercase()
            adj.getOrPut(e.to.lowercase()) { mutableListOf() } += e.from.lowercase()
        }
        val visited = mutableMapOf<String, String?>(src to null)
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(src to 0)
        while (queue.isNotEmpty()) {
            val (cur, depth) = queue.removeFirst()
            if (cur == dst) {
                val path = mutableListOf<String>()
                var n: String? = cur
                while (n != null) { path += n; n = visited[n] }
                // Walk back through original-case ids.
                val byLower = graph.nodes.associateBy { it.id.lowercase() }
                return path.reversed().map { byLower[it]?.id ?: it }
            }
            if (depth >= maxDepth) continue
            for (next in adj[cur].orEmpty()) {
                if (next !in visited) {
                    visited[next] = cur
                    queue.add(next to depth + 1)
                }
            }
        }
        return emptyList()
    }
}
