package jarvis.tutor

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DaemonAuthTest {

    private val secret = "test-secret-32bytes-min-".toByteArray() + Random(42).nextBytes(8)
    private val now = Instant.parse("2026-05-09T12:00:00Z")

    private fun signedHeaders(
        method: String, path: String, body: ByteArray,
        timestamp: Long = now.toEpochMilli(),
        nonce: String = "n-" + Random(1).nextBytes(8).joinToString("") { "%02x".format(it) },
    ): Triple<String, String, String> {
        val canon = DaemonAuth.canonical(method, path, timestamp, nonce, body)
        val sig = DaemonAuth.sign(secret, canon)
        return Triple(timestamp.toString(), nonce, sig)
    }

    @Test
    fun roundTripValidates() {
        val nonces = NonceCache()
        val body = """{"foo":"bar"}""".toByteArray()
        val (ts, nonce, sig) = signedHeaders("POST", "/effector/dispatch", body)
        val r = DaemonAuth.verify(secret, "POST", "/effector/dispatch", ts, nonce, body, sig, nonces, now)
        assertEquals(DaemonAuth.Result.Ok, r)
    }

    @Test
    fun replayedNonceRejected() {
        val nonces = NonceCache()
        val body = byteArrayOf()
        val (ts, nonce, sig) = signedHeaders("GET", "/health", body)
        // First call passes.
        assertEquals(DaemonAuth.Result.Ok,
            DaemonAuth.verify(secret, "GET", "/health", ts, nonce, body, sig, nonces, now))
        // Second call with same nonce → REPLAY.
        val r2 = DaemonAuth.verify(secret, "GET", "/health", ts, nonce, body, sig, nonces, now)
        assertTrue(r2 is DaemonAuth.Result.Fail && r2.kind == DaemonAuth.FailureKind.REPLAY)
    }

    @Test
    fun skewBeyond30sRejected() {
        val nonces = NonceCache()
        val body = byteArrayOf()
        val ts = now.minus(Duration.ofSeconds(45)).toEpochMilli()
        val (_, nonce, sig) = signedHeaders("GET", "/health", body, timestamp = ts)
        val r = DaemonAuth.verify(secret, "GET", "/health", ts.toString(), nonce, body, sig, nonces, now)
        assertTrue(r is DaemonAuth.Result.Fail && r.kind == DaemonAuth.FailureKind.SKEW,
            "expected SKEW; got $r")
    }

    @Test
    fun missingTimestampRejected() {
        val nonces = NonceCache()
        val r = DaemonAuth.verify(secret, "GET", "/x", null, "n", byteArrayOf(), "sig", nonces, now)
        assertTrue(r is DaemonAuth.Result.Fail && r.kind == DaemonAuth.FailureKind.BAD_TIMESTAMP)
    }

    @Test
    fun nonNumericTimestampRejected() {
        val nonces = NonceCache()
        val r = DaemonAuth.verify(secret, "GET", "/x", "later", "n", byteArrayOf(), "sig", nonces, now)
        assertTrue(r is DaemonAuth.Result.Fail && r.kind == DaemonAuth.FailureKind.BAD_TIMESTAMP)
    }

    @Test
    fun missingNonceRejected() {
        val nonces = NonceCache()
        val ts = now.toEpochMilli().toString()
        val r = DaemonAuth.verify(secret, "GET", "/x", ts, null, byteArrayOf(), "sig", nonces, now)
        assertTrue(r is DaemonAuth.Result.Fail && r.kind == DaemonAuth.FailureKind.REPLAY)
    }

    @Test
    fun missingSignatureRejected() {
        val nonces = NonceCache()
        val ts = now.toEpochMilli().toString()
        val r = DaemonAuth.verify(secret, "GET", "/x", ts, "fresh-nonce", byteArrayOf(), null, nonces, now)
        assertTrue(r is DaemonAuth.Result.Fail && r.kind == DaemonAuth.FailureKind.BAD_SIGNATURE)
    }

    @Test
    fun mutatedBodyRejected() {
        val nonces = NonceCache()
        val realBody = """{"x":1}""".toByteArray()
        val tamperBody = """{"x":2}""".toByteArray()
        val (ts, nonce, sig) = signedHeaders("POST", "/x", realBody)
        // Server receives tampered body; sig was bound to realBody.
        val r = DaemonAuth.verify(secret, "POST", "/x", ts, nonce, tamperBody, sig, nonces, now)
        assertTrue(r is DaemonAuth.Result.Fail && r.kind == DaemonAuth.FailureKind.BAD_SIGNATURE,
            "tampered body must fail HMAC; got $r")
    }

    @Test
    fun differentMethodRejected() {
        val nonces = NonceCache()
        val (ts, nonce, sig) = signedHeaders("POST", "/x", byteArrayOf())
        // Server invoked with same canonical fields except method=GET.
        val r = DaemonAuth.verify(secret, "GET", "/x", ts, nonce, byteArrayOf(), sig, nonces, now)
        assertTrue(r is DaemonAuth.Result.Fail && r.kind == DaemonAuth.FailureKind.BAD_SIGNATURE)
    }

    @Test
    fun differentPathRejected() {
        val nonces = NonceCache()
        val (ts, nonce, sig) = signedHeaders("POST", "/effector/dispatch", byteArrayOf())
        val r = DaemonAuth.verify(secret, "POST", "/effector/admin", ts, nonce, byteArrayOf(), sig, nonces, now)
        assertTrue(r is DaemonAuth.Result.Fail && r.kind == DaemonAuth.FailureKind.BAD_SIGNATURE)
    }

    @Test
    fun wrongSecretRejected() {
        val nonces = NonceCache()
        val (ts, nonce, sig) = signedHeaders("POST", "/x", byteArrayOf())
        val otherSecret = "different".toByteArray()
        val r = DaemonAuth.verify(otherSecret, "POST", "/x", ts, nonce, byteArrayOf(), sig, nonces, now)
        assertTrue(r is DaemonAuth.Result.Fail && r.kind == DaemonAuth.FailureKind.BAD_SIGNATURE)
    }

    @Test
    fun verifyFailureDoesNotConsumeNonce() {
        val nonces = NonceCache()
        val ts = now.toEpochMilli().toString()
        val nonce = "fresh-nonce-1"
        // Bad signature on first try.
        DaemonAuth.verify(secret, "GET", "/x", ts, nonce, byteArrayOf(), "bad-sig", nonces, now)
        // The nonce must still be valid for a legitimate retry.
        val (ts2, _, sig) = signedHeaders("GET", "/x", byteArrayOf(), timestamp = now.toEpochMilli(), nonce = nonce)
        val r = DaemonAuth.verify(secret, "GET", "/x", ts2, nonce, byteArrayOf(), sig, nonces, now)
        assertEquals(DaemonAuth.Result.Ok, r, "failed verify must not burn nonce")
    }

    /** Council pre-build gate equivalent: 1000 randomized verifications.
     *  Valid signs pass, all classes of mutation reject. */
    @Test
    fun fuzzerNeverFalsePositive() {
        val rng = Random(0xC0FFEE)
        val nonces = NonceCache()
        var passed = 0
        var rejected = 0
        repeat(500) {
            val body = ByteArray(rng.nextInt(0, 200)).also { rng.nextBytes(it) }
            val nonce = "n-${rng.nextLong()}"
            val ts = now.toEpochMilli()
            val canon = DaemonAuth.canonical("POST", "/effector/dispatch", ts, nonce, body)
            val sig = DaemonAuth.sign(secret, canon)
            val r = DaemonAuth.verify(secret, "POST", "/effector/dispatch", ts.toString(), nonce, body, sig,
                nonces, now)
            assertEquals(DaemonAuth.Result.Ok, r, "valid sign must pass")
            passed++
        }
        // Now 500 mutated requests — at least ONE field tampered.
        repeat(500) {
            val body = ByteArray(rng.nextInt(0, 200)).also { rng.nextBytes(it) }
            val nonce = "m-${rng.nextLong()}"
            val ts = now.toEpochMilli()
            val canon = DaemonAuth.canonical("POST", "/effector/dispatch", ts, nonce, body)
            val sig = DaemonAuth.sign(secret, canon)
            // Pick one mutation per iteration. Each must reject.
            var mMethod = "POST"
            var mPath = "/effector/dispatch"
            var mTs = ts
            var mBody = body
            var mSig = sig
            when (rng.nextInt(0, 5)) {
                0 -> mMethod = "GET"
                1 -> mPath = "/admin"
                2 -> mTs = ts + 60_000
                3 -> mBody = body.copyOf().also { if (it.isNotEmpty()) it[0] = (it[0].toInt() xor 0xFF).toByte() }
                else -> mSig = "deadbeef".repeat(8)
            }
            val r = DaemonAuth.verify(secret, mMethod, mPath, mTs.toString(), nonce, mBody, mSig,
                nonces, now)
            assertNotEquals(DaemonAuth.Result.Ok, r,
                "mutated request MUST reject (method=$mMethod path=$mPath tsDelta=${mTs - ts})")
            rejected++
        }
        assertEquals(500, passed)
        assertEquals(500, rejected)
    }
}
