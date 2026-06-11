package jarvis.content

import com.charleskorn.kaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Plan-2 Task 2 — KcBeats.isCompleteFor (INV-3.2 structural minimum, spec §3.2/§4.3) and the
 * additive language-keyed KnowledgeConcept.beats field. Beat ④ (name) is OPTIONAL at the schema
 * level (STANDARD plan omits it, §0.8 #5 / §4.5). Numerical concept types (PROCEDURE,
 * FORMULA_APPLICATION, PROBABILISTIC) require the skeleton+trace variant; others require the
 * choice variant.
 */
class KcBeatsTest {

    // A complete CHOICE-variant beats map (for a non-numerical type, e.g. DEFINITION_TAXONOMY).
    private fun completeChoiceBeats() = KcBeats(
        predict = BeatPredict(
            prompt = "Care dintre afirmații descrie corect un algoritm?",
            options = listOf(
                PredictOption("O secvență de operații neambigue care se termină", "Corect — definiția din curs.", correct = true),
                PredictOption("Orice listă de pași, chiar dacă nu se termină", "Greșit — algoritmul trebuie să se oprească în timp finit."),
                PredictOption("Doar o formulă matematică", "Greșit — un algoritm nu este o singură formulă."),
            ),
        ),
        attempt = BeatAttempt(
            statement = "Alege definiția care respectă toate condițiile din curs.",
            choices = listOf(
                AttemptChoice("Operații neambigue, efectiv calculabile, care se termină", true, "Da — toate cele trei condiții sunt prezente."),
                AttemptChoice("O listă de pași care poate continua la nesfârșit", false, "Nu — lipsește terminarea în timp finit."),
            ),
            feedback_correct = "Exact — definiția cere terminare, neambiguitate și calculabilitate.",
        ),
        reveal = BeatReveal(
            steps = listOf(
                RevealStep("Fiecare pas este neambiguu.", "Neambiguu = un singur sens de interpretare."),
                RevealStep("Execuția se oprește în timp finit.", "Finit = după un număr mărginit de pași."),
            ),
        ),
        name = BeatName(
            definition = "Un algoritm este o colecție bine ordonată de operații neambigue și efectiv calculabile.",
            invariant_statement = "Execuția produce un rezultat și se oprește în timp finit.",
            why_matters = "Fără terminare nu putem garanta un rezultat — de aici nevoia condiției.",
        ),
        check = BeatCheck(
            item_stem = "Este o rețetă de bucătărie un algoritm? Justifică folosind cele trei condiții.",
            choices = listOf(
                AttemptChoice("Da, dacă pașii sunt neambigui și se termină", true, "Corect."),
                AttemptChoice("Nu, niciodată", false, "Greșit — depinde de condiții."),
            ),
        ),
    )

    // A complete NUMERICAL-variant beats map (skeleton + trace), for FORMULA_APPLICATION.
    private fun completeNumericalBeats() = KcBeats(
        predict = BeatPredict(
            prompt = "Care este dimensiunea uniformă a unui vector cu 3 elemente, fiecare de mărime 1?",
            options = listOf(
                PredictOption("3", "Corect — 1 + 1 + 1 = 3.", correct = true),
                PredictOption("1", "Greșit — mărimea vectorului este suma mărimilor elementelor."),
                PredictOption("Depinde de valori", "Greșit — la măsura uniformă, fiecare element are mărime 1."),
            ),
        ),
        attempt = BeatAttempt(
            statement = "Completează tabelul de mărimi pentru vectorul [a0, a1, a2].",
            skeleton_rows = listOf(
                SkeletonRow("|a0|_unif", formula = "1"),
                SkeletonRow("|a1|_unif", formula = "1"),
                SkeletonRow("|a2|_unif", formula = "1"),
                SkeletonRow("|a|_unif = sumă", formula = "1 + 1 + 1", is_decision_row = true),
            ),
            trace_steps = listOf(
                TraceStep(row_index = 0, value = "1"),
                TraceStep(row_index = 1, value = "1"),
                TraceStep(row_index = 2, value = "1"),
                TraceStep(row_index = 3, value = "3", callout = "Suma mărimilor = 3."),
            ),
            input_schema = """{"type":"number","unit":"unități de mărime"}""",
            feedback_correct = "Exact — la măsura uniformă mărimea totală este 1 + 1 + 1 = 3.",
        ),
        reveal = BeatReveal(
            steps = listOf(
                RevealStep("Fiecare element are mărime uniformă 1.", "Regula |n|_unif = 1 din curs."),
                RevealStep("Mărimea vectorului este suma mărimilor.", "|a|_d = Σ |a_i|_d."),
            ),
        ),
        check = BeatCheck(
            item_stem = "Care este mărimea uniformă a unui vector cu 5 elemente, fiecare de mărime 1?",
            numeric_answer = "5",
            numeric_tolerance = 0.0,
        ),
    )

    @Test fun `complete numerical beats pass for FORMULA_APPLICATION`() {
        assertTrue(completeNumericalBeats().isCompleteFor(ConceptType.FORMULA_APPLICATION))
    }

    @Test fun `numerical type with empty skeleton_rows fails`() {
        val b = completeNumericalBeats().let {
            it.copy(attempt = it.attempt!!.copy(skeleton_rows = emptyList()))
        }
        assertFalse(b.isCompleteFor(ConceptType.FORMULA_APPLICATION))
    }

    @Test fun `complete choice beats pass for DEFINITION_TAXONOMY`() {
        assertTrue(completeChoiceBeats().isCompleteFor(ConceptType.DEFINITION_TAXONOMY))
    }

    @Test fun `choice-variant DEFINITION_TAXONOMY with empty choices fails`() {
        val b = completeChoiceBeats().let {
            it.copy(attempt = it.attempt!!.copy(choices = emptyList()))
        }
        assertFalse(b.isCompleteFor(ConceptType.DEFINITION_TAXONOMY))
    }

    @Test fun `predict with only 2 options fails`() {
        val b = completeChoiceBeats().let {
            it.copy(predict = it.predict!!.copy(options = it.predict!!.options.take(2)))
        }
        assertFalse(b.isCompleteFor(ConceptType.DEFINITION_TAXONOMY))
    }

    @Test fun `predict with no correct option fails`() {
        val b = completeChoiceBeats().let {
            it.copy(predict = it.predict!!.copy(options = it.predict!!.options.map { o -> o.copy(correct = false) }))
        }
        assertFalse(b.isCompleteFor(ConceptType.DEFINITION_TAXONOMY))
    }

    @Test fun `check without grading data fails`() {
        val b = completeChoiceBeats().let {
            it.copy(check = it.check!!.copy(choices = emptyList(), numeric_answer = null))
        }
        assertFalse(b.isCompleteFor(ConceptType.DEFINITION_TAXONOMY), "no correct choice and no numeric answer = no grading data")
    }

    @Test fun `beat 4 (name) absent is still complete (STANDARD plan legal)`() {
        val b = completeChoiceBeats().copy(name = null)
        assertTrue(b.isCompleteFor(ConceptType.DEFINITION_TAXONOMY), "beat name is optional at the schema level")
    }

    @Test fun `a KC YAML with a full ro beats map round-trips`() {
        val yaml = """
            id: pa-kc-001
            subject: PA
            name_ro: "Noțiunea de algoritm"
            name_en: "The notion of an algorithm"
            cluster: "Fundamentele algoritmilor"
            bloom_level: understand
            difficulty: 1
            time_minutes: 25
            exam_weight: 0.22
            tier: 1
            concept_type: definition-taxonomy
            beats:
              ro:
                predict:
                  prompt: "Care afirmație descrie corect un algoritm?"
                  options:
                    - text: "O secvență de operații neambigue care se termină"
                      callback: "Corect — definiția din curs."
                      correct: true
                    - text: "Orice listă de pași, chiar dacă nu se termină"
                      callback: "Greșit — algoritmul trebuie să se oprească."
                    - text: "Doar o formulă matematică"
                      callback: "Greșit — nu este o singură formulă."
                attempt:
                  statement: "Alege definiția care respectă toate condițiile."
                  choices:
                    - text: "Operații neambigue care se termină"
                      correct: true
                      feedback: "Da — toate condițiile sunt prezente."
                    - text: "Pași care pot continua la nesfârșit"
                      correct: false
                      feedback: "Nu — lipsește terminarea."
                  feedback_correct: "Exact — terminare, neambiguitate, calculabilitate."
                reveal:
                  steps:
                    - text: "Fiecare pas este neambiguu."
                      callout: "Un singur sens de interpretare."
                    - text: "Execuția se oprește în timp finit."
                      callout: "După un număr mărginit de pași."
                name:
                  definition: "Un algoritm este o colecție bine ordonată de operații neambigue."
                  invariant_statement: "Execuția produce un rezultat și se oprește în timp finit."
                  why_matters: "Fără terminare nu putem garanta un rezultat."
                check:
                  item_stem: "Este o rețetă un algoritm? Justifică."
                  choices:
                    - text: "Da, dacă pașii sunt neambigui și se termină"
                      correct: true
                      feedback: "Corect."
                    - text: "Nu, niciodată"
                      correct: false
                      feedback: "Greșit."
        """.trimIndent()
        val kc = Yaml.default.decodeFromString(KnowledgeConcept.serializer(), yaml)
        val ro = kc.beats["ro"]
        assertEquals("Care afirmație descrie corect un algoritm?", ro?.predict?.prompt)
        assertEquals(3, ro?.predict?.options?.size)
        assertTrue(ro!!.predict!!.options.first().correct)
        assertEquals("Corect — definiția din curs.", ro.predict!!.options.first().callback)
        assertEquals(2, ro.attempt?.choices?.size)
        assertEquals("Un algoritm este o colecție bine ordonată de operații neambigue.", ro.name?.definition)
        assertTrue(ro.isCompleteFor(ConceptType.DEFINITION_TAXONOMY), "the authored ro beats are structurally complete")
    }
}
