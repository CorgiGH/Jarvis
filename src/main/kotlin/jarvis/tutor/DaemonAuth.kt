package jarvis.tutor

import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Tutor Layer B1 — daemon HMAC contract.
 *
 * Council R3 fix #2 (council-1778325410-layer-b.md): the spec said
 * "HMAC per call" but didn't bind enough request material to defeat
 * cross-session replay. This module pins the canonical-string format
 * + verification logic on the SERVER side; the Rust daemon mirrors
 * the same canonicalization to sign outgoing requests.
 *
 * Canonical string format (one line, fields tab-separated, no
 * trailing newline):
 *
 *     METHOD\tPATH\tTIMESTAMP_MILLIS\tNONCE\tBODY_SHA256_HEX
 *
 * Signature header: `X-Jarvis-Hmac` = hex(HMAC-SHA256(secret, canonical))
 * Required side headers: `X-Jarvis-Timestamp`, `X-Jarvis-Nonce`.
 *
 * Verification rules (in order — first failure short-circuits):
 *  1. timestamp parses + within ±[ALLOWED_SKEW] of server `now`
 *  2. nonce not in NonceCache (server-side replay defense)
 *  3. body SHA256 matches the digest baked into the canonical string
 *  4. HMAC computed over the canonical string equals the header
 *
 * Defends against:
 *  - replay across sessions: nonce + timestamp window
 *  - request mutation in flight: body sha256 in canonical
 *  - signature reuse on a different route: method + path in canonical
 *  - secret leakage from one user's keychain: per-user secret rotation
 *    via `POST /daemon/rotate-key` (gated on user reconfirm — Layer B1
 *    UI work)
 */
object DaemonAuth {

    /** Maximum clock skew accepted between daemon + server. 30s matches
     *  the council's recommendation; tighter wedges out machines whose
     *  clock has drifted, looser opens the replay window. */
    val ALLOWED_SKEW: Duration = Duration.ofSeconds(30)

    /** SHA-256 of an empty body — used when `body` is absent. */
    private val EMPTY_BODY_DIGEST = sha256Hex(ByteArray(0))

    enum class FailureKind {
        BAD_TIMESTAMP, SKEW, REPLAY, BODY_DIGEST_MISMATCH, BAD_SIGNATURE,
    }

    sealed class Result {
        object Ok : Result()
        data class Fail(val kind: FailureKind, val reason: String) : Result()
    }

    /** Build the canonical string the daemon signs + the server
     *  verifies. Both sides must agree byte-for-byte. */
    fun canonical(
        method: String,
        path: String,
        timestampMillis: Long,
        nonce: String,
        body: ByteArray,
    ): String {
        val bodyHex = if (body.isEmpty()) EMPTY_BODY_DIGEST else sha256Hex(body)
        return "${method.uppercase()}\t$path\t$timestampMillis\t$nonce\t$bodyHex"
    }

    fun sign(secret: ByteArray, canonicalString: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return hex(mac.doFinal(canonicalString.toByteArray(Charsets.UTF_8)))
    }

    /** Server-side verification. [signature] is the hex from the
     *  X-Jarvis-Hmac header; [now] is the server clock for skew check. */
    fun verify(
        secret: ByteArray,
        method: String,
        path: String,
        timestampMillisRaw: String?,
        nonce: String?,
        body: ByteArray,
        signature: String?,
        nonces: NonceCache,
        now: Instant = Instant.now(),
    ): Result {
        val ts = timestampMillisRaw?.toLongOrNull()
            ?: return Result.Fail(FailureKind.BAD_TIMESTAMP, "missing/non-numeric timestamp")
        val skew = Duration.ofMillis(Math.abs(now.toEpochMilli() - ts))
        if (skew > ALLOWED_SKEW) {
            return Result.Fail(FailureKind.SKEW, "skew ${skew.seconds}s > ${ALLOWED_SKEW.seconds}s")
        }
        if (nonce.isNullOrBlank()) {
            return Result.Fail(FailureKind.REPLAY, "missing nonce")
        }
        if (nonces.seen(nonce)) {
            return Result.Fail(FailureKind.REPLAY, "nonce already seen")
        }
        if (signature.isNullOrBlank()) {
            return Result.Fail(FailureKind.BAD_SIGNATURE, "missing signature header")
        }
        val canonicalString = canonical(method, path, ts, nonce, body)
        val expected = sign(secret, canonicalString)
        if (!constantTimeEquals(expected, signature)) {
            return Result.Fail(FailureKind.BAD_SIGNATURE, "hmac mismatch")
        }
        // Side-effect last so a verification failure never burns a nonce.
        nonces.record(nonce)
        return Result.Ok
    }

    private fun sha256Hex(bytes: ByteArray): String =
        hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    /** Compare two hex strings without short-circuiting on first mismatch
     *  to avoid timing-side-channel signature reveal. */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].code xor b[i].code)
        }
        return diff == 0
    }
}
