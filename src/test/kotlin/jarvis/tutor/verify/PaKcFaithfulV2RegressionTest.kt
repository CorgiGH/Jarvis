package jarvis.tutor.verify

import jarvis.ChatMessage
import jarvis.Llm
import jarvis.content.ContentRepo
import jarvis.content.ContentReconcile
import jarvis.content.KnowledgeConcept
import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorMigration
import jarvis.tutor.VerificationStatus
import jarvis.tutor.verify.HonestFloor
import jarvis.web.VerifyAdmin
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * THE HARD ACCEPTANCE GATE (fix-plan §"Final green gate"): pa-kc-001..004 MUST stay `faithful` /
 * "matches your lecture" after the WHOLE trust-net change lands — the v2 content-hash re-key (D2 +
 * Step-0 prefix), the D1 `source_span_hash` column + serve gate, the D3 OPEN-report serve refusal,
 * and D4. A fix that drops these four is a false-negative regression and is UNACCEPTABLE.
 *
 * The four are pure DEFINITION KCs over the REAL `content/PA` corpus (no invariant ⇒ `c.invariant`
 * is the empty string in the v2 hash; no grader_rules). They reach faithful via the content-
 * relocating round-trip (case 3p), so this exercises the REAL `_sources/pa-lecture-01.md` extraction
 * through the SAME `fold()` D1 hashes over — proving D1's source-span fingerprint tolerates the
 * authored `\n`-indent + whitespace exactly as the round-trip does.
 *
 * Hermetic on the LLM side: fake SUPPORTED legs (a DEFINITION does not need the LLM to PROMOTE; an
 * agreed-REFUTED would veto, which SUPPORTED avoids). Temp SQLite. No network, no sympy needed.
 * (NOT the live DB, NOT a live re-key — fixtures only.)
 */
class PaKcFaithfulV2RegressionTest {

    private class FakeLlm(private val reply: String) : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?) =
            reply to "fake-model"
    }

    private val targetKcs = listOf("pa-kc-001", "pa-kc-002", "pa-kc-003", "pa-kc-004")

    private fun freshDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("pakc-v2.db").toString())
        TutorMigration.migrate(db)
        return db
    }

    private fun seedPending(db: Database, kcId: String) = transaction(db) {
        KcVerificationStatusTable.insert {
            it[KcVerificationStatusTable.kcId] = kcId
            it[status] = VerificationStatus.pending.name
            it[updatedAt] = Instant.parse("2026-06-01T00:00:00Z")
        }
    }

    @Test
    fun `pa-kc-001 through 004 each reach faithful AND serve the lecture badge under the v2 formula`(@TempDir tmp: Path) = runBlocking {
        val contentDir = Path.of("content")
        val repo = ContentRepo(contentDir)
        val pa = repo.loadSubject("PA")
        val kcs: Map<String, KnowledgeConcept> = pa.kcs.filter { it.id in targetKcs }.associateBy { it.id }
        assertEquals(targetKcs.toSet(), kcs.keys, "the four anchor KCs must exist in content/PA")

        // Sanity: all four are pure DEFINITIONs (no invariant), so the D2 raw-invariant term is empty
        // for them and ONLY the v2 prefix moves their hash (re-audited + re-stamped in the same pass).
        for (id in targetKcs) {
            assertTrue(kcs.getValue(id).invariant == null, "$id is a pure DEFINITION KC (no invariant)")
        }

        val db = freshDb(tmp)
        for (id in targetKcs) seedPending(db, id)

        // The runner over the four KCs' claims, against the REAL _sources extraction.
        val claims = targetKcs.flatMap { ContentReconcile.claimsFor(kcs.getValue(it)) }
        val runner = VerificationRunner(
            db = db,
            legA = TwoFamilyDeriver.Leg(LegFamily.RELAY, FakeLlm("SUPPORTED")),
            legB = TwoFamilyDeriver.Leg(LegFamily.OPENROUTER, FakeLlm("SUPPORTED")),
            nonLlmLegFor = { nonLlmLegFor(it) },
            rawSourceFor = { claim ->
                val doc = claim.source?.doc ?: error("claim ${claim.claimId} has no source.doc")
                repo.sourceText(claim.subject, doc) ?: error("no _sources/$doc.md for ${claim.subject}")
            },
            clock = { Instant.parse("2026-06-05T12:00:00Z") },
        )

        val results = runner.audit(claims)

        // (1) every KC aggregates to faithful under v2.
        for (id in targetKcs) {
            val kcResults = results.filter { it.kcId == id }
            assertTrue(kcResults.isNotEmpty(), "$id emitted claims")
            val status = transaction(db) {
                var s: String? = null
                exec("SELECT status FROM kc_verification_status WHERE kc_id='$id'") { rs -> if (rs.next()) s = rs.getString(1) }
                s
            }
            assertEquals(
                VerificationStatus.faithful.name, status,
                "$id MUST stay faithful under the v2 formula (hard acceptance gate). Per-claim: " +
                    kcResults.map { it.newStatus },
            )
        }

        // (2) every KC carries the v2 content_hash AND a non-null source_span_hash, and serves the
        //     "matches your lecture" badge (FAITHFUL_TO_SOURCE) through the real serve gate.
        for (id in targetKcs) {
            val kc = kcs.getValue(id)
            val row = transaction(db) {
                var contentHash: String? = null
                var sourceSpanHash: String? = null
                exec("SELECT content_hash, source_span_hash FROM kc_verification_status WHERE kc_id='$id'") { rs ->
                    if (rs.next()) { contentHash = rs.getString(1); sourceSpanHash = rs.getString(2) }
                }
                contentHash to sourceSpanHash
            }
            assertNotNull(row.first, "$id carries a stamped v2 content_hash")
            assertEquals(
                ContentReconcile.kcContentHash(kc), row.first,
                "$id's stamped content_hash equals the v2 recompute (audit-side == serve-side)",
            )
            assertNotNull(row.second, "$id carries a source_span_hash (the round-trip relocated its quotes)")

            assertEquals(
                HonestFloor.FAITHFUL_TO_SOURCE, VerifyAdmin.servedHonestFloor(db, id, kc),
                "$id MUST serve the 'matches your lecture' badge under v2 (hard acceptance gate)",
            )
        }
    }

    @Test
    fun `pa-kc-001 through 004 source_span_hash is stable across the round-trip fold (no whitespace flap)`(@TempDir tmp: Path) {
        // D1 anti-regression: re-OCR / whitespace churn must NOT flip the source-span hash for the four
        // anchors. Recompute over the live source and over a whitespace-mangled copy; assert equal.
        val repo = ContentRepo(Path.of("content"))
        val pa = repo.loadSubject("PA")
        val live = repo.sourceText("PA", "pa-lecture-01") ?: error("missing _sources/pa-lecture-01.md")
        // Mangle whitespace the way a re-OCR would: collapse runs, inject newlines+indents.
        val mangled = live.replace(" ", "  ").replace(". ", ".\n  ")

        for (id in listOf("pa-kc-001", "pa-kc-002", "pa-kc-003", "pa-kc-004")) {
            val kc = pa.kcs.single { it.id == id }
            val claims = ContentReconcile.claimsFor(kc)
            val liveHash = ContentReconcile.sourceSpanHashOf(claims) { _, _ -> live }
            val mangledHash = ContentReconcile.sourceSpanHashOf(claims) { _, _ -> mangled }
            assertNotNull(liveHash, "$id relocates against the live source")
            assertEquals(
                liveHash, mangledHash,
                "$id's source_span_hash must be invariant under whitespace/re-OCR churn (D1 fold tolerance)",
            )
        }
    }
}
