package jarvis.tutor.verify

import jarvis.content.KnowledgeConcept
import jarvis.tutor.VerificationStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/** §I2 — the single trust chokepoint. Pure: ALLOW iff faithful && !hasOpenReportWrong. */
class VerificationGateTest {

    private val kc = KnowledgeConcept(
        id = "pa-kc-005",
        subject = "PA",
        name_ro = "Dimensiunea valorilor",
        name_en = "Value size",
        cluster = "Eficiența algoritmilor",
        bloom_level = "apply",
        difficulty = 3,
        time_minutes = 30,
        exam_weight = 0.13,
        tier = 3,
    )

    @Test
    fun `faithful and no open report-wrong is ALLOW`() {
        assertEquals(
            GateDecision.ALLOW,
            VerificationGate.gate(kc, VerificationStatus.faithful, hasOpenReportWrong = false),
        )
    }

    @Test
    fun `every non-faithful status is DENY`() {
        for (status in listOf(
            VerificationStatus.unverified,
            VerificationStatus.pending,
            VerificationStatus.uncertain,
            VerificationStatus.failed,
        )) {
            assertEquals(
                GateDecision.DENY,
                VerificationGate.gate(kc, status, hasOpenReportWrong = false),
                "status=$status must DENY",
            )
        }
    }

    @Test
    fun `faithful but an open report-wrong is DENY`() {
        assertEquals(
            GateDecision.DENY,
            VerificationGate.gate(kc, VerificationStatus.faithful, hasOpenReportWrong = true),
        )
    }
}
