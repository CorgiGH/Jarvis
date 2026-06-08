package jarvis.content

import jarvis.tutor.verify.ClaimKind
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Grounded-teaching layer (Task 8) — the REAL pa-kc-001 corpus file authors explanation_ro +
 * worked_example_ro, so claimsFor emits one EXPLANATION + one WORKED_EXAMPLE claim, each PROSE
 * (invariant null) anchored on the KC's first span-bearing source ref.
 */
class Pa001TeachingClaimsTest {

    private fun contentRoot(): Path = Path.of("content") // repo root (gradle test cwd)

    @Test fun `pa-kc-001 emits the two authored teaching claims, anchored and prose`() {
        val kc = ContentRepo(contentRoot()).loadSubject("PA").kcs.single { it.id == "pa-kc-001" }
        assertNotNull(kc.explanation_ro, "pa-kc-001 must author explanation_ro")
        assertNotNull(kc.worked_example_ro, "pa-kc-001 must author worked_example_ro")

        val claims = ContentReconcile.claimsFor(kc)
        val expl = claims.single { it.kind == ClaimKind.EXPLANATION }
        val ex = claims.single { it.kind == ClaimKind.WORKED_EXAMPLE }

        assertNull(expl.invariant, "EXPLANATION is prose")
        assertNull(ex.invariant, "WORKED_EXAMPLE is prose")
        assertNotNull(expl.source?.span, "anchored on the first span-bearing ref")
        assertNotNull(ex.source?.span, "anchored on the first span-bearing ref")
        assertTrue(expl.content.isNotBlank())
        assertTrue(ex.content.isNotBlank())
    }
}
