/** PlacementShell — DARK door-language placement exam (surface 10).
 *
 *  8 questions, one at a time, with progress pips.
 *  After all questions answered, shows PlacementResultBanner.
 *
 *  testids:
 *   placement-shell
 *   placement-question          (delegated to PlacementQuestion)
 *   placement-result-banner     (delegated to PlacementResultBanner)
 *   placement-progress-pip-{n}  (n = 0..7, 8 total)
 */
import { useState } from "react";
import { PlacementQuestion, type PlacementQuestionData } from "./PlacementQuestion";
import { PlacementResultBanner, type PlacementResult } from "./PlacementResultBanner";

interface Props {
  onComplete: () => void;
}

/** Hardcoded 8-question placement bank (Romanian). */
const QUESTIONS: PlacementQuestionData[] = [
  {
    id: "p1",
    promptRo: "Ce calculează formula EWMA (media mobilă ponderată exponențial)?",
    options: [
      "Media ponderată a valorilor recente, descrescând exponențial în timp",
      "Suma tuturor valorilor împărțită la numărul lor",
      "Maximul dintr-o fereastră de observații",
      "Medidul median al ultimelor 5 valori",
    ],
  },
  {
    id: "p2",
    promptRo: "Ce înseamnă 'spaced repetition' (repetiție spațiată)?",
    options: [
      "Revizuirea materialului la intervale crescânde pentru a consolida memoria",
      "Citirea unui text de mai multe ori consecutiv",
      "Rezolvarea mai multor exerciții din același capitol odată",
      "Memorarea prin repetare rapidă în aceeași zi",
    ],
  },
  {
    id: "p3",
    promptRo: "Care este scopul unui graf de prereqizite într-un curriculum?",
    options: [
      "A defini ordinea în care conceptele trebuie studiate",
      "A afișa notele studentului",
      "A genera exerciții automat",
      "A compara performanța mai multor studenți",
    ],
  },
  {
    id: "p4",
    promptRo: "Ce reprezintă 'mastery threshold' (pragul de stăpânire)?",
    options: [
      "Scorul minim EWMA la care un concept este considerat stăpânit",
      "Numărul de exerciții dintr-un set",
      "Durata medie a unei sesiuni de studiu",
      "Numărul de erori permise la un examen",
    ],
  },
  {
    id: "p5",
    promptRo: "Ce este un 'misconception' (greșeală conceptuală) în context educațional?",
    options: [
      "O credință incorectă dar sistematică pe care un student o are despre un concept",
      "O greșeală de calcul întâmplătoare",
      "O întrebare la care studentul nu știe răspunsul",
      "Un concept nou pe care studentul nu l-a întâlnit încă",
    ],
  },
  {
    id: "p6",
    promptRo: "Cum influențează 'retrieval practice' (practica de reamintire) consolidarea memoriei?",
    options: [
      "Testarea activă a cunoștințelor consolidează mai bine memoria decât simpla recitire",
      "Sublinierea textului cu marker este cea mai eficientă metodă",
      "Cititul cu voce tare înlocuiește orice altă formă de recapitulare",
      "Rezolvarea exercițiilor nu ajută la memorarea definițiilor",
    ],
  },
  {
    id: "p7",
    promptRo: "Ce înseamnă 'interleaving' în context educațional?",
    options: [
      "Alternarea tipurilor de probleme sau subiecte în cadrul aceleiași sesiuni",
      "Studierea unui singur subiect pe sesiune, fără întrerupere",
      "Rezolvarea exercițiilor în ordinea crescătoare a dificultății",
      "Gruparea problemelor similare în blocuri compacte",
    ],
  },
  {
    id: "p8",
    promptRo: "Ce rol are 'feedback imediat' (immediate feedback) în procesul de învățare?",
    options: [
      "Permite corectarea rapidă a greșelilor și previne consolidarea cunoașterii eronate",
      "Motivează studentul prin note mari",
      "Reduce numărul de exerciții necesare",
      "Înlocuiește explicațiile profesorului",
    ],
  },
];

function buildResult(answers: number[]): PlacementResult {
  // Score: first option (index 0) is always correct in this fixture
  const correct = answers.filter((a) => a === 0).length;
  const total = QUESTIONS.length;
  let recommendation: string;
  const pct = correct / total;
  if (pct >= 0.75) {
    recommendation =
      "Nivelul tău estimat: avansat. Vom sări peste introducerile de bază și vom trece direct la practică.";
  } else if (pct >= 0.4) {
    recommendation =
      "Nivelul tău estimat: intermediar. Vom combina consolidarea bazelor cu exerciții mai complexe.";
  } else {
    recommendation =
      "Nivelul tău estimat: începător. Vom construi fundația pas cu pas, cu exemple concrete.";
  }
  return { score: correct, total, recommendation };
}

export function PlacementShell({ onComplete }: Props) {
  const [currentQ, setCurrentQ] = useState(0);
  const [answers, setAnswers] = useState<number[]>([]);
  const [result, setResult] = useState<PlacementResult | null>(null);

  function handleAnswer(optionIndex: number) {
    const newAnswers = [...answers, optionIndex];
    setAnswers(newAnswers);

    if (currentQ + 1 < QUESTIONS.length) {
      setCurrentQ((q) => q + 1);
    } else {
      setResult(buildResult(newAnswers));
    }
  }

  return (
    <div
      data-testid="placement-shell"
      className="min-h-screen bg-panel-dark-bg text-panel-dark-fg font-mono flex flex-col items-center px-4 py-8 gap-8"
    >
      {/* Progress pips */}
      <div className="flex items-center gap-2">
        {QUESTIONS.map((_, i) => (
          <div
            key={i}
            data-testid={`placement-progress-pip-${i}`}
            className={
              "w-2 h-2 " +
              (i < answers.length
                ? "bg-accent"
                : i === currentQ
                  ? "bg-panel-dark-fg/70"
                  : "bg-panel-dark-fg/20")
            }
          />
        ))}
      </div>

      {/* Header */}
      {!result && (
        <p className="text-[10px] font-bold tracking-widest uppercase text-panel-dark-fg/40">
          Întrebarea {currentQ + 1} din {QUESTIONS.length}
        </p>
      )}

      {/* Question or result */}
      <div className="w-full max-w-lg">
        {result ? (
          <PlacementResultBanner result={result} onContinue={onComplete} />
        ) : (
          <PlacementQuestion
            question={QUESTIONS[currentQ]}
            onAnswer={handleAnswer}
            disabled={false}
          />
        )}
      </div>
    </div>
  );
}
