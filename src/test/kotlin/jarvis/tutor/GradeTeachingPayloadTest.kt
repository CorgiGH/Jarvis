package jarvis.tutor

import jarvis.content.KnowledgeConcept
import jarvis.content.Misconception
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase-3 GROUP 7 (SERVE WIRING) — pure-helper tests for the served `/drill/grade` teaching payload
 * (TASK P3-MISC-SERVE / P3-LADDER-SERVE / P3-GHOST-FIELDS / H15 next_phase_action).
 *
 * Class-killer (H16): a field with STORED content present is SERVED non-null/non-empty (not an
 * always-null ghost); ABSENT content ⇒ null/empty, no throw.
 */
class GradeTeachingPayloadTest {

    private fun kc(
        selfExplain: String? = null,
        farTransfer: String? = null,
    ) = KnowledgeConcept(
        id = "pa-kc-005", subject = "PA", name_ro = "A", name_en = "Algorithm",
        cluster = "c", bloom_level = "understand", difficulty = 1, time_minutes = 10,
        exam_weight = 1.0, tier = 1,
        self_explanation_prompt = selfExplain, far_transfer_stem = farTransfer,
    )

    private fun misc(
        refutation: String = "Nu, complexitatea nu e liniară.",
        trigger: String = "Crezi că bucla simplă e mereu O(n).",
        figureSpec: String? = "bar-chart:complexity",
        selfExplain: String? = "Explică de ce crește pătratic.",
    ) = Misconception(
        id = "pa-misc-001", kc_id = "pa-kc-005",
        label_ro = "l", label_en = "l", trigger = trigger, refutation = refutation,
        figure_spec = figureSpec, self_explanation_prompt = selfExplain,
    )

    // ── TASK P3-MISC-SERVE: a matched stored misconception → inline payload carries refutation+figure_spec ──
    @Test
    fun `MisconceptionPayload from a stored misconception carries refutation and figure_spec (P3-MISC-SERVE)`() {
        val p = MisconceptionPayload.from(misc())
        assertTrue(p != null, "a matched stored misconception must serve a non-null inline payload")
        assertEquals("pa-misc-001", p!!.id)
        assertEquals("Nu, complexitatea nu e liniară.", p.refutation, "refutation served from stored content")
        assertEquals("bar-chart:complexity", p.figure_spec, "figure_spec served for MisconceptionRibbon (P3-GHOST-FIELDS c)")
        assertEquals("Explică de ce crește pătratic.", p.self_explanation_prompt)
    }

    @Test
    fun `MisconceptionPayload from null misconception is null (no ghost)`() {
        assertNull(MisconceptionPayload.from(null), "no misconception ⇒ null inline payload, never a throw")
    }

    @Test
    fun `MisconceptionPayload tolerates an absent figure_spec without throwing`() {
        val p = MisconceptionPayload.from(misc(figureSpec = null))
        assertTrue(p != null)
        assertNull(p!!.figure_spec, "absent figure_spec ⇒ null, ribbon renders its degraded state")
    }

    private fun miscRow(id: String, labelEn: String) = Misconception(
        id = id, kc_id = "pa-kc-005", label_ro = labelEn, label_en = labelEn,
        trigger = "t", refutation = "r",
    )

    @Test
    fun `matchByGraderCode matches a grader code to a stored misconception by id or label`() {
        val rows = listOf(miscRow("pa-misc-off-by-one", "Off by one"), miscRow("pa-misc-overflow", "Overflow"))
        assertEquals("pa-misc-off-by-one", MisconceptionPayload.matchByGraderCode("OFF_BY_ONE", rows)?.id)
    }

    @Test
    fun `matchByGraderCode returns null for generic codes and no-match (no ghost)`() {
        val rows = listOf(miscRow("pa-misc-overflow", "Overflow"))
        assertNull(MisconceptionPayload.matchByGraderCode("OTHER", rows), "generic OTHER never matches")
        assertNull(MisconceptionPayload.matchByGraderCode(null, rows), "null code never matches")
        assertNull(MisconceptionPayload.matchByGraderCode("OFF_BY_ONE", rows), "no stored row ⇒ null")
    }

    // ── TASK P3-LADDER-SERVE: rendered L0-L4 rungs from stored content; degrades when absent ──
    @Test
    fun `FeedbackLadderBuilder renders the full L0-L4 ladder from stored content`() {
        val rungs = FeedbackLadderBuilder.build(
            kc = kc(selfExplain = "De ce crește timpul?"),
            misconception = misc(trigger = "Confuzi liniar cu pătratic.", refutation = "Bucla dublă e O(n^2)."),
            elaboratedFeedback = "Răspunsul corect e O(n^2) pentru că ai două bucle imbricate.",
        )
        val levels = rungs.map { it.level }
        assertEquals(listOf(0, 1, 2, 3, 4), levels, "all five rungs present when all backing content is stored")
        assertEquals("De ce crește timpul?", rungs.single { it.level == 1 }.text, "L1 = self_explanation_prompt")
        assertEquals("Bucla dublă e O(n^2).", rungs.single { it.level == 3 }.text, "L3 = misconception refutation")
        assertTrue(rungs.single { it.level == 4 }.text.contains("două bucle"), "L4 = grader elaborated feedback")
    }

    @Test
    fun `FeedbackLadderBuilder omits rungs whose stored backing is absent (no ghost)`() {
        // No self-explanation prompt, no misconception, no feedback ⇒ only the L0 nudge survives.
        val rungs = FeedbackLadderBuilder.build(kc = kc(selfExplain = null), misconception = null, elaboratedFeedback = null)
        assertEquals(listOf(0), rungs.map { it.level }, "absent teaching content ⇒ degrade to the single L0 nudge")
    }

    @Test
    fun `FeedbackLadderBuilder over a null KC is empty (nothing to scaffold)`() {
        assertTrue(FeedbackLadderBuilder.build(kc = null, misconception = null, elaboratedFeedback = "x").isEmpty())
    }

    // ── H15 next_phase_action: advance / hold / remediate ──
    @Test
    fun `NextPhaseResolver advances on a forward phase move`() {
        assertEquals(NextPhaseAction.advance,
            NextPhaseResolver.resolve(before = Phase.intro, after = Phase.practice, correct = false))
    }

    @Test
    fun `NextPhaseResolver remediates on a phase regression`() {
        assertEquals(NextPhaseAction.remediate,
            NextPhaseResolver.resolve(before = Phase.retrieval, after = Phase.practice, correct = true))
    }

    @Test
    fun `NextPhaseResolver holds at a flat phase on an incorrect attempt, advances when correct`() {
        assertEquals(NextPhaseAction.hold,
            NextPhaseResolver.resolve(before = Phase.practice, after = Phase.practice, correct = false))
        assertEquals(NextPhaseAction.advance,
            NextPhaseResolver.resolve(before = Phase.practice, after = Phase.practice, correct = true))
    }

    @Test
    fun `NextPhaseResolver treats a null before-phase as intro`() {
        assertEquals(NextPhaseAction.advance,
            NextPhaseResolver.resolve(before = null, after = Phase.practice, correct = false))
    }
}
