package jarvis.tutor.grader

import jarvis.tutor.ProblemKcLinksTable
import jarvis.tutor.ProblemRubricItemsTable
import jarvis.tutor.ProblemSeed
import jarvis.tutor.ProblemsRepo
import jarvis.tutor.ProblemsTable
import jarvis.tutor.TutorDb
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan-6 Task 3 Step 8 — INV-6.1 (pending-not-green) over the SEEDED real-corpus bank.
 *
 * Loads the Task-2 seeds (`ProblemSeed` into a scratch DB), and for EVERY seeded
 * (subject × archetype) problem:
 *   - resolves its chain via [GraderRouting] and asserts the FIRST leg is non-LLM
 *     (rubric counts as a valid first leg for SO code archetypes),
 *   - declares the EXPECTED pending set (subjects AND surface archetype-classes with
 *     ZERO seeded problems) and asserts the actual counts match it EXACTLY — a subject
 *     or archetype-class gaining its first problem flips the expectation LOUDLY
 *     (fix-claim-discipline "pending-not-green").
 */
class GraderRoutingTotalityTest {

    private fun scratchSeededDb(tmp: Path): Database {
        val db = TutorDb.connect(tmp.resolve("routing-totality.db").toString())
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(
                ProblemsTable, ProblemRubricItemsTable, ProblemKcLinksTable,
            )
        }
        ProblemSeed.seed(db, Path.of("content"))
        return db
    }

    @Test
    fun `every seeded problem resolves to a chain whose first leg is non-LLM`(@TempDir tmp: Path) {
        val repo = ProblemsRepo(scratchSeededDb(tmp))
        val allSubjects = repo.countBySubject().keys
        assertTrue(allSubjects.isNotEmpty(), "the seed must produce at least one problem")

        for (subject in allSubjects) {
            for (prob in repo.listBySubject(subject)) {
                val cls = GraderRouting.classify(prob.archetype, prob.examLanguage)
                val chain = GraderRouting.chainFor(prob.subject, prob.examLanguage, cls)
                assertTrue(chain.isNotEmpty(), "chain for ${prob.id} must be non-empty")
                assertTrue(
                    chain.first() != GraderLegKind.LLM_JUDGE,
                    "INV-6.1: ${prob.id} (${prob.subject}/$cls) first leg must be non-LLM, got $chain",
                )
            }
        }
    }

    @Test
    fun `the pending SUBJECT set is exactly POO and SO-RC (named, asserted)`(@TempDir tmp: Path) {
        val repo = ProblemsRepo(scratchSeededDb(tmp))
        val counts = repo.countBySubject()

        // EXPECTED seeded subjects (Task 2 outcome): PA, ALO, PS — 1 each.
        assertEquals(1, counts["PA"], "PA must have its proof seed")
        assertEquals(1, counts["ALO"], "ALO must have its trace seed")
        assertEquals(1, counts["PS"], "PS must have its code seed")

        // EXPECTED pending subjects (zero seeded problems): POO, SO-RC.
        // A pending subject gaining its first problem flips this assertion LOUDLY.
        val pending = setOf("POO", "SO-RC")
        for (p in pending) {
            assertTrue(
                counts[p] == null || counts[p] == 0,
                "PENDING subject '$p' must have zero seeded problems (Task-2 outcome); got ${counts[p]}",
            )
        }
        assertEquals(
            setOf("PA", "ALO", "PS"), counts.keys,
            "exactly PA/ALO/PS are seeded — any new subject must update this pending declaration",
        )
    }

    @Test
    fun `every surface archetype-class is covered — none pending`(@TempDir tmp: Path) {
        val repo = ProblemsRepo(scratchSeededDb(tmp))
        val classes = repo.countBySubject().keys
            .flatMap { repo.listBySubject(it) }
            .map { GraderRouting.classify(it.archetype, it.examLanguage) }
            .toSet()

        // Task-2 seeds cover proof (PA) / trace (ALO) / code (PS). The three surface
        // archetype-classes that the practice surfaces consume are all covered.
        val requiredSurfaceClasses = setOf(
            ArchetypeClass.PROOF, ArchetypeClass.TRACE, ArchetypeClass.CODE,
        )
        val pendingClasses = requiredSurfaceClasses - classes
        assertTrue(
            pendingClasses.isEmpty(),
            "PENDING archetype-classes (zero seeded problems): $pendingClasses — must be named, never silently green",
        )
    }

    @Test
    fun `the PA proof seed routes RUBRIC-first, the ALO trace ORACLE-first, the PS code EXECUTION-first`(
        @TempDir tmp: Path,
    ) {
        val repo = ProblemsRepo(scratchSeededDb(tmp))

        val pa = repo.findById("pa-prob-001")!!
        assertEquals(
            GraderLegKind.RUBRIC,
            GraderRouting.chainFor(pa.subject, pa.examLanguage,
                GraderRouting.classify(pa.archetype, pa.examLanguage)).first(),
        )

        val alo = repo.findById("alo-prob-001")!!
        assertEquals(
            GraderLegKind.NUMERIC_ORACLE,
            GraderRouting.chainFor(alo.subject, alo.examLanguage,
                GraderRouting.classify(alo.archetype, alo.examLanguage)).first(),
        )

        val ps = repo.findById("ps-prob-001")!!
        assertEquals(
            GraderLegKind.EXECUTION,
            GraderRouting.chainFor(ps.subject, ps.examLanguage,
                GraderRouting.classify(ps.archetype, ps.examLanguage)).first(),
        )
    }
}
