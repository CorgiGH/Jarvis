package jarvis.content

import jarvis.tutor.KcVerificationStatusTable
import jarvis.tutor.VerificationStatus
import jarvis.tutor.verify.ClaimKind
import jarvis.tutor.verify.EquationSyntax
import jarvis.tutor.verify.VerificationClaim
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.time.Instant

/**
 * curate-tutor **Stage-9 reconcile** (Batch-4, master-plan Area B). The bridge between authored
 * content (`content/{subject}/`) and the runtime trust-net (Phase 2). It runs AFTER
 * `validateContent` succeeds (a malformed corpus must never be reconciled into the audit queue).
 *
 * For each [KnowledgeConcept] the reconcile:
 *  1. emits its [VerificationClaim](s) with a CONTENT-HASH `claimId`
 *     `"{kcId}:{KIND}:{sha256_8(content)}"` (M-CLAIM). Reorder-stable: the id is addressed by its
 *     content, never a positional ordinal, so re-running (or shuffling `grader_rules`) yields the
 *     SAME claimIds.
 *  2. sets `kc_verification_status = pending` DIRECTLY (the §2.4 UNVERIFIED→PENDING edge) — NOT via
 *     [jarvis.tutor.VerificationStatus_.transition]. An unaudited KC simply enters the audit queue
 *     at `pending`; certification to `faithful` happens later, only through `VerificationRunner.audit`.
 *  3. is IDEMPOTENT and FAIL-SAFE (H10): a re-run yields identical claimIds, and it NEVER regresses a
 *     KC whose runtime status is already `faithful` (or `uncertain`/`failed`/`pending`) back to
 *     `pending`. ONLY a missing row or an explicit `unverified` row is promoted to `pending`.
 *
 * The claim emission ([claimsFor]) is PURE (no DB) so it is unit-testable in isolation; only
 * [reconcile] touches the B8 table. NOT a wire type — the offline curate/CLI path consumes the
 * [ReconcileReport].
 */
object ContentReconcile {

    /**
     * The set of claims emitted by reconcile + the set of KCs moved to `pending` this run. NOT a
     * wire type — the curate/CLI path reads it to report what was reconciled.
     */
    data class ReconcileReport(
        /** Every [VerificationClaim] emitted across all reconciled KCs (the audit queue input). */
        val claims: List<VerificationClaim>,
        /** kcIds whose `kc_verification_status` was set to `pending` this run (the UNVERIFIED→PENDING
         *  promotions). Already-audited KCs (faithful/uncertain/failed) are NOT in this set. */
        val pendingSet: Set<String>,
    )

    /**
     * Emit one KC's [VerificationClaim]s — PURE, reorder-stable, no DB.
     *
     * Emission rules (each claim's `content` is what `sha256_8` hashes for the id):
     *  - **DEFINITION** — one per `source` ref, `content = ref.quote`, `source = that ref` (so the
     *    span-anchored round-trip leg can re-locate it).
     *  - **INVARIANT** — one when `invariant != null`, `content = invariant`,
     *    `source = the KC's first span-bearing ref` (the gold span the round-trip leg anchors on).
     *  - **GRADER_RULE** — one per `grader_rules` entry, `content = the rule`,
     *    `source = the KC's first span-bearing ref`.
     *
     * Reorder-stability: the RETURNED claim set (by claimId) is independent of `grader_rules` order.
     * `source` reorder is stable for DEFINITION claims (each ref is its own claim, so no ref is
     * "first"). For anchorRef-bound kinds (INVARIANT / GRADER_RULE / EXPLANATION / WORKED_EXAMPLE),
     * the emitted claim's `source` is `kc.source.firstOrNull { it.span != null }` (anchorRef); a
     * source-list reorder that promotes a different span-bearing ref to first changes that claim's
     * `doc|page|spanStart|spanEnd` — and therefore changes `kcContentHash`. Source reorder is stable
     * ONLY for DEFINITION-only KCs (no anchorRef-bound claims). See [kcContentHashOf].
     * The list is sorted by `claimId` for a deterministic order.
     */
    fun claimsFor(kc: KnowledgeConcept): List<VerificationClaim> {
        val out = ArrayList<VerificationClaim>()
        // The gold span the equational legs anchor on: the KC's first span-bearing source ref.
        val anchorRef = kc.source.firstOrNull { it.span != null }
        // B5r-3 (D-R7) — the plain-English restatement of the invariant, when authored. It becomes the
        // LLM/NLI hypothesis (`content`) for every EQUATIONAL claim (the INVARIANT + any directive-derived
        // GRADER_RULE) so the family judges the NL meaning against the lecture quote instead of a bare
        // equation. Blank ⇒ no restatement (fall back to the equation as content). The SymPy input
        // (`invariant`) STAYS the raw equation regardless.
        val nlHypothesis: String? = kc.invariant_statement?.takeIf { it.isNotBlank() }

        // DEFINITION claims — one per source ref, anchored on that ref's own span.
        for (ref in kc.source) {
            out += claim(kc, ClaimKind.DEFINITION, content = ref.quote, invariant = null, source = ref)
        }
        // INVARIANT claim — the KC's precise machine-checkable equation.
        //  - `invariant` (SymPy input)  = the raw equation, always.
        //  - `content`   (NLI hypothesis) = invariant_statement when present, else the equation (D-R7).
        kc.invariant?.let { inv ->
            out += claim(kc, ClaimKind.INVARIANT, content = nlHypothesis ?: inv, invariant = inv, source = anchorRef)
        }
        // GRADER_RULE claims — one per authored rule, routed by what the rule IS (D-R8):
        //
        //  (a) A SymPy DIRECTIVE — either prefixed `sympy:` (case-insensitive, after trim) OR already a
        //      plain `lhs = rhs` equation. A directive is a MACHINE instruction, NOT prose, so it must
        //      NEVER reach the LLM/NLI family as a bare hypothesis (NLI returns garbage on `sympy: …` and
        //      on a bare `1+1+1=3`). Instead we EXTRACT its equation and route it to the SymPy leg as an
        //      EQUATIONAL GRADER_RULE (`invariant = the equation`); its `content` (the NLI hypothesis,
        //      should the family still vote) = the KC's invariant_statement when present, else the
        //      extracted equation. If we CANNOT confidently extract an equation we emit NO claim for that
        //      rule (better a missing claim than garbage fed to NLI).
        //
        //  (b) A PROSE rule ("the answer must distinguish the three size measures") is a genuine NL
        //      instruction ⇒ stays an LLM-judged GRADER_RULE exactly as today: `invariant = null`,
        //      `content = the rule text`. It floors to the §2.5 UNCERTAIN floor / prose round-trip path,
        //      never a hollow tautological SymPy pass.
        for (rule in kc.grader_rules) {
            val equation = directiveEquation(rule)
            if (equation != null) {
                // (a) directive ⇒ equational GRADER_RULE; the literal directive NEVER becomes the content.
                out += claim(
                    kc, ClaimKind.GRADER_RULE,
                    content = nlHypothesis ?: equation,
                    invariant = equation,
                    source = anchorRef,
                )
            } else if (isSymPyDirective(rule)) {
                // (a') a `sympy:`-prefixed rule whose equation we could NOT extract ⇒ emit NO claim
                // (never feed the bare `sympy: …` string to NLI). Intentionally skipped.
                continue
            } else {
                // (b) prose rule ⇒ LLM/round-trip-judged GRADER_RULE, content = the rule text verbatim.
                out += claim(kc, ClaimKind.GRADER_RULE, content = rule, invariant = null, source = anchorRef)
            }
        }
        // ── Grounded-teaching layer (council 1780928193) — authored prose claims ──────────────────
        // EXPLANATION — authored plain-words restatement. PROSE (invariant = null ⇒ !isEquationalKind),
        // anchored on `anchorRef` (the KC's first span-bearing ref) so the round-trip leg can relocate
        // the gold span; routes like a prose GRADER_RULE (decideOutcome case 3pr/4p). Emitted only when
        // non-blank, so a KC without it keeps its content_hash unchanged (no re-audit cascade).
        kc.explanation_ro?.takeIf { it.isNotBlank() }?.let { expl ->
            out += claim(kc, ClaimKind.EXPLANATION, content = expl, invariant = null, source = anchorRef)
        }
        // WORKED_EXAMPLE — authored worked solution. Same routing + anchoring as EXPLANATION.
        kc.worked_example_ro?.takeIf { it.isNotBlank() }?.let { ex ->
            out += claim(kc, ClaimKind.WORKED_EXAMPLE, content = ex, invariant = null, source = anchorRef)
        }
        // Deterministic, reorder-stable order by the content-addressed id.
        return out.sortedBy { it.claimId }
    }

    /** B5r-3 (D-R8) — is [text] a SymPy DIRECTIVE (a machine instruction, not prose)? True when it is
     *  prefixed `sympy:` (case-insensitive, after trim) OR is already a plain `lhs = rhs` equation. */
    private fun isSymPyDirective(text: String): Boolean =
        text.trim().startsWith("sympy:", ignoreCase = true) || isPlainEquation(text)

    /**
     * B5r-3 (D-R8) — extract the plain `lhs = rhs` equation a SymPy directive checks, or null when none
     * can be confidently parsed (⇒ the caller emits NO claim rather than feed garbage to NLI).
     *
     * Handles:
     *  - a bare `lhs = rhs` rule (already an equation)                              ⇒ returns it (trimmed).
     *  - `sympy: <body>  # optional comment` where <body> is:
     *      · `simplify(LHS - RHS) == 0` / `simplify((A) - (B)) == 0`                ⇒ returns `A = B`.
     *      · a plain `lhs = rhs` equation                                          ⇒ returns it.
     *  - anything else (e.g. `sympy: assert is_valid(tree)`)                        ⇒ returns null.
     */
    internal fun directiveEquation(rule: String): String? {
        val trimmed = rule.trim()
        // A bare plain equation rule IS the equation.
        if (!trimmed.startsWith("sympy:", ignoreCase = true)) {
            return if (isPlainEquation(trimmed)) trimmed else null
        }
        // `sympy:` directive — strip the prefix and any trailing `# comment`, then parse the body.
        var body = trimmed.substring("sympy:".length)
        val hash = body.indexOf('#')
        if (hash >= 0) body = body.substring(0, hash)
        body = body.trim()
        if (body.isEmpty()) return null

        // Form 1: simplify(<inner>) == 0  ⇒ split <inner> into A - B on the TOP-LEVEL minus.
        extractSimplifyEqualsZero(body)?.let { return it }

        // Form 2: the body is itself a plain `lhs = rhs` equation.
        return if (isPlainEquation(body)) body else null
    }

    /**
     * Parse `simplify(<inner>) == 0` (optional whitespace) and turn `<inner>` = `A - B` into `A = B`,
     * stripping ONE balancing pair of outer parens from each side (so `(1 + 1 + 1) - 3` ⇒ `1 + 1 + 1 = 3`
     * and `(t + t + t) - 3*t` ⇒ `t + t + t = 3*t`). Returns null when the shape does not match or the
     * top-level `-` cannot be located (e.g. `simplify(f(x))` with no subtraction).
     */
    private fun extractSimplifyEqualsZero(body: String): String? {
        val lower = body.lowercase()
        if (!lower.startsWith("simplify(")) return null
        // Require a trailing `== 0` (whitespace-tolerant).
        val noWs = body.replace(" ", "")
        if (!noWs.endsWith(")==0")) return null
        // Inner = between the first '(' after `simplify` and its MATCHING ')': scan with depth, stop at
        // the close paren that returns depth to 0 — the rest must be `==0`.
        val open = body.indexOf('(')
        if (open < 0) return null
        var depth = 0
        var close = -1
        for (i in open until body.length) {
            when (body[i]) {
                '(' -> depth++
                ')' -> { depth--; if (depth == 0) { close = i; break } }
            }
        }
        if (close < 0) return null
        val inner = body.substring(open + 1, close).trim()
        // Split inner on the TOP-LEVEL (depth-0) '-' (the subtraction separating A and B).
        val (a, b) = splitTopLevelMinus(inner) ?: return null
        val lhs = stripOuterParens(a.trim())
        val rhs = stripOuterParens(b.trim())
        if (lhs.isEmpty() || rhs.isEmpty()) return null
        return "$lhs = $rhs"
    }

    /** Split [expr] on the LAST top-level (paren-depth-0) `-` into (A, B); null when there is none. */
    private fun splitTopLevelMinus(expr: String): Pair<String, String>? {
        var depth = 0
        var cut = -1
        for (i in expr.indices) {
            when (expr[i]) {
                '(' -> depth++
                ')' -> depth--
                '-' -> if (depth == 0 && i > 0) cut = i // i>0: not a leading unary minus
            }
        }
        if (cut < 0) return null
        return expr.substring(0, cut) to expr.substring(cut + 1)
    }

    /** Strip ONE balanced pair of outer parens wrapping the WHOLE expression (`(1 + 1)` ⇒ `1 + 1`). */
    private fun stripOuterParens(s: String): String {
        val t = s.trim()
        if (t.length < 2 || t.first() != '(' || t.last() != ')') return t
        var depth = 0
        for (i in t.indices) {
            when (t[i]) {
                '(' -> depth++
                ')' -> { depth--; if (depth == 0 && i != t.lastIndex) return t } // closes before the end ⇒ not a wrapping pair
            }
        }
        return t.substring(1, t.length - 1).trim()
    }

    private fun claim(
        kc: KnowledgeConcept,
        kind: ClaimKind,
        content: String,
        invariant: String?,
        source: SourceRef?,
    ) = VerificationClaim(
        claimId = claimId(kc.id, kind, content),
        kcId = kc.id,
        subject = kc.subject,
        kind = kind,
        content = content,
        invariant = invariant,
        source = source,
    )

    /**
     * Is [text] a plain `lhs = rhs` equation the SYMPY leg can machine-check? MED-b: delegates to the
     * ONE shared [EquationSyntax.isPlainEquation] (exactly one top-level `=`, no relational operator
     * `<=`/`>=`/`!=`/`==`, both sides non-empty) so it can never drift from `SymPyLeg.splitEquation`
     * or the validator's tautology split (guarded by `EquationSyntaxAgreementTest`). A prose grader
     * rule returns false ⇒ the reconcile emits `invariant = null` so it floors to UNCERTAIN rather
     * than a hollow tautological SymPy pass (FIX-C).
     */
    private fun isPlainEquation(text: String): Boolean = EquationSyntax.isPlainEquation(text)

    /** The frozen content-hash id `"{kcId}:{KIND}:{sha256_8(content)}"` (M-CLAIM). */
    fun claimId(kcId: String, kind: ClaimKind, content: String): String =
        "$kcId:${kind.name}:${sha256_8(content)}"

    /**
     * D8 — the KC-level content fingerprint: a deterministic `sha256_8` over the KC's AUDITED claim
     * texts + cited spans. Written onto `kc_verification_status.content_hash` at audit time
     * ([jarvis.tutor.verify.VerificationRunner.audit]) and recomputed at serve time
     * ([jarvis.web.VerifyAdmin]) to detect "the lecture was edited after the audit" — a mismatch falls
     * the served badge CLOSED to `unverified` (it never lies "matches your lecture" over text it never
     * checked).
     *
     * Derived from [claimsFor] (the SAME claim emission the audit consumes), so the audit-side hash and
     * the serve-side hash are computed identically and agree byte-for-byte. Reorder-stable: [claimsFor]
     * already sorts by the content-addressed `claimId`, so shuffling `grader_rules`/`source` does not
     * change the hash. Changing ANY audited claim's text, or moving a cited span, DOES change it.
     *
     * B5r-3 (D-R7): `invariant_statement` is folded in transitively — it becomes the `content` of the
     * equational claims ([claimsFor] sets the INVARIANT/directive-GRADER_RULE claim `content =
     * invariant_statement`), so editing it changes that claim's text ⇒ changes the hash ⇒ re-triggers
     * staleness. Folding it via the emitted claims (not as a separate term) keeps the FROZEN identity
     * `kcContentHash(kc) == kcContentHashOf(claimsFor(kc))` (audit-side == serve-side) intact.
     */
    fun kcContentHash(kc: KnowledgeConcept): String = kcContentHashOf(claimsFor(kc))

    /**
     * D8 — the KC content fingerprint over an explicit claim set (the audit-side entry point: the
     * runner already holds the audited claims for a KC and hashes THEM, so the fingerprint reflects
     * exactly what was audited). Canonicalizes each claim as
     * `kind|content|doc|page|spanStart|spanEnd` over a `claimId`-sorted set, then `sha256_8`.
     *
     * Span + doc + page are folded in (not just `content`) so a span move with identical quote text
     * still re-fingerprints — the cited anchor is part of what "this KC's content" means (D8).
     */
    fun kcContentHashOf(claims: List<VerificationClaim>): String {
        val canonical = claims
            .sortedBy { it.claimId }
            .joinToString(" ") { c ->
                val s = c.source
                listOf(
                    c.kind.name,
                    c.content,
                    // D2 (v2): the RAW SymPy equation, a separate read-dependency from `content`.
                    // (When invariant_statement is authored, `content` is the NL restatement and the
                    // raw equation lives ONLY here.) Hashed AS AUTHORED (no fold) so audit==serve.
                    c.invariant ?: "",
                    s?.doc ?: "",
                    (s?.page ?: 0).toString(),
                    (s?.span?.start ?: -1).toString(),
                    (s?.span?.end ?: -1).toString(),
                ).joinToString("")
            }
        // Step-0 (v2): prepend the schema-version prefix so every legacy (v1) hash fails CLOSED.
        return sha256_8(HASH_SCHEMA_VERSION_PREFIX + canonical)
    }

    /**
     * D8/D2 v2: the content-hash SCHEMA-VERSION prefix. Prepended to the canonical string of
     * [kcContentHashOf] so the v2 keyspace is disjoint from every legacy (v1) hash: a row stamped
     * under the OLD formula deterministically MISMATCHES at serve and fails CLOSED. Bump this string
     * whenever the canonicalization (the set of folded fields) changes, so old rows never silently
     * pass a staleness gate they were never re-audited against. Pre-flip the migration ASSERTS there
     * are 0 live content_hash rows ([jarvis.tutor.TutorMigration]) so the re-key is free.
     */
    const val HASH_SCHEMA_VERSION_PREFIX: String = "v2:"

    /**
     * D2 build-time double-hash guard (council D-RF1 point 4). The D2 fold adds the RAW `invariant`
     * field to the fingerprint. That is NOT pure double-counting of `invariant_statement` (which is
     * folded transitively via `content`) ONLY while the raw equation carries signal `content` does
     * not already hold. This pure check proves it for a [claim] set: it returns the claims whose raw
     * `invariant` is byte-identical to their `content` (so the new term adds NOTHING for them and is
     * redundant double-hashing). The healthy case is an empty list — `content` is the NL restatement
     * (or the equation only when no restatement was authored, in which case the redundancy is
     * harmless and expected). A caller (a build-time/test assertion) can use this to flag accidental
     * future authoring where `content == invariant` would make the D2 term dead.
     *
     * It is PC-side/build-time only (no DB, no serve ripple) — used by the ContentReconcile test
     * suite as a structural assertion over the real corpus's emitted claims.
     */
    fun claimsWhereInvariantDoublesContent(claims: List<VerificationClaim>): List<VerificationClaim> =
        claims.filter { it.invariant != null && it.invariant == it.content }

    /**
     * D1 — the round-trip's OWN whitespace fold, lifted to the reconcile so the source-span hash is
     * computed over EXACTLY the bytes the round-trip leg compares (`SpanClaimRoundTrip.fold` /
     * `LiveSourceLocator.foldQuote`: collapse every whitespace run to one space, trim). Hashing the
     * FOLDED slice (never raw bytes) is the single most important D1 constraint: it tolerates the
     * `\s+`/CRLF/`\n`-indent + re-OCR churn that pa-kc-002/004 were hand-corrected for, so a no-op
     * re-run never flips the hash and never false-negatives a faithful KC.
     */
    fun foldSlice(s: String): String = s.replace(Regex("\\s+"), " ").trim()

    /**
     * D1 — the KC-level SOURCE-SPAN fingerprint: `sha256_8` over each span-bearing claim's
     * round-tripped, FOLDED located slice of the LIVE source, aggregated over a `claimId`-sorted set
     * (mirroring [kcContentHashOf]'s order-stability). [rawSourceFor] resolves a claim's live
     * `_sources/{doc}.md` text; the same `LiveSourceLocator` + `SourceOfRecord.slice` + `foldSlice`
     * the audit's round-trip leg uses re-locates the cited quote and folds the relocated slice.
     *
     * PC-SIDE ONLY (D7): this reads `_sources` bytes, which exist ONLY on the authoring PC — it is
     * NEVER called on the VPS serve path. Returns null when NO span-bearing claim relocates (the KC
     * has nothing to source-fingerprint) — the runner stores null ⇒ the serve gate fails CLOSED.
     *
     * @param rawSourceFor (subject, doc) -> the live source text, or null when the file is absent.
     */
    fun sourceSpanHashOf(
        claims: List<VerificationClaim>,
        rawSourceFor: (subject: String, doc: String) -> String?,
    ): String? {
        val parts = claims
            .filter { it.source?.span != null && !it.source?.quote.isNullOrBlank() }
            .sortedBy { it.claimId }
            .mapNotNull { c ->
                val src = c.source ?: return@mapNotNull null
                val raw = rawSourceFor(c.subject, src.doc) ?: return@mapNotNull null
                // Re-locate the quote in the LIVE source (the round-trip's own locate), then fold the
                // relocated RAW slice. A quote that no longer relocates contributes nothing (its
                // absence changes the aggregate ⇒ a real source edit is detected).
                val located = jarvis.tutor.verify.LiveSourceLocator.locate(raw, src.quote).span
                    ?: return@mapNotNull null
                val slice = SourceOfRecord.slice(raw, located) ?: return@mapNotNull null
                "${c.claimId}${foldSlice(slice)}"
            }
        if (parts.isEmpty()) return null
        return sha256_8(HASH_SCHEMA_VERSION_PREFIX + parts.joinToString(""))
    }

    /** First 8 lowercase-hex chars of `SHA-256(content)`. Reorder-stable content hash (M-CLAIM). */
    fun sha256_8(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(8)
        for (i in 0 until 4) sb.append("%02x".format(digest[i]))
        return sb.toString()
    }

    /**
     * Run the reconcile over every KC in [subjects], writing to the B8 `kc_verification_status`
     * table on [db]. Returns the emitted claims + the set of KCs promoted to `pending`.
     *
     * H10 / FAIL-SAFE upsert per KC:
     *  - NO existing row  ⇒ INSERT `status = pending` (UNVERIFIED→PENDING, the §2.4 edge, set DIRECTLY).
     *  - row == `unverified` ⇒ UPDATE to `pending` (an explicit seed is promoted into the queue).
     *  - any other status (`pending`/`faithful`/`uncertain`/`failed`) ⇒ LEFT UNTOUCHED. A faithful KC
     *    is NEVER regressed to pending; an already-pending KC stays pending (idempotent).
     *
     * @param clock injected wall-clock seam for the `updated_at` write (hermetic in tests).
     */
    fun reconcile(
        subjects: List<LoadedSubject>,
        db: Database,
        clock: () -> Instant = Instant::now,
    ): ReconcileReport {
        val allClaims = ArrayList<VerificationClaim>()
        val promoted = LinkedHashSet<String>()

        for (sub in subjects) {
            for (kc in sub.kcs) {
                allClaims += claimsFor(kc)
                if (setPending(db, kc.id, clock())) promoted += kc.id
            }
        }
        return ReconcileReport(claims = allClaims, pendingSet = promoted)
    }

    /**
     * Set a single KC's runtime status to `pending` iff it is currently absent or `unverified`.
     * Returns true iff this call moved the KC into `pending` (a fresh promotion this run).
     * NEVER regresses an already-audited status (H10).
     */
    private fun setPending(db: Database, kcId: String, now: Instant): Boolean = transaction(db) {
        val existing = KcVerificationStatusTable.selectAll()
            .where { KcVerificationStatusTable.kcId eq kcId }
            .singleOrNull()

        if (existing == null) {
            // No row yet: enter the audit queue at pending (UNVERIFIED→PENDING, set DIRECTLY).
            KcVerificationStatusTable.insert {
                it[KcVerificationStatusTable.kcId] = kcId
                it[status] = VerificationStatus.pending.name
                it[updatedAt] = now
            }
            return@transaction true
        }

        val current = existing[KcVerificationStatusTable.status]
        // ONLY an explicit `unverified` seed is promoted. faithful/uncertain/failed/pending stay put
        // (H10: never regress a faithful KC back to pending; never re-open an audited verdict).
        if (current == VerificationStatus.unverified.name) {
            KcVerificationStatusTable.update({ KcVerificationStatusTable.kcId eq kcId }) {
                it[status] = VerificationStatus.pending.name
                it[updatedAt] = now
            }
            return@transaction true
        }
        false
    }

    /** One KC whose source bytes changed since the audit (the D1 watcher fired on it). */
    data class SourceSpanReconcileResult(
        /** kcIds whose live source no longer folds-equal to the stored `source_span_hash` ⇒ NULLed +
         *  re-pended (the narrowed H10 exception: regress faithful->pending ONLY on a REAL source edit). */
        val invalidated: Set<String>,
    )

    /**
     * D1 step-4 — the PC-side SOURCE-EDIT WATCHER (curate-tutor Stage-9 reconcile leg). For every KC
     * that carries a stored `source_span_hash`, recompute the hash from the LIVE `_sources` bytes; if
     * it no longer matches (the source file was edited / re-OCR'd after the audit, even though the
     * YAML quote+span is unchanged so `content_hash` would still match), the row is invalidated:
     * `content_hash` AND `source_span_hash` are NULLed and the KC is re-pended for audit. The serve
     * gate then fails CLOSED (NULL `content_hash`) until a fresh audit re-grounds it.
     *
     * This is the leg the serve side CANNOT run (the VPS has no `_sources` bytes, D7) — a passive
     * serve-time hash column alone can never fire, so detection MUST live PC-side here.
     *
     * NARROWED H10 (never-regress-faithful) EXCEPTION — deliberately scoped, council-approved:
     *  - regress ONLY when the stored hash is present AND the recomputed live hash DIFFERS (a real
     *    source-byte change). A no-op re-run (bytes unchanged ⇒ folds-equal) leaves the row untouched,
     *    so the reconcile is IDEMPOTENT and never flaps pa-kc-001..004 on whitespace/re-OCR churn
     *    (the hash is over the round-trip's FOLDED slice, which absorbs that churn).
     *  - a row with a NULL stored `source_span_hash` is SKIPPED (nothing to compare; a legacy/partial
     *    row already fails closed at serve on its NULL content_hash).
     *
     * PURE-ish: no LLM, no network — only DB + the injected [rawSourceFor] (the live `_sources` read).
     *
     * @param rawSourceFor (subject, doc) -> live source text (or null when absent — an absent source
     *                     makes the recomputed hash differ ⇒ invalidate, the correct fail-closed move).
     */
    fun reconcileSourceSpans(
        subjects: List<LoadedSubject>,
        db: Database,
        rawSourceFor: (subject: String, doc: String) -> String?,
        clock: () -> Instant = Instant::now,
    ): SourceSpanReconcileResult {
        val invalidated = LinkedHashSet<String>()
        for (sub in subjects) {
            for (kc in sub.kcs) {
                val storedHash = transaction(db) {
                    KcVerificationStatusTable.selectAll()
                        .where { KcVerificationStatusTable.kcId eq kc.id }
                        .singleOrNull()
                        ?.get(KcVerificationStatusTable.sourceSpanHash)
                } ?: continue   // no stored source-span hash to compare against ⇒ skip (already fail-closed)

                val liveHash = sourceSpanHashOf(claimsFor(kc), rawSourceFor)
                if (liveHash == storedHash) continue   // bytes unchanged ⇒ idempotent no-op (H10 safe)

                // A REAL source-byte change: fail CLOSED. NULL both hashes + re-pend for re-audit.
                val now = clock()
                transaction(db) {
                    KcVerificationStatusTable.update({ KcVerificationStatusTable.kcId eq kc.id }) {
                        it[status] = VerificationStatus.pending.name
                        it[contentHash] = null
                        it[sourceSpanHash] = null
                        it[lectureGrounded] = false
                        it[lastAuditRunId] = null
                        it[updatedAt] = now
                    }
                }
                invalidated += kc.id
            }
        }
        return SourceSpanReconcileResult(invalidated)
    }
}
