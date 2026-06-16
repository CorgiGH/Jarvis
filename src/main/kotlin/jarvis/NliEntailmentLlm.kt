package jarvis

import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * D6 / D-R12 (B5r-6) — the PC-side LOCAL NLI entailment family (`LegFamily.NLI`).
 *
 * Replaces the audit's family-B (a 2nd OpenRouter `:free` LLM — false independence + 429-throttled)
 * with a TRULY-independent, network-free entailment model so the OFFLINE audit can run two genuinely
 * independent families. It is an [Llm] adapter so it drops into the frozen
 * `TwoFamilyDeriver.Leg(family, llm)` seam UNCHANGED — the deriver's `buildPrompt`/`classify` are
 * untouched.
 *
 * PC-side / OFFLINE ONLY (D7): the VPS request path (`TrustRoutes` legB) MUST NOT load a model — it
 * stays `OpenRouterChatLlm`. This adapter is wired only at `VerifyContentCli` legB.
 *
 * It does NOT chat. Its [complete] re-interprets the deriver's chat prompt as a PREMISE (the SOURCE
 * QUOTE) ⊨ HYPOTHESIS (the CLAIM content) NLI problem, runs an embedded python NLI runner via
 * ProcessBuilder (MIRRORING `jarvis.tutor.verify.realSymPyEquals` + `PY_EQUALS_RUNNER`), and returns
 * the one-word verdict the deriver's `classify` reads from the leading token.
 *
 * FAIL-LOUD: if python cannot start, the runner emits `ERR`, the model is not installed, or the
 * output is unparseable ⇒ THROW. A thrown leg is what the deriver/runner treats as `anyThrew`
 * (fail-loud). UNCLEAR is reserved for a GENUINE low-confidence model verdict — NEVER for an infra
 * failure (never a silent SUPPORTED, never a swallowed-to-UNCLEAR-as-success).
 */
class NliEntailmentLlm : Llm {

    override suspend fun complete(
        messages: List<ChatMessage>,
        maxTokens: Int,
        responseFormat: String?,
        imagePath: String?,
    ): Pair<String, String> {
        val parsed = parseNliInput(messages)
        // No SOURCE QUOTE ⇒ nothing to entail against ⇒ a genuine low-confidence (UNCLEAR) verdict.
        // This is NOT an infra failure: the deriver simply built no premise (e.g. a no-gold-span
        // claim), so abstaining is the honest answer — return UNCLEAR, do not throw.
        if (parsed.premise == null) return "UNCLEAR" to NLI_MODEL_ID
        val verdict = runNli(parsed.premise, parsed.hypothesis)
        return verdict to NLI_MODEL_ID
    }

    override fun close() { /* no-op: ProcessBuilder owns no long-lived resource */ }

    companion object {
        /** The HF model id reported back as the leg's "model" string (D-R1: keep DeBERTa-v3 3-class). */
        const val NLI_MODEL_ID = "MoritzLaurer/DeBERTa-v3-base-mnli-fever-anli"

        /** Markers the deriver's `buildPrompt` injects, in this order: CLAIM ... [STATED INVARIANT ...] [SOURCE QUOTE ...]. */
        private const val CLAIM_MARKER = "CLAIM ("
        private const val INVARIANT_MARKER = "\nSTATED INVARIANT:\n"
        private const val QUOTE_MARKER = "\nSOURCE QUOTE:\n"

        /** Model load is slow (cold cache may download ~0.5GB); allow a generous wall-clock. */
        private const val NLI_TIMEOUT_SECONDS = 120L
        private const val NLI_MAX_OUTPUT_BYTES = 4096

        /**
         * Pure, unit-testable parse of the deriver's prompt into the NLI (premise, hypothesis) pair.
         *
         * The deriver's user message is:
         *   `CLAIM (<kind>):\n<content>[<STATED INVARIANT:\n...>][<SOURCE QUOTE:\n...>]`
         *
         *  - PREMISE  = the text after the LAST `\nSOURCE QUOTE:\n` marker (null when there is no
         *               SOURCE QUOTE ⇒ caller abstains to UNCLEAR — nothing to entail against).
         *  - HYPOTHESIS = the CLAIM content = the text after `CLAIM (<kind>):\n` up to the FIRST
         *               `\nSTATED INVARIANT:` or `\nSOURCE QUOTE:` marker (whichever appears first).
         *
         * We read from the LAST user message so a stray system-prompt mention of a marker word can't
         * be mistaken for the payload.
         */
        internal fun parseNliInput(messages: List<ChatMessage>): NliInput {
            val user = messages.lastOrNull { it.role == "user" }?.content
                ?: messages.lastOrNull()?.content
                ?: ""

            val premise = run {
                val q = user.lastIndexOf(QUOTE_MARKER)
                if (q < 0) null else user.substring(q + QUOTE_MARKER.length)
            }

            val claimStart = user.indexOf(CLAIM_MARKER)
            val hypothesis = if (claimStart < 0) {
                // No CLAIM header at all — treat the whole message as the hypothesis (defensive).
                user
            } else {
                val afterHeader = user.indexOf('\n', claimStart)
                if (afterHeader < 0) {
                    ""
                } else {
                    val contentStart = afterHeader + 1
                    // First of either marker, scanning from the content start.
                    val inv = user.indexOf(INVARIANT_MARKER, contentStart)
                    val quo = user.indexOf(QUOTE_MARKER, contentStart)
                    val end = listOf(inv, quo).filter { it >= 0 }.minOrNull() ?: user.length
                    user.substring(contentStart, end)
                }
            }
            return NliInput(premise = premise, hypothesis = hypothesis)
        }

        /**
         * Pure, unit-testable mapping of the runner's single tab-delimited stdout line to the verdict
         * WORD the deriver's `classify` reads.
         *
         *  - `SUPPORTED\t<prob>` / `REFUTED\t<prob>` / `UNCLEAR\t<prob>` ⇒ return the word (UNCLEAR is a
         *    GENUINE low-confidence model verdict — returned, NOT thrown).
         *  - `ERR\t<msg>` ⇒ THROW (infra/model failure — fail-loud, becomes the leg's `anyThrew`).
         *  - anything else (empty / unrecognised first token) ⇒ THROW (unparseable — fail-loud).
         */
        internal fun mapRunnerOutput(raw: String): String {
            val line = raw.trim()
            if (line.isEmpty()) {
                throw RuntimeException("NLI runner returned empty output (fail-loud)")
            }
            val parts = line.split('\t', limit = 2)
            return when (parts.firstOrNull()) {
                "SUPPORTED" -> "SUPPORTED"
                "REFUTED" -> "REFUTED"
                "UNCLEAR" -> "UNCLEAR"
                "ERR" -> throw RuntimeException(
                    "NLI runner failed (fail-loud): ${parts.getOrNull(1).orEmpty().ifBlank { "unspecified error" }}",
                )
                else -> throw RuntimeException("unparseable NLI runner output (fail-loud): ${line.take(200)}")
            }
        }

        /**
         * Run the embedded python NLI runner over (premise, hypothesis). MIRRORS `realSymPyEquals`:
         * resolve python from `JARVIS_PYTHON3` (else `python3`), `ProcessBuilder(python, "-c", runner)`,
         * write premise+hypothesis lines to stdin, bounded waitFor, read one stdout line, map it.
         *
         * FAIL-LOUD throughout: a process that cannot start, a timeout, an I/O error, an ERR line, or
         * unparseable output all THROW — never a silent pass and never an infra-failure masquerading as
         * a model UNCLEAR.
         */
        internal fun runNli(premise: String, hypothesis: String): String {
            val python = System.getenv("JARVIS_PYTHON3")?.takeIf { it.isNotBlank() } ?: "python3"
            // D-R13: pass the runner as a temp FILE, NOT `python -c "<script>"`. On Windows the JVM's
            // CreateProcess command-line quoting MANGLES the embedded double-quotes in a `-c` arg
            // (python then hits a SyntaxError → empty stdout → this leg throws "empty output"). The
            // file path is quote-safe + is the proven bridge. stderr is DISCARDed so transformers'
            // progress-bar/warnings can never fill an undrained pipe.
            val scriptFile = java.nio.file.Files.createTempFile("jarvis-nli-", ".py")
            java.nio.file.Files.write(scriptFile, PY_NLI_RUNNER.toByteArray(StandardCharsets.UTF_8))
            val proc = try {
                ProcessBuilder(python, scriptFile.toString())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            } catch (e: Exception) {
                runCatching { java.nio.file.Files.deleteIfExists(scriptFile) }
                throw RuntimeException(
                    "NLI bridge unavailable: cannot start '$python' (${e.javaClass.simpleName}: ${e.message})",
                    e,
                )
            }
            return try {
                OutputStreamWriter(proc.outputStream, StandardCharsets.UTF_8).use { w ->
                    w.write(premise.replace("\n", " ")); w.write("\n")
                    w.write(hypothesis.replace("\n", " ")); w.write("\n")
                    w.flush()
                }
                val finished = proc.waitFor(NLI_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (!finished) {
                    proc.destroyForcibly()
                    throw RuntimeException("NLI runner timed out after ${NLI_TIMEOUT_SECONDS}s (fail-loud)")
                }
                val out = proc.inputStream.readNBytes(NLI_MAX_OUTPUT_BYTES)
                    .toString(StandardCharsets.UTF_8)
                mapRunnerOutput(out)
            } catch (e: RuntimeException) {
                proc.destroyForcibly()
                throw e
            } catch (e: Exception) {
                proc.destroyForcibly()
                throw RuntimeException("NLI bridge error (fail-loud): ${e.javaClass.simpleName}: ${e.message?.take(160)}", e)
            } finally {
                runCatching { java.nio.file.Files.deleteIfExists(scriptFile) }
            }
        }

        /**
         * The embedded python NLI runner. Reads premise line + hypothesis line from stdin, loads the
         * DeBERTa-v3 3-class NLI model, softmaxes the logits, and prints exactly one tab line:
         * `SUPPORTED|REFUTED|UNCLEAR\t<prob>`. On ANY import/exception prints `ERR\t<msg>`.
         *
         * 3-band verdict (the proven `tools/nli_spike.py` logic):
         *   argmax==entailment    & top_prob >= 0.60 -> SUPPORTED
         *   argmax==contradiction & top_prob >= 0.60 -> REFUTED
         *   neutral OR top_prob < 0.60 (the LOAD-BEARING abstain band) -> UNCLEAR
         */
        internal val PY_NLI_RUNNER = """
import sys

MODEL = "MoritzLaurer/DeBERTa-v3-base-mnli-fever-anli"
ABSTAIN_FLOOR = 0.60  # top class must clear this or we abstain to UNCLEAR (load-bearing)

try:
    import torch
    from transformers import AutoTokenizer, AutoModelForSequenceClassification
except Exception as e:
    sys.stdout.write("ERR\t" + ("import failed: " + str(e)).replace("\t", " ").replace("\n", " ") + "\n")
    sys.exit(0)

try:
    premise = sys.stdin.readline().rstrip("\n")
    hypothesis = sys.stdin.readline().rstrip("\n")
    tok = AutoTokenizer.from_pretrained(MODEL)
    model = AutoModelForSequenceClassification.from_pretrained(MODEL)
    model.eval()
    id2label = model.config.id2label
    inputs = tok(premise, hypothesis, return_tensors="pt", truncation=True)
    with torch.no_grad():
        probs = torch.softmax(model(**inputs).logits[0], dim=-1)
    top_id = int(torch.argmax(probs))
    top_label = id2label[top_id].lower()
    top_prob = float(probs[top_id])
    if top_prob < ABSTAIN_FLOOR:
        verdict = "UNCLEAR"
    elif "entail" in top_label:
        verdict = "SUPPORTED"
    elif "contradict" in top_label:
        verdict = "REFUTED"
    else:
        verdict = "UNCLEAR"  # neutral
    sys.stdout.write(verdict + "\t" + ("%.4f" % top_prob) + "\n")
except Exception as e:
    sys.stdout.write("ERR\t" + str(e).replace("\t", " ").replace("\n", " ") + "\n")
""".trimIndent()
    }

    /** Parsed NLI problem extracted from the deriver's prompt. premise==null ⇒ no SOURCE QUOTE. */
    internal data class NliInput(val premise: String?, val hypothesis: String)
}
