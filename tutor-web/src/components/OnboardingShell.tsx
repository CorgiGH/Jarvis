/** OnboardingShell — 5-step full-page DARK onboarding (surface 4).
 *
 *  Steps:
 *   1 — AI literacy gate (reuses AiLiteracyGate)
 *   2 — ToS / privacy
 *   3 — Placement intent ("Vrei să faci testul de plasare?")
 *   4 — Profile + LangToggle
 *   5 — Notifications
 *
 *  Each step is gated by the previous completing.
 *  LangToggle + VoxButton (tombstone) ship here.
 *
 *  testids: onboarding-shell · onboarding-step-{1..5} · lang-toggle · vox-btn-disabled
 */
import { useState } from "react";
import { AiLiteracyGate } from "./AiLiteracyGate";
import { LangToggle, type Lang } from "./LangToggle";
import { VoxButton } from "./VoxButton";

interface Props {
  onComplete: () => void;
}

const TOTAL_STEPS = 5;

export function OnboardingShell({ onComplete }: Props) {
  const [step, setStep] = useState(1);
  const [lang, setLang] = useState<Lang>("ro");

  function next() {
    if (step < TOTAL_STEPS) {
      setStep((s) => s + 1);
    } else {
      onComplete();
    }
  }

  return (
    <div
      data-testid="onboarding-shell"
      className="min-h-screen bg-panel-dark-bg text-panel-dark-fg font-mono flex flex-col items-center px-4 py-8"
    >
      {/* Progress pips */}
      <div className="flex items-center gap-2 mb-8">
        {Array.from({ length: TOTAL_STEPS }, (_, i) => (
          <div
            key={i}
            className={
              "w-2 h-2 rounded-none " +
              (i + 1 <= step
                ? "bg-accent"
                : "bg-panel-dark-fg/20")
            }
          />
        ))}
      </div>

      {/* Lang toggle + VoxButton always visible */}
      <div className="flex items-center gap-4 mb-8 self-end">
        <LangToggle lang={lang} onToggle={setLang} />
        <VoxButton />
      </div>

      {/* Step 1 — AI literacy */}
      {step === 1 && (
        <div data-testid="onboarding-step-1" className="w-full max-w-lg">
          <AiLiteracyGate lang={lang} onConfirmed={next} />
        </div>
      )}

      {/* Step 2 — ToS / privacy */}
      {step === 2 && (
        <div data-testid="onboarding-step-2" className="w-full max-w-lg flex flex-col gap-6">
          <h2 className="text-base font-bold tracking-widest uppercase">
            {lang === "ro" ? "Termeni și confidențialitate" : "Terms & Privacy"}
          </h2>
          <p className="text-sm text-panel-dark-fg/80 leading-relaxed">
            {lang === "ro"
              ? "Datele tale de progres sunt stocate în siguranță pe serverele Jarvis și nu sunt partajate cu terți. Poți solicita exportul sau ștergerea oricând din Setări → Confidențialitate."
              : "Your progress data is stored securely on Jarvis servers and is not shared with third parties. You may request export or deletion at any time from Settings → Privacy."}
          </p>
          <button
            type="button"
            onClick={next}
            className="self-start bg-accent text-page-fg text-xs font-bold tracking-widest uppercase px-4 py-2 hover:bg-accent-hover"
          >
            {lang === "ro" ? "Accept — Continuă" : "Accept — Continue"}
          </button>
        </div>
      )}

      {/* Step 3 — Placement intent */}
      {step === 3 && (
        <div data-testid="onboarding-step-3" className="w-full max-w-lg flex flex-col gap-6">
          <h2 className="text-base font-bold tracking-widest uppercase">
            {lang === "ro" ? "Test de plasare" : "Placement Test"}
          </h2>
          <p className="text-sm text-panel-dark-fg/80 leading-relaxed">
            {lang === "ro"
              ? "Vrei să faci testul de plasare acum? Acesta ne ajută să stabilim nivelul tău de cunoștințe și să îți oferim un program de studiu personalizat."
              : "Would you like to take the placement test now? It helps us assess your knowledge level and provide a personalised study plan."}
          </p>
          <div className="flex gap-3">
            <button
              type="button"
              onClick={next}
              className="bg-accent text-page-fg text-xs font-bold tracking-widest uppercase px-4 py-2 hover:bg-accent-hover"
            >
              {lang === "ro" ? "Da, continuă" : "Yes, continue"}
            </button>
            <button
              type="button"
              onClick={next}
              className="border border-panel-dark-fg/40 text-panel-dark-fg/70 text-xs font-bold tracking-widest uppercase px-4 py-2 hover:border-panel-dark-fg/70"
            >
              {lang === "ro" ? "Nu acum" : "Not now"}
            </button>
          </div>
        </div>
      )}

      {/* Step 4 — Profile + LangToggle */}
      {step === 4 && (
        <div data-testid="onboarding-step-4" className="w-full max-w-lg flex flex-col gap-6">
          <h2 className="text-base font-bold tracking-widest uppercase">
            {lang === "ro" ? "Profilul tău" : "Your Profile"}
          </h2>
          <p className="text-sm text-panel-dark-fg/80">
            {lang === "ro"
              ? "Alege limba preferată pentru explicații:"
              : "Choose your preferred language for explanations:"}
          </p>
          <LangToggle lang={lang} onToggle={setLang} />
          <button
            type="button"
            onClick={next}
            className="self-start bg-accent text-page-fg text-xs font-bold tracking-widest uppercase px-4 py-2 hover:bg-accent-hover mt-2"
          >
            {lang === "ro" ? "Gata — Continuă" : "Done — Continue"}
          </button>
        </div>
      )}

      {/* Step 5 — Notifications */}
      {step === 5 && (
        <div data-testid="onboarding-step-5" className="w-full max-w-lg flex flex-col gap-6">
          <h2 className="text-base font-bold tracking-widest uppercase">
            {lang === "ro" ? "Notificări" : "Notifications"}
          </h2>
          <p className="text-sm text-panel-dark-fg/80 leading-relaxed">
            {lang === "ro"
              ? "Jarvis îți poate reaminti să revizuiești materialul la momentul optim. Notificările sunt opționale și pot fi dezactivate oricând."
              : "Jarvis can remind you to review material at the optimal time. Notifications are optional and can be disabled at any time."}
          </p>
          <button
            type="button"
            onClick={next}
            className="self-start bg-accent text-page-fg text-xs font-bold tracking-widest uppercase px-4 py-2 hover:bg-accent-hover"
          >
            {lang === "ro" ? "Finalizează" : "Finish"}
          </button>
        </div>
      )}
    </div>
  );
}
