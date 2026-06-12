package jarvis.tutor

import jarvis.content.KnowledgeConcept
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/**
 * Plan-2 Task 8 (spec §3.6 step 4) — the fsrs_cards.kc_id backfill: link an existing card to a KC
 * ONLY when their source documents provably match. The match is EXACT normalized-stem equality
 * (§0.8 #2) — no fuzzy/similarity scoring, no LLM guessing. Cards that cannot be confidently linked
 * stay NULL-linked and keep scheduling independently ("no forced guesses").
 *
 * Production reality (verified 2026-06-11): 0 links — PA cards reference lecture11_x/seminar9_10_x,
 * the 6 PA KCs are all sourced from `pa-lecture-01`; zero confident overlap. A 0-link run is the
 * CORRECT result, not a failure.
 */
object KcIdBackfill {

    /** A card's identity + its raw `source_ref` (pattern `SUBJECT:filename.md`). */
    data class CardRef(val id: String, val sourceRef: String)

    /** One confident link: this card belongs to this KC (of this subject). */
    data class Link(val cardId: String, val kcId: String, val subject: String)

    /**
     * Map a card-source prefix to the tutor subject id used for KC lookup. `SO:` and `RC:` BOTH map
     * to the `SO-RC` subject (subjects.yaml id; §0.5). Every other prefix maps to itself.
     */
    fun subjectOfPrefix(prefix: String): String = when (prefix) {
        "SO", "RC" -> "SO-RC"
        else -> prefix
    }

    /**
     * Normalize a source-doc identifier to its comparable stem:
     *   strip a `SUBJECT:` prefix (everything up to and including the first ':'),
     *   strip a file extension (everything from the last '.'),
     *   lowercase,
     *   collapse every run of [-_ ] into a single space, then trim.
     *
     * Examples (§0.8 #2, PM-amended 2026-06-12):
     *   "PA:lecture11_en.md" -> "lecture11 en"
     *   "pa-lecture-01"      -> "pa lecture 01"   (see kcDocStems for the subject-stripped variant)
     */
    fun normalizeDocStem(raw: String): String {
        val afterPrefix = raw.substringAfter(':', raw) // no ':' -> unchanged
        val noExt = afterPrefix.substringBeforeLast('.', afterPrefix)
        return noExt.lowercase()
            .replace(Regex("[-_ ]+"), " ")
            .trim()
    }

    /**
     * Normalized subject tokens whose LEADING occurrence in a KC doc stem is non-identifying:
     * the whole normalized subject plus each dash-half. PA -> ["pa"]; SO-RC -> ["so rc","so","rc"].
     */
    fun subjectStripTokens(subject: String): List<String> {
        val whole = subject.lowercase().replace(Regex("[-_ ]+"), " ").trim()
        return (listOf(whole) + whole.split(' ')).distinct()
    }

    /**
     * All comparable stems for one KC source doc: the raw normalized stem PLUS the
     * subject-prefix-stripped variant. Rationale (PM reconciliation 2026-06-12): KC docs
     * conventionally carry the subject INSIDE the name ("pa-lecture-01") while card source_refs
     * carry it in the "PA:" prefix — the subject marker is non-identifying on both sides; the
     * stem is the document identity. Still exact-match only; no fuzzy similarity.
     */
    fun kcDocStems(subject: String, doc: String): Set<String> {
        val raw = normalizeDocStem(doc)
        val stripped = subjectStripTokens(subject)
            .sortedByDescending { it.length }
            .firstOrNull { raw.startsWith("$it ") }
            ?.let { raw.removePrefix("$it ").trim() }
        return setOfNotNull(raw, stripped)
    }

    /** The `SUBJECT` prefix of a card `source_ref` ("PA:lecture11_en.md" -> "PA"); "" if no ':'. */
    private fun prefixOf(sourceRef: String): String =
        if (':' in sourceRef) sourceRef.substringBefore(':') else ""

    /**
     * Pure. A link exists iff (a) the card's prefix maps to the KC's subject AND (b) the card's
     * normalized stem equals an UNAMBIGUOUS KC doc stem of that subject. A stem claimed by MORE
     * THAN ONE distinct KC is dropped entirely — ambiguous = no link ("no forced guesses", §0.8 #2;
     * silent first-wins would be a hidden guess). Deterministic: input-card order.
     */
    fun computeLinks(cards: List<CardRef>, kcs: List<KnowledgeConcept>): List<Link> {
        data class Key(val subject: String, val stem: String)
        val claims = HashMap<Key, MutableSet<String>>()
        for (kc in kcs) {
            for (ref in kc.source) {
                for (stem in kcDocStems(kc.subject, ref.doc)) {
                    claims.getOrPut(Key(kc.subject, stem)) { LinkedHashSet() }.add(kc.id)
                }
            }
        }
        val unambiguous = claims.filterValues { it.size == 1 }.mapValues { (_, v) -> v.single() }
        val links = ArrayList<Link>()
        for (card in cards) {
            val subject = subjectOfPrefix(prefixOf(card.sourceRef))
            val stem = normalizeDocStem(card.sourceRef)
            val kcId = unambiguous[Key(subject, stem)] ?: continue
            links += Link(cardId = card.id, kcId = kcId, subject = subject)
        }
        return links
    }

    /**
     * Apply links to fsrs_cards: `UPDATE fsrs_cards SET kc_id=? WHERE id=? AND kc_id IS NULL`.
     * NEVER overwrites a non-null kc_id (the RUBRIC_CRITERION migration backfill already stamped
     * some; this is additive). Returns the count actually written. Idempotent: a second run re-finds
     * no NULL rows for the already-linked ids and writes 0.
     */
    fun applyLinks(db: Database, links: List<Link>): Int = transaction(db) {
        var applied = 0
        for (link in links) {
            // Exposed DSL update: returns the affected-row count directly, no string-built SQL,
            // no connection-scoped changes() readback. WHERE kc_id IS NULL = never-overwrite.
            applied += FsrsCardsTable.update(
                { (FsrsCardsTable.id eq link.cardId) and FsrsCardsTable.kcId.isNull() },
            ) { it[kcId] = link.kcId }
        }
        applied
    }
}
