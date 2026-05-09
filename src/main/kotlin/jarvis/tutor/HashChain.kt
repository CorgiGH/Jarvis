package jarvis.tutor

import java.security.MessageDigest

object HashChain {
    data class Link(val prev: String, val canonical: String, val thisHash: String)

    fun nextHash(prev: String, canonicalLine: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(prev.toByteArray(Charsets.UTF_8))
        md.update(canonicalLine.toByteArray(Charsets.UTF_8))
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(chain: List<Link>): Boolean {
        if (chain.isEmpty()) return true
        var prev = chain.first().prev
        for (link in chain) {
            if (link.prev != prev) return false
            val computed = nextHash(link.prev, link.canonical)
            if (computed != link.thisHash) return false
            prev = link.thisHash
        }
        return true
    }
}
