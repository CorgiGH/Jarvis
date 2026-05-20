package jarvis.tutor

import io.ktor.util.AttributeKey
import jarvis.VisionLlm
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import java.nio.file.Path
import java.security.SecureRandom

data class TutorContext(
    val db: Database,
    val ledgerDir: Path,
    /** Layer B: vision-capable LLM for the screenshot sensor. Null when
     *  no vision provider is configured (e.g. OPENROUTER_API_KEY unset).
     *  Routes that need vision check this and respond 503 cleanly. */
    val visionLlm: VisionLlm? = null,
    /** Gate 2: mailer used by POST /auth/request-link to send magic-link emails.
     *  Defaults to LoggingMailer() (dev fallback) so all existing callers compile
     *  unchanged. Production sets ResendMailer via installTutorContext(). */
    val mailer: MagicLinkMailer = LoggingMailer(),
)
val TutorContextKey = AttributeKey<TutorContext>("TutorContext")

object TutorTypes {
    private val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray()
    private val rng = SecureRandom()
    @Volatile private var lastTs: Long = 0L
    private val lastRand = ByteArray(10)
    private val lock = Any()

    fun ulid(): String {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val rand: ByteArray
            if (now == lastTs) {
                rand = lastRand.copyOf()
                var i = rand.size - 1
                while (i >= 0) {
                    val v = (rand[i].toInt() and 0xFF) + 1
                    rand[i] = (v and 0xFF).toByte()
                    if (v <= 0xFF) break
                    i--
                }
            } else {
                rand = ByteArray(10).also { rng.nextBytes(it) }
                lastTs = now
            }
            System.arraycopy(rand, 0, lastRand, 0, 10)
            return encode(now, rand)
        }
    }

    private fun encode(timestamp: Long, randomness: ByteArray): String {
        require(randomness.size == 10)
        val out = CharArray(26)
        var ts = timestamp
        for (i in 9 downTo 0) {
            out[i] = CROCKFORD[(ts and 0x1F).toInt()]
            ts = ts shr 5
        }
        var idx = 10
        var carry = 0L
        var bits = 0
        for (b in randomness) {
            carry = (carry shl 8) or (b.toLong() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                out[idx++] = CROCKFORD[((carry shr bits) and 0x1F).toInt()]
            }
        }
        return String(out)
    }

    val tutorJson: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }
}
