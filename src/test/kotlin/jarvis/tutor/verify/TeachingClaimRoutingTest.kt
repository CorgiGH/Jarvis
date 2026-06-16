package jarvis.tutor.verify

import jarvis.ChatMessage
import jarvis.Llm
import jarvis.content.SourceRef
import jarvis.content.Span
import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.TutorDb
import jarvis.tutor.TutorMigration
import jarvis.tutor.VerificationStatus
import jarvis.tutor.VerificationAuditTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Grounded-teaching layer (Task 3) — proves the two NEW prose ClaimKinds (EXPLANATION /
 * WORKED_EXAMPLE) route through the UNCHANGED VerificationRunner.decideOutcome on the PROSE path:
 *   - case 3pr: both-supported + roundtrip → faithful
 *   - case 4p: roundtrip but NOT both-supported → uncertain floor
 *   - anyRefuted veto: one REFUTED → failed
 * They must NEVER touch the equational or DEFINITION branches. invariant = null on both ⇒
 * isEquationalKind == false. No production code was changed; if any case routes to a wrong status
 * a kind leaked into the wrong branch.
 *
 * Hermetic: fake LLM legs (bare-token replies: SUPPORTED / UNCLEAR / REFUTED) + NONE non-LLM leg
 * (prose claims have no machine checker) + injected clock + temp SQLite. No network.
 */
class TeachingClaimRoutingTest {

    // --- fakes -----------------------------------------------------------------------------------

    private class FakeLlm(private val reply: String, private val model: String = "fake-model") : Llm {
        override suspend fun complete(messages: List<ChatMessage>, maxTokens: Int, responseFormat: String?, imagePath: String?) =
            reply to model
    }

    /** A NONE non-LLM leg — prose claims have no machine checker (invariant = null). */
    private val noneNonLlm = NonLlmLeg {
        NonLlmResult(kind = NonLlmLegKind.NONE, ran = false, pass = false, detail = "no machine check for prose kind")
    }

    // --- source fixture --------------------------------------------------------------------------

    /**
     * Tiny raw source whose body contains the exact prose quote we anchor on.
     * Must include a form-feed page-break so SourceOfRecord.PAGE_BREAK relocator works (mirrors
     * the shape used in VerificationRunnerTest.rawSource).
     */
    private val rawSource =
        "PAGE ONE intro text\n" +
            jarvis.content.SourceOfRecord.PAGE_BREAK +
            "An algorithm is a well-ordered collection of unambiguous operations that produces a result and halts.\nEnd.\n"

    private val goldQuote =
        "An algorithm is a well-ordered collection of unambiguous operations that produces a result and halts."

    private fun goldSource() = SourceRef(
        doc = "pa-lecture-01",
        quote = goldQuote,
        page = 2,
        span = Span(0, 1),
    )

    // --- DB harness ------------------------------------------------------------------------------

    private fun freshDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("t.db").toString())
        TutorMigration.migrate(db)
        return db
    }

    private fun seedStatus(db: Database, kcId: String, status: VerificationStatus) {
        transaction(db) {
            KcVerificationStatusTable.insert {
                it[KcVerificationStatusTable.kcId] = kcId
                it[KcVerificationStatusTable.status] = status.name
                it[updatedAt] = Instant.parse("2026-06-01T00:00:00Z")
            }
        }
    }

    /** Build a runner with two DISTINCT-family LLM legs + the NONE non-LLM leg + a fixed clock. */
    private fun runner(
        db: Database,
        legAReply: String,
        legBReply: String,
    ) = VerificationRunner(
        db = db,
        legA = TwoFamilyDeriver.Leg(family = LegFamily.RELAY, llm = FakeLlm(legAReply, "relay-claude")),
        legB = TwoFamilyDeriver.Leg(family = LegFamily.OPENROUTER, llm = FakeLlm(legBReply, "or-free")),
        nonLlmLegFor = { noneNonLlm },
        rawSourceFor = { rawSource },
        clock = { Instant.parse("2026-06-08T00:00:00Z") },
    )

    private fun teachingClaim(kind: ClaimKind, content: String) = VerificationClaim(
        claimId = "pa-kc-001:${kind.name}:deadbeef",
        kcId = "pa-kc-001",
        subject = "PA",
        kind = kind,
        content = content,
        invariant = null,           // prose ⇒ isEquationalKind == false
        source = goldSource(),
    )

    private fun statusOf(db: Database, kcId: String): String? = transaction(db) {
        var s: String? = null
        exec("SELECT status FROM kc_verification_status WHERE kc_id='$kcId'") { rs ->
            if (rs.next()) s = rs.getString(1)
        }
        s
    }

    // --- TESTS -----------------------------------------------------------------------------------

    @Test
    fun `EXPLANATION both-supported + roundtrip reaches faithful (case 3pr)`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        seedStatus(db, "pa-kc-001", VerificationStatus.pending)
        val out = runner(db, legAReply = "SUPPORTED", legBReply = "SUPPORTED")
            .audit(listOf(teachingClaim(ClaimKind.EXPLANATION, "Un algoritm este o colecție bine ordonată de operații neambigue care se oprește.")))

        assertEquals(1, out.size)
        assertEquals(
            VerificationStatus.faithful, out[0].newStatus,
            "EXPLANATION + both-SUPPORTED + roundtrip ⇒ faithful (case 3pr prose path)",
        )
        assertEquals("faithful", statusOf(db, "pa-kc-001"), "B8 upsert must reflect faithful")
    }

    @Test
    fun `WORKED_EXAMPLE roundtrip but not both-supported floors to uncertain (case 4p)`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        seedStatus(db, "pa-kc-001", VerificationStatus.pending)
        val out = runner(db, legAReply = "UNCLEAR", legBReply = "UNCLEAR")
            .audit(listOf(teachingClaim(ClaimKind.WORKED_EXAMPLE, "Exemplu: pașii de adunare sunt neambigui și se opresc.")))

        assertEquals(1, out.size)
        assertEquals(
            VerificationStatus.uncertain, out[0].newStatus,
            "WORKED_EXAMPLE + roundtrip + NOT both-supported (both UNCLEAR) ⇒ uncertain (case 4p prose floor, PROSE_LLM_UNCONFIRMED)",
        )
        assertEquals("uncertain", statusOf(db, "pa-kc-001"))
    }

    @Test
    fun `EXPLANATION refuted by one family vetoes to failed (anyRefuted)`(@TempDir tmp: Path) = runBlocking {
        val db = freshDb(tmp)
        seedStatus(db, "pa-kc-001", VerificationStatus.pending)
        val out = runner(db, legAReply = "SUPPORTED", legBReply = "REFUTED")
            .audit(listOf(teachingClaim(ClaimKind.EXPLANATION, "Un algoritm poate rula la nesfârșit fără să se oprească.")))

        assertEquals(1, out.size)
        assertEquals(
            VerificationStatus.failed, out[0].newStatus,
            "EXPLANATION + any-REFUTED ⇒ failed (anyRefuted veto holds on the prose path too)",
        )
        assertEquals("failed", statusOf(db, "pa-kc-001"))
    }
}
