import { brutalistVars, type ThemeChoice } from "../door/palettes";

const KEY = "jarvis.theme";
const DEFAULT: ThemeChoice = { paletteId: "brand-yellow" };

/** Write the resolved brutalist palette vars onto :root so every
 *  `bg-accent` / `text-accent` / `border-accent` token recolors at once. */
export function applyThemeToRoot(choice: ThemeChoice): void {
  const vars = brutalistVars(choice) as Record<string, string>;
  const root = document.documentElement;
  for (const [k, v] of Object.entries(vars)) {
    if (k.startsWith("--")) root.style.setProperty(k, v);
  }
}

export function saveTheme(choice: ThemeChoice): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(choice));
  } catch (_) {
    /* storage unavailable — non-fatal */
  }
}

export function loadTheme(): ThemeChoice {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return { ...DEFAULT };
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed.paletteId === "string") return parsed;
    return { ...DEFAULT };
  } catch (_) {
    return { ...DEFAULT };
  }
}

// ---- Manual motion preference (spine §2.3: localStorage motion toggle, ADHD) ----
export type MotionPreference = "system" | "reduced";

const MOTION_KEY = "jarvis.motion";

/** Force-reduced motion is expressed as a `data-reduce-motion` attribute on
 *  <html>; a global rule in index.css then neutralizes all animation +
 *  transition, so BOTH JS-class and CSS-`@media` animated components honor the
 *  manual toggle without per-component edits. */
export function applyMotionToRoot(pref: MotionPreference): void {
  const root = document.documentElement;
  if (pref === "reduced") root.setAttribute("data-reduce-motion", "");
  else root.removeAttribute("data-reduce-motion");
}

export function saveMotion(pref: MotionPreference): void {
  try {
    localStorage.setItem(MOTION_KEY, pref);
  } catch (_) {
    /* storage unavailable — non-fatal */
  }
}

export function loadMotion(): MotionPreference {
  try {
    return localStorage.getItem(MOTION_KEY) === "reduced" ? "reduced" : "system";
  } catch (_) {
    return "system";
  }
}

/** Imperative resolver for useEffect / non-React call sites (e.g. Plotly's
 *  animate effect, viz render): the manual toggle (`data-reduce-motion` on
 *  <html>) wins, else the OS `prefers-reduced-motion`. */
export function prefersReducedMotionNow(): boolean {
  if (
    typeof document !== "undefined" &&
    document.documentElement.hasAttribute("data-reduce-motion")
  ) {
    return true;
  }
  return (
    typeof window !== "undefined" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}
