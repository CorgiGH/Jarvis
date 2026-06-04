package jarvis.tutor.verify

import jarvis.tutor.VerificationStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/** §P — honest-floor derivation: faithful ⇒ FAITHFUL_TO_SOURCE; every other ⇒ UNVERIFIED. */
class HonestFloorTest {

    @Test
    fun `faithful maps to FAITHFUL_TO_SOURCE`() {
        assertEquals(HonestFloor.FAITHFUL_TO_SOURCE, honestFloorOf(VerificationStatus.faithful))
    }

    @Test
    fun `every other status maps to UNVERIFIED`() {
        for (status in listOf(
            VerificationStatus.unverified,
            VerificationStatus.pending,
            VerificationStatus.uncertain,
            VerificationStatus.failed,
        )) {
            assertEquals(HonestFloor.UNVERIFIED, honestFloorOf(status), "status=$status")
        }
    }

    @Test
    fun `enum has exactly the two frozen UPPER literals`() {
        assertEquals(listOf("FAITHFUL_TO_SOURCE", "UNVERIFIED"), HonestFloor.entries.map { it.name })
    }
}
