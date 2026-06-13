package jarvis.content

import jarvis.tutor.verify.EquationSyntax
import jarvis.tutor.verify.ExtractionConfidence
import kotlinx.serialization.Serializable

@Serializable
data class ValidationIssue(
    val severity: String,   // "error" | "warning"
    val rule: String,       // "cycle" | "orphan" | "exam_weight" | "bilingual" | "verbatim_source" | "viz_reference"
    val subject: String,
    val detail: String,
)

@Serializable
data class ValidationReport(
    val ok: Boolean,
    val disclaimer: String,
    val issues: List<ValidationIssue>,
)

/**
 * Structural validator for the content/ corpus. Per council 1779311876 amendment 1,
 * this validator proves the graph is well-FORMED (acyclic, connected, weights sum,
 * bilingual, attributed) — it does NOT prove the content is TRUE. Semantic
 * groundedness is the human curator's job (and the Gate 5 sidecar's).
 */
object ContentValidator {

    const val DISCLAIMER = "structural checks only — content not groundedness-verified"

    private val WS_RE = Regex("\\s+")
    private val COMBINING_RE = Regex("\\p{M}+")

    /** Three-color DFS cycle detection over the prerequisite graph (edge prereq -> kc). */
    fun detectCycles(sub: LoadedSubject): List<ValidationIssue> {
        val adj: Map<String, List<String>> = sub.edges.groupBy({ it.prereq }, { it.kc })
        val color = HashMap<String, Int>() // 0 = white, 1 = gray, 2 = black
        val nodes = (sub.kcs.map { it.id } + sub.edges.flatMap { listOf(it.kc, it.prereq) }).toSet()
        val issues = mutableListOf<ValidationIssue>()

        fun dfs(node: String): Boolean {
            color[node] = 1
            for (next in adj[node].orEmpty()) {
                val c = color[next] ?: 0
                if (c == 1) return true            // back-edge to a gray node = cycle
                if (c == 0 && dfs(next)) return true
            }
            color[node] = 2
            return false
        }

        for (n in nodes) {
            if ((color[n] ?: 0) == 0 && dfs(n)) {
                issues += ValidationIssue("error", "cycle", sub.subject,
                    "prerequisite graph contains a cycle reachable from KC '$n'")
                break // one cycle issue is enough — the curator fixes and re-runs
            }
        }
        return issues
    }

    /**
     * Orphan = a KC that is NOT a tier-1 root and has no prerequisite chain of
     * <= [MAX_HOPS] edges reaching a tier-1 KC. Walks edges kc -> prereq.
     */
    const val MAX_HOPS = 8

    fun detectOrphans(sub: LoadedSubject): List<ValidationIssue> {
        val tier1: Set<String> = sub.kcs.filter { it.tier == 1 }.map { it.id }.toSet()
        // prereqsOf[x] = the KCs that x directly depends on.
        val prereqsOf: Map<String, List<String>> = sub.edges.groupBy({ it.kc }, { it.prereq })
        val issues = mutableListOf<ValidationIssue>()

        for (kc in sub.kcs) {
            if (kc.id in tier1) continue
            // BFS up the prerequisite chain, bounded by MAX_HOPS.
            val seen = hashSetOf(kc.id)
            var frontier = listOf(kc.id)
            var reached = false
            var hop = 0
            while (frontier.isNotEmpty() && hop < MAX_HOPS && !reached) {
                val next = mutableListOf<String>()
                for (n in frontier) for (p in prereqsOf[n].orEmpty()) {
                    if (p in tier1) { reached = true }
                    if (seen.add(p)) next += p
                }
                frontier = next
                hop++
            }
            if (!reached) {
                issues += ValidationIssue("error", "orphan", sub.subject,
                    "KC '${kc.id}' has no prerequisite path (<= $MAX_HOPS hops) to a tier-1 root")
            }
        }
        return issues
    }

    const val WEIGHT_TOLERANCE = 0.02

    /** Per spec §13: the exam_weight of a subject's KCs must sum to 1.0 +/- 0.02. */
    fun checkExamWeights(sub: LoadedSubject): List<ValidationIssue> {
        if (sub.kcs.isEmpty()) return emptyList()
        val sum = sub.kcs.sumOf { it.exam_weight }
        if (kotlin.math.abs(sum - 1.0) > WEIGHT_TOLERANCE) {
            return listOf(ValidationIssue("error", "exam_weight", sub.subject,
                "exam_weight sum is %.4f — must be 1.0 +/- %.2f".format(sum, WEIGHT_TOLERANCE)))
        }
        return emptyList()
    }

    /** Every KC and misconception must carry non-blank RO and EN text. */
    fun checkBilingual(sub: LoadedSubject): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        for (kc in sub.kcs) {
            if (kc.name_ro.isBlank() || kc.name_en.isBlank()) {
                issues += ValidationIssue("error", "bilingual", sub.subject,
                    "KC '${kc.id}' missing name_ro or name_en")
            }
        }
        for (m in sub.misconceptions) {
            if (m.label_ro.isBlank() || m.label_en.isBlank()) {
                issues += ValidationIssue("error", "bilingual", sub.subject,
                    "misconception '${m.id}' missing label_ro or label_en")
            }
        }
        return issues
    }

    /** E3: a requires_visual KC must name a viz_id present in content/viz-ids.yaml. */
    fun checkVizReferences(sub: LoadedSubject, validVizIds: Set<String>): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        for (kc in sub.kcs) {
            if (!kc.requires_visual) continue
            val vid = kc.viz_id
            when {
                vid == null -> issues += ValidationIssue("error", "viz_reference", sub.subject,
                    "KC '${kc.id}' is requires_visual but has no viz_id")
                vid !in validVizIds -> issues += ValidationIssue("error", "viz_reference", sub.subject,
                    "KC '${kc.id}' viz_id '$vid' is not in content/viz-ids.yaml")
            }
        }
        return issues
    }

    /**
     * Runs every structural check across all [subjects]. [sourceText] resolves a
     * doc id to its extracted source text (see checkVerbatimSources).
     * [knownInstances] resolves a subject name to its (instance_id → family_id) map
     * (pass `ContentRepo.loadVizInstances(subject).associate { it.id to it.family_id }`).
     * No silent default — every caller must thread the real resolver so INV-5.5 is
     * never vacuous by a missing-parameter default.
     * ok = true iff there are no "error"-severity issues; warnings do not fail.
     */
    fun validate(
        subjects: List<LoadedSubject>,
        validVizIds: Set<String> = emptySet(),
        knownInstances: (subject: String) -> Map<String, String> = { emptyMap() },
        sourceText: (doc: String) -> String?,
    ): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()
        for (sub in subjects) {
            issues += detectCycles(sub)
            issues += detectOrphans(sub)
            issues += checkExamWeights(sub)
            issues += checkBilingual(sub)
            issues += checkVerbatimSources(sub, sourceText)
            issues += checkVizReferences(sub, validVizIds)
            issues += checkStrictGrounding(sub)
            issues += checkVerificationStatusEnum(sub)
            issues += checkConceptTypeEnum(sub)
            issues += checkTautologicalInvariants(sub)
            issues += checkExtractionLegibility(sub, sourceText)
            issues += checkFigureBindings(sub, knownInstances)
        }
        val ok = issues.none { it.severity == "error" }
        return ValidationReport(ok = ok, disclaimer = DISCLAIMER, issues = issues)
    }

    /**
     * H11 — Strict-grounding check (CHANGE 3 / B4).
     *
     * When grounding_tier == "strict", ALL of the following are required:
     *   - anchored span present (at least one ref with span != null)
     *   - vision-confirmed (all refs with spans must have provenance == "vision-confirmed")
     *   - invariant != null
     *   - grader_rules non-empty
     *
     * The span + provenance conditions are ALREADY enforced in checkVerbatimSources; this
     * function adds the invariant + grader_rules conditions and emits ALL failing conditions
     * TOGETHER in ONE aggregated issue (H11 "additive not double-error").
     *
     * Non-strict KCs are unaffected.
     */
    fun checkStrictGrounding(sub: LoadedSubject): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        for (kc in sub.kcs) {
            if (kc.grounding_tier != "strict") continue
            val failing = mutableListOf<String>()
            // Check invariant
            if (kc.invariant == null) failing += "invariant is null (required for strict KC)"
            // Check grader_rules
            if (kc.grader_rules.isEmpty()) failing += "grader_rules is empty (required for strict KC)"
            if (failing.isNotEmpty()) {
                issues += ValidationIssue(
                    severity = "error",
                    rule = "strict_grounding",
                    subject = sub.subject,
                    detail = "KC '${kc.id}': strict grounding missing: ${failing.joinToString("; ")}",
                )
            }
        }
        return issues
    }

    /**
     * H11 — verification_status enum check (CHANGE 3 / B4).
     *
     * Every KC and Misconception must have a verification_status that is one of the
     * three AUTHORED literals: {unverified, pending, uncertain}.
     *
     * F4-author: `faithful` is NO LONGER authorable. It certifies an audit that actually ran
     * (two families agree + non-LLM-leg pass + span round-trip, §2.5) and is earned ONLY at runtime
     * via `VerificationRunner.audit` writing the B8 `kc_verification_status` row. Authoring it would
     * be an unearned trust claim with zero legs run, so the validator rejects it here exactly like
     * the runtime-only `failed`.
     */
    fun checkVerificationStatusEnum(sub: LoadedSubject): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        for (kc in sub.kcs) {
            if (kc.verification_status !in AUTHORED_VERIFICATION_STATUSES) {
                issues += ValidationIssue(
                    severity = "error",
                    rule = "verification_status_enum",
                    subject = sub.subject,
                    detail = "KC '${kc.id}': verification_status='${kc.verification_status}' " +
                        "is not an authored literal (must be one of $AUTHORED_VERIFICATION_STATUSES)",
                )
            }
        }
        for (m in sub.misconceptions) {
            if (m.verification_status !in AUTHORED_VERIFICATION_STATUSES) {
                issues += ValidationIssue(
                    severity = "error",
                    rule = "verification_status_enum",
                    subject = sub.subject,
                    detail = "misconception '${m.id}': verification_status='${m.verification_status}' " +
                        "is not an authored literal (must be one of $AUTHORED_VERIFICATION_STATUSES)",
                )
            }
        }
        return issues
    }

    /**
     * Plan-2 Task 1 (spec §3.1) — concept_type load-time validation.
     *
     * When a KC's [KnowledgeConcept.concept_type] is non-null it MUST be a valid wire literal
     * (ConceptType.fromWire non-null). Null is ALLOWED here; Task 4 tightens it to required-for-all
     * after the YAML backfill. Localized author/audit check — ZERO serve/sync/hash ripple (concept_type
     * does NOT feed ContentReconcile.claimsFor / kcContentHashOf, plan core §0.3).
     */
    fun checkConceptTypeEnum(sub: LoadedSubject): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val validLiterals = ConceptType.entries.joinToString(", ") { ConceptType.wireOf(it) }
        for (kc in sub.kcs) {
            val ct = kc.concept_type
            when {
                ct == null -> issues += ValidationIssue(
                    severity = "error",
                    rule = "concept_type_enum",
                    subject = sub.subject,
                    detail = "KC '${kc.id}': concept_type is required (INV-3.2) " +
                        "— must be one of: $validLiterals",
                )
                ConceptType.fromWire(ct) == null -> issues += ValidationIssue(
                    severity = "error",
                    rule = "concept_type_enum",
                    subject = sub.subject,
                    detail = "KC '${kc.id}': concept_type='$ct' is not a valid literal " +
                        "(must be one of: $validLiterals)",
                )
            }
        }
        return issues
    }

    /**
     * Plan 4b Task 5 (spec §5.5 / INV-5.5) — every FigureBinding in a beat reveal must:
     *   (a) resolve to a known viz instance id (instance-existence leg), AND
     *   (b) have `binding.family_id == instance.family_id` (family-REGISTRY match leg at admission).
     *
     * Both halves are required: without (b), a family_id-mismatched/typo'd binding passes admission,
     * hits the frontend registry-miss fallback, and ships a silent text-only ghost figure with every
     * gate green (the ghost-component class — §0.9A). The frontend dispatches on the BINDING's
     * family_id; the harness keys off the INSTANCE's family_id — a mismatch is structurally invisible
     * to both unless this check catches it at admission.
     *
     * ADDITIVE author/audit check; vacuous-true when no beat binds a figure.
     * [knownInstances] returns a (instance_id → family_id) map for the subject
     * (caller passes ContentRepo.loadVizInstances id→family_id). No compile-forcing default —
     * every caller must thread the real map; a default that silently passes would defeat INV-5.5.
     * ZERO serve/sync/hash ripple (beats don't feed claimsFor — pinned by FigureBindingHashNoRippleTest).
     */
    fun checkFigureBindings(
        sub: LoadedSubject,
        knownInstances: (String) -> Map<String, String>,
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val known = knownInstances(sub.subject) // instance_id → family_id
        for (kc in sub.kcs) {
            for ((lang, beats) in kc.beats) {
                val fb = beats.reveal?.figure ?: continue
                val instanceFamilyId = known[fb.instance_id]
                when {
                    instanceFamilyId == null -> {
                        issues += ValidationIssue(
                            severity = "error",
                            rule = "figure_binding",
                            subject = sub.subject,
                            detail = "KC '${kc.id}' (beats[$lang]) binds figure family='${fb.family_id}' " +
                                "instance_id='${fb.instance_id}', which is not a known viz instance " +
                                "(known: ${known.keys.sorted().joinToString(", ").ifEmpty { "<none>" }})",
                        )
                    }
                    instanceFamilyId != fb.family_id -> {
                        issues += ValidationIssue(
                            severity = "error",
                            rule = "figure_binding",
                            subject = sub.subject,
                            detail = "KC '${kc.id}' (beats[$lang]) binds family_id='${fb.family_id}' " +
                                "but instance '${fb.instance_id}' has family_id='$instanceFamilyId' — " +
                                "family mismatch: FigureReveal dispatches on the binding's family_id and " +
                                "would hit the registry-miss fallback, silently degrading to text (§0.9A)",
                        )
                    }
                }
            }
        }
        return issues
    }

    /**
     * The three authored verification_status literals. F4-author: `faithful` is NOT authorable
     * (earned only at runtime by an audit) and `failed` is runtime-only — both are rejected in YAML.
     */
    val AUTHORED_VERIFICATION_STATUSES: Set<String> = setOf("unverified", "pending", "uncertain")

    /**
     * D4 — reject TAUTOLOGICAL (vacuously-true) invariants at AUTHOR/AUDIT time (PC-side).
     *
     * The hole: a trivially-true authored equation (`t = t`, `0 = 0`) passes the SymPy leg's
     * `splitEquation` (which rejects `<=,>=,!=,==` but NOT an `lhs == rhs` identity) ⇒
     * `simplify(t - t) == 0` ⇒ `ran=true, pass=true`. "SymPy ran && passed" is satisfied VACUOUSLY —
     * a check that cannot fail certifies nothing. Reject it before it can certify, exactly like a
     * tautology guard in a theorem prover.
     *
     * PRIMARY guard (HARD ERROR) = the conservative SYNTACTIC-identity reject: the invariant is a
     * plain `lhs = rhs` equation AND `lhs.trim() == rhs.trim()`. This never false-rejects a meaningful
     * identity (`|n|_unif = 1` is NOT `lhs==rhs`-identical; `2x = x + x` is not either) — only the
     * literally-vacuous `t = t` / `0 = 0` shape. The broader `simplify`-trivial reject (`2x = x + x`)
     * is deliberately NOT a hard fail here (it would false-reject authored content); it stays a
     * runtime SymPy concern + the `bothSupported` requirement already blunts weaponization (LOW sev).
     *
     * Localized entirely to author/audit — ZERO serve/sync/hash ripple, no frozen signature moves.
     */
    fun checkTautologicalInvariants(sub: LoadedSubject): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        fun checkOne(owner: String, id: String, invariant: String?) {
            val inv = invariant?.trim() ?: return
            if (inv.isEmpty()) return
            // MED-b: delegate the split to the ONE shared EquationSyntax.split (was a copy-paste of
            // SymPyLeg.splitEquation). The D4 tautology test then layers lhs==rhs ON TOP of the split.
            val (lhs, rhs) = EquationSyntax.split(inv) ?: return
            if (lhs == rhs) {
                issues += ValidationIssue(
                    severity = "error",
                    rule = "tautological_invariant",
                    subject = sub.subject,
                    detail = "$owner '$id': invariant '$invariant' is tautological (lhs == rhs after trim) — " +
                        "a vacuously-true equation certifies nothing; author a non-trivial invariant",
                )
            }
        }
        for (kc in sub.kcs) checkOne("KC", kc.id, kc.invariant)
        return issues
    }

    /**
     * P2-RULE1 — wire [ExtractionConfidence] into the validateContent chokepoint (master-impl-plan-v2
     * §196). A garbled-but-PRESENT `_sources` extraction (mojibake / binary-shredded `pdftotext`
     * output) must ERROR here, BEFORE any KC is authored/reconciled over it — otherwise a garbled doc
     * validates GREEN and a KC gets stamped against text no human (or re-locator) can re-ground.
     *
     * For each DISTINCT doc referenced by a KC or misconception source ref, resolve its source text
     * via [sourceText]. When the text is non-null and non-blank, run [ExtractionConfidence.reason]:
     * a non-null reason (score < [ExtractionConfidence.MIN_CONFIDENCE]) is an `error`-severity issue
     * naming the doc + the reason + the score. A null source text is NOT a garble error here — that is
     * the "unverifiable" verbatim-source case (a warning in [checkVerbatimSources]); garble only fires
     * on present-but-illegible text. Each distinct doc is reported at most once.
     *
     * Localized PC-side author/audit check — ZERO serve/sync/hash ripple (mirrors the shape of
     * [checkTautologicalInvariants]). Because validateContent gates Stage-9 reconcile (ContentCli),
     * a garbled doc anywhere in the corpus blocks KC authoring, not just one enumerated ref.
     */
    fun checkExtractionLegibility(
        sub: LoadedSubject,
        sourceText: (doc: String) -> String?,
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val docs = LinkedHashSet<String>()
        for (kc in sub.kcs) for (ref in kc.source) docs += ref.doc
        for (m in sub.misconceptions) for (ref in m.source) docs += ref.doc
        for (doc in docs) {
            val text = sourceText(doc) ?: continue        // null = unverifiable (warning elsewhere), not garble
            if (text.isBlank()) continue                   // blank handled by checkVerbatimSources; not a garble emit here
            val reason = ExtractionConfidence.reason(text) ?: continue
            val score = ExtractionConfidence.score(text)
            issues += ValidationIssue(
                severity = "error",
                rule = "garbled_extraction",
                subject = sub.subject,
                detail = "source '$doc': extraction is illegible ($reason, " +
                    "score %.2f < %.2f) — re-extract before authoring a KC over it"
                        .format(score, ExtractionConfidence.MIN_CONFIDENCE),
            )
        }
        return issues
    }

    private fun normalizeWs(s: String): String = s.replace(WS_RE, " ").trim()

    /** Strip Unicode combining marks (Romanian ș/ț/ă/â/î → s/t/a/a/i). */
    private fun stripDiacritics(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(COMBINING_RE, "")

    private fun fold(s: String): String = normalizeWs(stripDiacritics(s)).lowercase()

    /**
     * E2 grounding gate. Per source ref:
     *  - absent source text → warning (cannot verify);
     *  - span present → diacritic-EXACT confirm of the raw slice vs the quote; mismatch/oob → error;
     *  - span absent → diacritic-insensitive candidate-find: not found → error;
     *    found → error if the KC is grounding_tier=strict (span required), else warning;
     *  - empty source list → error;
     *  - grounding_tier=strict: every ref must be provenance=="vision-confirmed", else error.
     */
    fun checkVerbatimSources(
        sub: LoadedSubject,
        sourceText: (doc: String) -> String?,
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()

        fun err(owner: String, id: String, msg: String) =
            issues.add(ValidationIssue("error", "verbatim_source", sub.subject, "$owner '$id': $msg"))
        fun warn(owner: String, id: String, msg: String) =
            issues.add(ValidationIssue("warning", "verbatim_source", sub.subject, "$owner '$id': $msg"))

        fun checkOne(owner: String, id: String, refs: List<SourceRef>, strict: Boolean) {
            if (refs.isEmpty()) { err(owner, id, "has no source attribution"); return }
            for (ref in refs) {
                val text = sourceText(ref.doc)
                if (text == null) { warn(owner, id, "source '${ref.doc}' has no extracted text on disk — quote unverifiable"); continue }
                val span = ref.span
                if (span != null) {
                    val slice = SourceOfRecord.slice(text, span)
                    if (slice == null) { err(owner, id, "span ${span.start}..${span.end} out of bounds in '${ref.doc}'"); continue }
                    if (slice != ref.quote) { err(owner, id, "quote does not match raw span in '${ref.doc}' (diacritic-exact)"); continue }
                    // span confirmed — now enforce strict provenance (only meaningful when the span matches)
                    if (strict && ref.provenance != "vision-confirmed")
                        err(owner, id, "strict KC requires provenance=vision-confirmed on '${ref.doc}' (got '${ref.provenance}')")
                } else {
                    if (!fold(text).contains(fold(ref.quote))) { err(owner, id, "quote not found in source '${ref.doc}'"); continue }
                    if (strict) { err(owner, id, "strict KC requires an anchored span — none on ref to '${ref.doc}'"); continue }
                    warn(owner, id, "ungrounded span — quote found by fuzzy match only in '${ref.doc}'")
                }
            }
        }

        for (kc in sub.kcs) checkOne("KC", kc.id, kc.source, kc.grounding_tier == "strict")
        for (m in sub.misconceptions) checkOne("misconception", m.id, m.source, false)
        return issues
    }
}
