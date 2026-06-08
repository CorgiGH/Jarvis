import type { ReactNode } from "react";
import { useTheme } from "../theme/ThemeProvider";

/** Masthead control for the manual motion toggle (spine §2.3 — ADHD): flips the
 *  app between honoring the OS motion setting ("system") and forcing reduced
 *  motion ("reduced"). The choice persists and recolors nothing — it drives the
 *  `data-reduce-motion` root attribute + the global index.css neutralizer. */
export function MotionToggle(): ReactNode {
  const { motionPreference, setMotionPreference } = useTheme();
  const reduced = motionPreference === "reduced";
  return (
    <button
      type="button"
      data-testid="motion-toggle"
      aria-pressed={reduced}
      aria-label={
        reduced
          ? "Animations off — tap to follow system setting"
          : "Animations on — tap to turn off"
      }
      title={
        reduced
          ? "Animations off (tap to follow system)"
          : "Animations on (tap to turn off)"
      }
      onClick={() => setMotionPreference(reduced ? "system" : "reduced")}
      className="text-[11px] font-bold tracking-widest hover:underline"
    >
      {reduced ? "MOTION OFF" : "MOTION ON"}
    </button>
  );
}
