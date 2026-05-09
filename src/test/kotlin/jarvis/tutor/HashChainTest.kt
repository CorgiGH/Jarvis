package jarvis.tutor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class HashChainTest {
    @Test
    fun `nextHash deterministic given prevHash and canonical line`() {
        val a = HashChain.nextHash(prev = "0".repeat(64), canonicalLine = """{"a":1}""")
        val b = HashChain.nextHash(prev = "0".repeat(64), canonicalLine = """{"a":1}""")
        assertEquals(a, b)
    }

    @Test
    fun `nextHash differs for different prev`() {
        val a = HashChain.nextHash(prev = "0".repeat(64), canonicalLine = """{"a":1}""")
        val b = HashChain.nextHash(prev = "f".repeat(64), canonicalLine = """{"a":1}""")
        assertNotEquals(a, b)
    }

    @Test
    fun `verify returns true for intact chain`() {
        val l1 = HashChain.nextHash("0".repeat(64), """{"seq":1}""")
        val l2 = HashChain.nextHash(l1, """{"seq":2}""")
        val l3 = HashChain.nextHash(l2, """{"seq":3}""")
        val chain = listOf(
            HashChain.Link("0".repeat(64), """{"seq":1}""", l1),
            HashChain.Link(l1, """{"seq":2}""", l2),
            HashChain.Link(l2, """{"seq":3}""", l3),
        )
        assertEquals(true, HashChain.verify(chain))
    }

    @Test
    fun `verify detects tampered line`() {
        val l1 = HashChain.nextHash("0".repeat(64), """{"seq":1}""")
        val l2 = HashChain.nextHash(l1, """{"seq":2}""")
        val chain = listOf(
            HashChain.Link("0".repeat(64), """{"seq":1}""", l1),
            HashChain.Link(l1, """{"seq":2-TAMPERED}""", l2),
        )
        assertEquals(false, HashChain.verify(chain))
    }
}
