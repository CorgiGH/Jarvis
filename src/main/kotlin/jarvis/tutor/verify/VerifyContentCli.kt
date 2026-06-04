package jarvis.tutor.verify

import jarvis.content.ContentRepo
import jarvis.content.ContentReconcile
import jarvis.tutor.TutorDb
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Batch-5 — the `verifyContent` Gradle task entry point (master-plan H6).
 *
 * OWNER / MANUAL OFFLINE ONLY. This is the trust-net's offline batch: it re-derives every authored
 * KC claim against two LLM families + a non-LLM leg + the span↔claim round-trip and writes the
 * `verification_audit` + `kc_verification_status` (B8) tables. It NEVER runs on a request path and
 * is NOT wired into `check` (H6) — running it requires a LIVE relay + OpenRouter key, which CI does
 * not have.
 *
 * FAIL-LOUD (H4): the task ABORTS with a non-zero exit BEFORE touching any DB if a required family
 * env var is missing — never silently degrades to a one-family (gameable) audit. The two required
 * families are:
 *   - RELAY      — `JARVIS_RELAY_URL` + `JARVIS_RELAY_TOKEN`
 *   - OPENROUTER — `OPENROUTER_API_KEY`
 */
object VerifyContentCli {

    /** A required LLM family and the env var(s) that provision it. */
    private data class RequiredFamily(val family: String, val envVars: List<String>)

    private val REQUIRED_FAMILIES = listOf(
        RequiredFamily("RELAY", listOf("JARVIS_RELAY_URL", "JARVIS_RELAY_TOKEN")),
        RequiredFamily("OPENROUTER", listOf("OPENROUTER_API_KEY")),
    )

    /**
     * PURE pre-flight env check (no DB, no network) — the FAIL-LOUD gate (H4). Returns the list of
     * MISSING required family env vars; an empty list means every required family is provisioned.
     * Unit-testable in isolation: the env lookup is injected so the default suite never reads the
     * real process env.
     *
     * @param env resolver: an env-var name -> its value (or null/blank when unset). Defaults to the
     *            real process env. A blank value counts as MISSING (a stray empty export is not a
     *            provisioned family).
     */
    fun missingFamilyEnv(env: (String) -> String? = { System.getenv(it) }): List<String> {
        val missing = ArrayList<String>()
        for (rf in REQUIRED_FAMILIES) {
            for (name in rf.envVars) {
                if (env(name).isNullOrBlank()) missing += name
            }
        }
        return missing
    }

    /**
     * The single human-readable FAIL-LOUD abort message for a set of missing env vars. Pulled out so
     * the test can assert the message names the missing var without exercising `exitProcess`.
     */
    fun abortMessage(missing: List<String>): String =
        "verifyContent ABORTED (FAIL-LOUD, H4): the trust-net requires TWO independent LLM families " +
            "(RELAY + OPENROUTER) — refusing to run a one-family (gameable) audit. Missing env: " +
            missing.joinToString(", ") +
            ". Set them and re-run; this offline batch is owner/manual only and is NOT part of check."

    /**
     * CLI entry. Args (all optional, defaulted):
     *   args[0] = content dir (default: JARVIS_CONTENT_DIR or "content")
     *   args[1] = subject filter (default: every subject in the manifest)
     *
     * FAIL-LOUD: if any required family env var is missing, prints [abortMessage] to stderr and
     * exits 2 BEFORE opening the DB or loading content.
     */
    fun run(args: Array<String>) {
        val missing = missingFamilyEnv()
        if (missing.isNotEmpty()) {
            System.err.println(abortMessage(missing))
            exitProcess(2)
        }

        val contentDir = Path.of(
            args.getOrNull(0)
                ?: System.getProperty("JARVIS_CONTENT_DIR")
                ?: System.getenv("JARVIS_CONTENT_DIR")
                ?: "content",
        )
        val subjectFilter = args.getOrNull(1)

        val dbPath = System.getenv("JARVIS_TUTOR_DB")
            ?: System.getProperty("JARVIS_TUTOR_DB")
            ?: jarvis.Config.tutorDbPath
        val db = TutorDb.connect(dbPath)
        jarvis.tutor.TutorMigration.migrate(db)

        val repo = ContentRepo(contentDir)
        val manifest = repo.loadManifest()
        val subjects = manifest.subjects
            .filter { subjectFilter == null || it.id == subjectFilter }
            .map { repo.loadSubject(it.id) }

        // Build the audit claims (Stage-9 emission, pure) for every KC, then run the offline batch.
        val claims = subjects.flatMap { sub -> sub.kcs.flatMap { ContentReconcile.claimsFor(it) } }
        if (claims.isEmpty()) {
            println("verifyContent: no claims to audit (content dir=$contentDir, subject=$subjectFilter)")
            return
        }

        val runner = liveRunner(db, repo)
        val results = runBlocking { runner.audit(claims) }

        val byStatus = results.groupingBy { it.newStatus.name }.eachCount()
        println("verifyContent: audited ${results.size} claims -> $byStatus")
        for (r in results) {
            println("  ${r.kcId} ${r.claimId} : ${r.priorStatus} -> ${r.newStatus} (${r.outcome})")
        }
    }

    /**
     * Build the real two-family + non-LLM runner from the env-provisioned families. Family A = RELAY
     * (`RelayLlm`); family B = NLI (`NliEntailmentLlm`) — a LOCAL DeBERTa-v3 entailment model run via
     * the py3.12 ProcessBuilder bridge (D6 / D-R12). The NLI swap makes family-B a TRULY-independent
     * family that is not network-throttled and is not a 2nd OpenRouter `:free` LLM (the old false-
     * independence + 429 trap). PC-side / OFFLINE ONLY (D7): the VPS request path (`TrustRoutes` legB)
     * deliberately STAYS OpenRouter — the VPS must not load a model. The non-LLM leg is per-subject
     * (PA ⇒ SymPy, else NONE), and the raw source is the LIVE `_sources/{doc}.md` extraction.
     */
    private fun liveRunner(db: org.jetbrains.exposed.sql.Database, repo: ContentRepo): VerificationRunner =
        VerificationRunner(
            db = db,
            legA = TwoFamilyDeriver.Leg(LegFamily.RELAY, jarvis.RelayLlm()),
            legB = TwoFamilyDeriver.Leg(LegFamily.NLI, jarvis.NliEntailmentLlm()),
            nonLlmLegFor = { subject -> nonLlmLegFor(subject) },
            rawSourceFor = { claim ->
                val doc = claim.source?.doc
                    ?: error("verifyContent: claim ${claim.claimId} has no source.doc to locate against")
                repo.sourceText(claim.subject, doc)
                    ?: error("verifyContent: no _sources/$doc.md for subject ${claim.subject}")
            },
        )
}

/** Gradle `verifyContent` task main. Owner/manual offline only; NOT on check (H6). */
fun main(args: Array<String>) = VerifyContentCli.run(args)
