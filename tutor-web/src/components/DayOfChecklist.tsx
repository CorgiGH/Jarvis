/** DayOfChecklist — FII exam-day checklist.
 *
 *  Hardcoded Romanian checklist items.
 *  Each item can be toggled (checked state is local).
 *
 *  testids: day-of-checklist
 */
import { useState } from "react";

const CHECKLIST_ITEMS = [
  "Legitimație student / buletin",
  "Pix (de rezervă) — cerneală neagră sau albastră",
  "Apă și o gustare ușoară",
  "Am dormit suficient",
  "Știu sala și ora exactă de examen",
  "Am calculatorul / foile de formule permise",
];

export function DayOfChecklist() {
  const [checked, setChecked] = useState<boolean[]>(
    () => CHECKLIST_ITEMS.map(() => false),
  );

  function toggle(i: number) {
    setChecked((prev) => {
      const next = [...prev];
      next[i] = !next[i];
      return next;
    });
  }

  return (
    <div
      data-testid="day-of-checklist"
      className="flex flex-col gap-2 font-mono"
    >
      <p className="text-[10px] font-bold tracking-widest uppercase text-page-fg/50 mb-1">
        Checklist examen
      </p>
      {CHECKLIST_ITEMS.map((item, i) => (
        <label
          key={item}
          className="flex items-center gap-3 cursor-pointer group"
        >
          <input
            type="checkbox"
            checked={checked[i]}
            onChange={() => toggle(i)}
            className="accent-[var(--color-accent)] w-4 h-4 cursor-pointer"
            aria-label={item}
          />
          <span
            className={
              "text-xs tracking-wide transition-colors " +
              (checked[i] ? "text-page-fg/40 line-through" : "text-page-fg/80")
            }
          >
            {item}
          </span>
        </label>
      ))}
    </div>
  );
}
