import { useState } from "react";
import { getCsrfToken } from "../lib/api";

/**
 * AiLiteracyGate — first-login AI-literacy acknowledgement screen.
 *
 * Brutalist mono style (JetBrains Mono, Ink/Paper/Accent tokens).
 * Props:
 *   lang       — "ro" | "en", drives which copy block is shown.
 *   onConfirmed — called after the confirm POST resolves (even on network error,
 *                 to avoid trapping the user in the gate indefinitely).
 *
 * POST /api/v1/me/ai-literacy/confirm with { lang } + X-CSRF-Token header.
 * In-flight guard prevents double-POSTs on rapid clicks.
 */
export function AiLiteracyGate({
  lang,
  onConfirmed,
}: {
  lang: "ro" | "en";
  onConfirmed: () => void;
}) {
  const [inFlight, setInFlight] = useState(false);

  async function handleConfirm() {
    if (inFlight) return;
    setInFlight(true);
    try {
      await fetch("/api/v1/me/ai-literacy/confirm", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-CSRF-Token": getCsrfToken() ?? "",
        },
        credentials: "include",
        body: JSON.stringify({ lang }),
      });
    } catch (_) {
      // network error — still proceed; gate must not trap the user
    }
    onConfirmed();
  }

  return (
    <div
      data-testid="ai-literacy-gate"
      className="p-6 font-mono text-sm flex flex-col items-start gap-6 max-w-2xl"
    >
      <h1 className="text-lg font-bold tracking-widest">
        {lang === "ro" ? "INFORMARE PRIVIND AI — TUTORUL JARVIS" : "AI NOTICE — JARVIS TUTOR"}
      </h1>

      {lang === "ro" ? (
        <div className="flex flex-col gap-3 text-sm text-page-fg">
          <p>
            Tutorul Jarvis folosește un model de limbaj de mari dimensiuni (LLM) pentru a genera
            explicații, sugestii și răspunsuri. Ca orice sistem AI, poate produce răspunsuri incorecte
            sau incomplete.
          </p>
          <p>
            Verifică întotdeauna informațiile primite cu surse de referință (manuale, profesori,
            documentație oficială) înainte să le consideri corecte.
          </p>
          <p>
            Interacțiunile tale cu tutorul sunt înregistrate în contul tău pentru a susține urmărirea
            progresului tău individual de învățare.
          </p>
          <p>
            Poți revoca consimțământul sau solicita ștergerea datelor oricând, din secțiunea
            Setări → Confidențialitate.
          </p>
        </div>
      ) : (
        <div className="flex flex-col gap-3 text-sm text-page-fg">
          <p>
            Jarvis Tutor uses a large language model (LLM) to generate explanations, hints, and
            answers. Like any AI system, it can produce incorrect or incomplete responses.
          </p>
          <p>
            Always verify information you receive against authoritative sources (textbooks, instructors,
            official documentation) before treating it as correct.
          </p>
          <p>
            Your interactions with the tutor are logged to your account to support tracking of your
            individual learning progress.
          </p>
          <p>
            You can withdraw consent or request data deletion at any time from Settings → Privacy.
          </p>
        </div>
      )}

      <div className="border-t-2 border-border-strong w-full pt-4">
        <button
          type="button"
          onClick={handleConfirm}
          disabled={inFlight}
          className="text-xs font-bold tracking-widest bg-panel-dark-bg text-panel-dark-fg px-4 py-2 hover:bg-page-fg disabled:opacity-50 disabled:cursor-not-allowed self-start"
        >
          {lang === "ro" ? "AM ÎNȚELES — CONTINUĂ" : "CONFIRM — CONTINUE"}
        </button>
      </div>
    </div>
  );
}
