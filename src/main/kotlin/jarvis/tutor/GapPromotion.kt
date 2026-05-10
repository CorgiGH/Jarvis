package jarvis.tutor

import jarvis.Fsrs
import org.jetbrains.exposed.sql.Database
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * Phase 7.5 deferral closer: promote a [KnowledgeGap] into an FSRS card.
 *
 * Why: gaps capture *what the user just discovered they didn't know*.
 * Without an SR loop, that knowledge decays. Promotion writes a
 * [FsrsCardsTable] row seeded with [Fsrs.initial(grade = 2)] (Hard —
 * fresh material, low stability), due in ~24 h.
 *
 * The reverse pointer (gap.fsrs_card_id) lets the UI render a "promoted"
 * pill and hide the promote button on subsequent loads. Idempotent: if
 * the gap already carries a cardId, [promote] is a no-op and returns
 * the existing id.
 *
 * Front/back template:
 *   front = topic
 *   back  = content (truncated to 4 KiB) plus the language tag if any
 */
object GapPromotion {

    /** Default initial grade for a freshly-promoted gap. 2 = "Hard" in the
     *  Fsrs four-button model — user just learned it, low confidence. */
    private const val INITIAL_GRADE = 2

    /** Time-to-first-review for a freshly seeded card. Anki-equivalent
     *  default: 1 day. */
    private val FIRST_DUE_DELAY: Duration = Duration.ofDays(1)

    private const val BACK_MAX_LEN = 4_000

    data class PromotionResult(
        val cardId: String,
        val createdNew: Boolean,
        val front: String,
        val back: String,
    )

    fun promote(
        db: Database,
        ledgerDir: Path,
        gapId: String,
        now: Instant = Instant.now(),
    ): PromotionResult? {
        val gapRepo = KnowledgeGapRepo(db, ledgerDir)
        val gap = gapRepo.findById(gapId) ?: return null
        gap.fsrsCardId?.let {
            // Already promoted — return the existing reference rather than
            // creating a duplicate card. Front/back are reconstructed from
            // the gap so callers see consistent payload.
            return PromotionResult(it, createdNew = false, front = gap.topic, back = renderBack(gap))
        }
        val cardRepo = FsrsCardRepo(db)
        val cardId = TutorTypes.ulid()
        val initial = Fsrs.initial(INITIAL_GRADE)
        val front = gap.topic
        val back = renderBack(gap)
        cardRepo.insert(TutorCard(
            id = cardId,
            userId = gap.userId,
            source = FsrsSource.GAP_PROMOTION,
            sourceRef = gap.id,
            front = front,
            back = back,
            state = FsrsState(
                difficulty = initial.difficulty,
                stability = initial.stability,
                retrievability = 1.0,
                dueAt = now.plus(FIRST_DUE_DELAY),
                lastReviewedAt = now,
                lapses = 0,
            ),
        ))
        gapRepo.setFsrsCardId(gap.id, cardId, now)
        return PromotionResult(cardId, createdNew = true, front = front, back = back)
    }

    internal fun renderBack(g: KnowledgeGap): String {
        val sb = StringBuilder()
        sb.append(g.content.take(BACK_MAX_LEN))
        if (!g.exampleCode.isNullOrBlank()) {
            sb.append("\n\n```")
            if (!g.language.isNullOrBlank()) sb.append(g.language)
            sb.append('\n').append(g.exampleCode.take(BACK_MAX_LEN))
            sb.append("\n```")
        }
        if (!g.sourceCitation.isNullOrBlank()) {
            sb.append("\n\nsource: ").append(g.sourceCitation.take(400))
        }
        return sb.toString().take(BACK_MAX_LEN)
    }
}
