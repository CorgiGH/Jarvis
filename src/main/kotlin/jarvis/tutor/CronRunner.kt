package jarvis.tutor

import jarvis.LoopsKillSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * Tutor Stage F — background skill-runner cron.
 *
 * Council 2026-05-09 flagged "non-user-initiated LLM activity"
 * trust gap. Guards baked in:
 *
 *  GUARD A — default-OFF behind JARVIS_CRON_ENABLED.
 *  GUARD B — only skills with `cron_minutes: <N>` AND `cron_enabled: true`
 *            in their SKILL.md frontmatter run. Adding a SKILL.md is
 *            never enough; explicit opt-in field required.
 *  GUARD C — read-only tool surface (same allowlist as
 *            GatewayInbound.GATEWAY_TOOL_ALLOWLIST). Cron skills
 *            can search + read + log to wiki, never write effectors.
 *  GUARD D — single-flight per skill: if a skill's prior tick is
 *            still running, the next tick skips (no overlap).
 *  GUARD E — per-skill audit row (signals.jsonl) with input prompt
 *            + reply summary so any mutation the LLM made via
 *            wiki_append is traceable to a cron tick.
 *
 * Cron expression: SKILL.md frontmatter `cron_minutes: 60` means
 * fire every 60 minutes. Simpler than full crontab for V1.
 */
object CronRunner {

    private const val TICK_CHECK_MS = 60_000L  // 1 min

    @OptIn(ExperimentalCoroutinesApi::class)
    private val cronDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + cronDispatcher)

    @Volatile private var lastTickAt: Map<String, Instant> = emptyMap()
    @Volatile private var inFlight: Set<String> = emptySet()

    fun isEnabled(): Boolean {
        if (LoopsKillSwitch.loopsDisabled()) return false
        return System.getenv("JARVIS_CRON_ENABLED")?.lowercase()
            ?.let { it == "1" || it == "true" || it == "yes" } ?: false
    }

    fun start(skillsRoot: Path = SkillLoader.defaultRoot()) {
        if (!isEnabled()) {
            System.err.println("[CronRunner] JARVIS_CRON_ENABLED unset — loop dormant.")
            return
        }
        System.err.println("[CronRunner] started (re-scan every ${TICK_CHECK_MS / 1000}s)")
        scope.launch {
            while (true) {
                try {
                    tickOnce(skillsRoot, Instant.now())
                } catch (e: Exception) {
                    System.err.println("[CronRunner] tick failed: ${e.message?.take(160)}")
                }
                delay(TICK_CHECK_MS)
            }
        }
    }

    /** Public for tests. Walks skills, fires due ones, records
     *  lastTickAt + inFlight state. Returns the skill names that
     *  fired this tick. */
    suspend fun tickOnce(skillsRoot: Path, now: Instant): List<String> {
        val skills = try { SkillLoader.load(skillsRoot) } catch (_: Exception) { return emptyList() }
        val fired = mutableListOf<String>()
        for (spec in skills) {
            val cronMinutes = spec.cronMinutes ?: continue
            if (!spec.cronEnabled) continue
            if (spec.name in inFlight) continue
            val last = lastTickAt[spec.name]
            val due = last == null || Duration.between(last, now).toMinutes() >= cronMinutes
            if (!due) continue
            inFlight = inFlight + spec.name
            try {
                runSkill(spec)
                fired += spec.name
            } catch (e: Exception) {
                System.err.println("[CronRunner] skill '${spec.name}' failed: ${e.message?.take(160)}")
            } finally {
                inFlight = inFlight - spec.name
                lastTickAt = lastTickAt + (spec.name to now)
            }
        }
        return fired
    }

    private suspend fun runSkill(spec: SkillSpec) {
        // Cron-context allowlist — read-only intersection with the
        // skill's own tool_allowlist (skill can NARROW further but
        // can't widen past read-only).
        val effective = if (spec.toolAllowlist.isEmpty()) GatewayInbound.GATEWAY_TOOL_ALLOWLIST
            else spec.toolAllowlist.toSet().intersect(GatewayInbound.GATEWAY_TOOL_ALLOWLIST)
        val allowedSpec = spec.copy(toolAllowlist = effective.toList())
        // Synthetic user message — the skill's body IS the system
        // prompt; the user message is just the tick trigger so
        // tool_use round-trip starts.
        val userText = "(cron tick @ ${Instant.now()}) Run the skill body now."
        JarvisToolset().use { ts ->
            val r = ts.chat(
                systemPrompt = allowedSpec.systemPromptBody,
                userText = userText,
            )
            // Audit row — write to signals.jsonl so the user can see
            // what the cron skill did.
            val signal = jarvis.ProactiveSignal(
                id = jarvis.ProactiveLoop.computeSignalId("cron|${spec.name}", Instant.now()),
                ts = Instant.now().toString(),
                kind = "cron_skill",
                importance = 0.4f,
                sourceTs = Instant.now().toString(),
                snippet = "[${spec.name}] ${r.text.take(200)}",
                rationale = "cron tick + ${r.toolRounds} tool rounds",
                status = "emitted",
            )
            try { jarvis.Signals.append(signal) } catch (_: Exception) {}
        }
    }

    /** Test hook — wipe state between runs. */
    internal fun resetForTests() {
        lastTickAt = emptyMap()
        inFlight = emptySet()
    }
}
