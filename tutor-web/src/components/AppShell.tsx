import type { ReactNode } from "react";
import { useTheme } from "../theme/ThemeProvider";
import { ThemePicker } from "../door/ThemePicker";
import { MotionToggle } from "./MotionToggle";

/** App-wide dark masthead: brand-circle theme picker + brand word + a nav slot,
 *  wrapping the routed content. The picker is palette-only in production
 *  (concepts=[] strips the demo concept row). */
export function AppShell({
  nav,
  children,
}: {
  nav: ReactNode;
  children: ReactNode;
}): ReactNode {
  const { choice, setChoice } = useTheme();
  return (
    <div data-testid="app-shell" className="h-dvh flex flex-col">
      <header className="bg-panel-dark-bg text-panel-dark-fg px-4 py-3 flex flex-wrap items-center justify-between gap-2 border-b-4 border-accent">
        <div className="flex items-center gap-3 min-w-0">
          <ThemePicker
            skin="brutalist"
            onSkin={() => {}}
            choice={choice}
            onChoice={setChoice}
            concepts={[]}
            conceptId=""
            onConcept={() => {}}
            size={26}
          />
          <span className="text-lg font-bold tracking-widest">TUTOR</span>
          <MotionToggle />
        </div>
        {nav}
      </header>
      <main className="flex-1 min-h-0 overflow-hidden overflow-y-auto bg-page-bg">
        {children}
      </main>
    </div>
  );
}
