import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import type { ThemeChoice } from "../door/palettes";
import {
  applyThemeToRoot,
  loadTheme,
  saveTheme,
  applyMotionToRoot,
  loadMotion,
  saveMotion,
  prefersReducedMotionNow,
  type MotionPreference,
} from "./applyTheme";

interface ThemeCtx {
  choice: ThemeChoice;
  setChoice: (c: ThemeChoice) => void;
  motionPreference: MotionPreference;
  setMotionPreference: (p: MotionPreference) => void;
}

const Ctx = createContext<ThemeCtx | null>(null);

/** App-wide theme: holds the palette choice + the manual motion preference,
 *  writes both to :root, and persists them. */
export function ThemeProvider({ children }: { children: ReactNode }): ReactNode {
  const [choice, setChoiceState] = useState<ThemeChoice>(() => loadTheme());
  const [motionPreference, setMotionState] = useState<MotionPreference>(
    () => loadMotion(),
  );

  useEffect(() => {
    applyThemeToRoot(choice);
    saveTheme(choice);
  }, [choice]);

  useEffect(() => {
    applyMotionToRoot(motionPreference);
    saveMotion(motionPreference);
  }, [motionPreference]);

  return (
    <Ctx.Provider
      value={{
        choice,
        setChoice: setChoiceState,
        motionPreference,
        setMotionPreference: setMotionState,
      }}
    >
      {children}
    </Ctx.Provider>
  );
}

export function useTheme(): ThemeCtx {
  const v = useContext(Ctx);
  if (!v) throw new Error("useTheme must be used inside <ThemeProvider>");
  return v;
}

/** Resolved reduced-motion boolean for React render sites: the manual
 *  "reduced" toggle wins; otherwise honor the OS `prefers-reduced-motion`.
 *  Reactive — consumers re-render when the toggle flips.
 *
 *  Degrades gracefully when used OUTSIDE a `<ThemeProvider>` (e.g. a component
 *  rendered standalone in a unit test): falls back to the imperative resolver
 *  (manual `data-reduce-motion` attribute OR the OS preference) instead of
 *  throwing, so presentational consumers need no provider to render. */
export function useReducedMotion(): boolean {
  const ctx = useContext(Ctx);
  if (ctx?.motionPreference === "reduced") return true;
  return prefersReducedMotionNow();
}
