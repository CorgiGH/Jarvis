package jarvis.content

import org.jetbrains.exposed.sql.Database
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.system.exitProcess

object ContentCli {

    /** Pure read-only validation — loads the manifest + all subjects and runs ContentValidator.
     *  No file writes. Safe to call from an HTTP GET handler. */
    fun validateOnly(contentDir: Path): ValidationReport {
        val repo = ContentRepo(contentDir)
        val manifest = repo.loadManifest()
        val subjects = manifest.subjects.map { repo.loadSubject(it.id) }
        val vizIds = repo.loadVizIds()
        // Build (instance_id → family_id) map per subject for INV-5.5 checkFigureBindings.
        // The map is built eagerly per call to knownInstances — acceptable for CLI/reconcile paths.
        return ContentValidator.validate(
            subjects,
            vizIds,
            knownInstances = { subject ->
                repo.loadVizInstances(subject).associate { it.id to it.family_id }
            },
        ) { doc ->
            // doc ids are unique across subjects in practice; search each subject's _sources.
            manifest.subjects.firstNotNullOfOrNull { repo.sourceText(it.id, doc) }
        }
    }

    /** Regenerates edges.mmd for every subject that has an edges.yaml. */
    fun regenerateMermaid(contentDir: Path) {
        val repo = ContentRepo(contentDir)
        val manifest = repo.loadManifest()
        for (entry in manifest.subjects) {
            val edgesYaml = contentDir.resolve(entry.id).resolve("edges.yaml")
            if (edgesYaml.exists()) {
                val loaded = repo.loadSubject(entry.id)
                val mmd = MermaidMirror.render(EdgesFile(entry.id, loaded.edges))
                contentDir.resolve(entry.id).resolve("edges.mmd").writeText(mmd + "\n")
            }
        }
    }

    /** CLI entry point: regenerates edges.mmd files then runs pure validation.
     *  Behaviour is identical to the original runValidation. */
    fun runValidation(contentDir: Path): ValidationReport {
        regenerateMermaid(contentDir)
        return validateOnly(contentDir)
    }

    /**
     * curate-tutor **Stage-9 reconcile**. Runs the structural [validateOnly] gate FIRST — a malformed
     * corpus (any `error`-severity issue) is NEVER reconciled into the audit queue (anti-contamination,
     * council R2) — then, in ONE flow, runs the two PC-side reconcile legs over the loaded subjects:
     *
     *  1. [ContentReconcile.reconcileSourceSpans] — the **D1 source-edit WATCHER**. For every audited
     *     KC carrying a stored `source_span_hash`, recompute it from the LIVE `_sources` bytes (via the
     *     [ContentRepo.sourceText] resolver, mirroring [jarvis.tutor.verify.VerifyContentCli]); a real
     *     fold-different edit NULLs `content_hash` + `source_span_hash` and re-pends the KC so the serve
     *     gate fails CLOSED until a fresh audit. Idempotent on whitespace-only churn (H10-safe except
     *     the council-approved narrowed exception: regress faithful→pending ONLY on a REAL source edit).
     *
     *  2. [ContentReconcile.reconcile] — the **Stage-9 setPending leg**. Emits each KC's
     *     [jarvis.tutor.verify.VerificationClaim]s and sets every still-unaudited KC's
     *     `kc_verification_status` to `pending` (UNVERIFIED→PENDING, DIRECTLY). Idempotent + H10-safe
     *     (never regresses a faithful KC). Runs AFTER the watcher so a KC the watcher just re-pended is
     *     left at `pending` (the watcher set it; setPending leaves any `pending` row untouched).
     *
     * Order: validate → reconcileSourceSpans (NULL+re-pend edited) → reconcile (setPending). Both legs
     * are PC-SIDE ONLY (D7): the watcher reads `_sources` bytes that exist only on the authoring PC.
     *
     * @return the [ContentReconcile.ReconcileReport] (emitted claims + promoted kcIds) from leg 2.
     * @throws IllegalStateException if [validateOnly] reports any error-severity issue.
     */
    fun reconcile(
        contentDir: Path,
        db: Database,
        clock: () -> Instant = Instant::now,
    ): ContentReconcile.ReconcileReport {
        val report = validateOnly(contentDir)
        check(report.ok) {
            "Stage-9 reconcile aborted: content validation has " +
                "${report.issues.count { it.severity == "error" }} error(s) — fix the corpus first."
        }
        val repo = ContentRepo(contentDir)
        val manifest = repo.loadManifest()
        val subjects = manifest.subjects.map { repo.loadSubject(it.id) }

        // Leg 1 — D1 source-edit watcher. The live `_sources/{doc}.md` resolver mirrors how
        // VerifyContentCli builds rawSourceFor (VerifyContentCli.kt:136-141): (subject, doc) ->
        // repo.sourceText(subject, doc). An absent source returns null ⇒ the recomputed hash differs
        // ⇒ the KC is invalidated (the correct fail-closed move).
        ContentReconcile.reconcileSourceSpans(
            subjects = subjects,
            db = db,
            rawSourceFor = { subject, doc -> repo.sourceText(subject, doc) },
            clock = clock,
        )

        // Leg 2 — Stage-9 setPending (UNVERIFIED→PENDING). Returns the claim/promotion report.
        return ContentReconcile.reconcile(subjects, db, clock)
    }
}

fun main(args: Array<String>) {
    val dir = Path.of(args.firstOrNull() ?: "content")
    if (!dir.exists()) {
        System.err.println("[validateContent] content dir not found: $dir")
        exitProcess(2)
    }
    val report = ContentCli.runValidation(dir)
    println("[validateContent] ${report.disclaimer}")
    for (issue in report.issues) {
        println("  [${issue.severity.uppercase()}] ${issue.subject}/${issue.rule}: ${issue.detail}")
    }
    if (report.ok) {
        println("[validateContent] OK — ${report.issues.size} warning(s), 0 errors")
        exitProcess(0)
    } else {
        val errs = report.issues.count { it.severity == "error" }
        System.err.println("[validateContent] FAILED — $errs error(s)")
        exitProcess(1)
    }
}
