package jarvis.tutor

import org.jetbrains.exposed.sql.Database
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

private val PATH_BLOCKLIST = listOf(
    Regex(""".*/\.ssh(/|$).*"""),
    Regex(""".*/\.git(/|$).*"""),
    Regex(""".*\.env(\.[a-zA-Z0-9_]+)?$"""),
    Regex(""".*\.(key|pem)$"""),
    Regex(""".*/\.aws(/|$).*"""),
    Regex(""".*/\.config(/|$).*"""),
    Regex(""".*/\.kube(/|$).*"""),
)

sealed class ValidationResult {
    object Pass : ValidationResult()
    data class Reject(val outcome: Outcome, val reason: String) : ValidationResult()
}

class NonceCache(private val capacity: Int = 1000) {
    private val q = ConcurrentLinkedQueue<String>()
    private val set = java.util.Collections.synchronizedSet(HashSet<String>())
    fun seen(nonce: String): Boolean = set.contains(nonce)
    fun record(nonce: String) {
        if (set.add(nonce)) {
            q.add(nonce)
            while (set.size > capacity) {
                val removed = q.poll() ?: break
                set.remove(removed)
            }
        }
    }
}

class EffectorValidator(
    private val db: Database,
    private val nonces: NonceCache,
) {
    private val grantRepo = TrustGrantRepo(db)

    fun validate(userId: String, req: ApplyEditRequest, currentDocVersion: String): ValidationResult {
        // 1. Hardcoded blocklist (always wins).
        if (PATH_BLOCKLIST.any { it.matches(req.targetUri) }) {
            return ValidationResult.Reject(Outcome.PATH_DENIED, "path on blocklist")
        }
        // 2. Grant exists, active, owned by this user, op allowed.
        val now = Instant.now()
        val grant = grantRepo.findActive(req.grantId, now)
            ?: return ValidationResult.Reject(Outcome.REJECTED, "grant missing/expired/revoked")
        if (grant.userId != userId) return ValidationResult.Reject(Outcome.REJECTED, "grant userId mismatch")
        if (EffectorType.APPLY_EDIT !in grant.ops)
            return ValidationResult.Reject(Outcome.REJECTED, "op not in grant")
        // 3. Path within grant scope (glob).
        if (!grant.scope.any { matchGlob(req.targetUri, it) }) {
            return ValidationResult.Reject(Outcome.PATH_DENIED, "outside grant scope")
        }
        // 4. expectedDocVersion matches.
        if (req.expectedDocVersion != currentDocVersion) {
            return ValidationResult.Reject(Outcome.STALE_DOC,
                "expected ${req.expectedDocVersion} got $currentDocVersion")
        }
        // 5. Nonce not seen before.
        if (nonces.seen(req.nonce)) {
            return ValidationResult.Reject(Outcome.REJECTED, "nonce replay")
        }
        // 6. Consume one slot from grant.
        if (!grantRepo.tryConsume(req.grantId)) {
            return ValidationResult.Reject(Outcome.REJECTED, "grant maxCalls exhausted")
        }
        nonces.record(req.nonce)
        return ValidationResult.Pass
    }

    /**
     * Minimal glob matcher: ** = any path, * = single segment, literal text otherwise.
     */
    private fun matchGlob(target: String, glob: String): Boolean {
        val regex = buildString {
            append('^')
            var i = 0
            while (i < glob.length) {
                val c = glob[i]
                when {
                    c == '*' && i + 1 < glob.length && glob[i + 1] == '*' -> { append(".*"); i += 2 }
                    c == '*' -> { append("[^/]*"); i++ }
                    c == '.' || c == '(' || c == ')' || c == '+' || c == '?' || c == '|' -> {
                        append('\\').append(c); i++
                    }
                    else -> { append(c); i++ }
                }
            }
            append('$')
        }
        return Regex(regex).matches(target)
    }
}
