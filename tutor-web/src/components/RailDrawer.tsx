import { useEffect, type ReactNode } from "react";
import type { RailItem } from "../lib/taskPrep";

interface RailDrawerProps {
  item: RailItem;
  onClose: () => void;
  children: ReactNode;
}

/**
 * Slide-in drawer wrapper. Animation #7 from spec §G (220ms ease-out).
 * Reduced-motion handled by CSS class `.rail-drawer-slide-in` whose
 * @media (prefers-reduced-motion: reduce) clause sets animation: none.
 *
 * Esc to close + click on close button to close. Focus management
 * deferred to Slice 2 (a11y backlog).
 */
export function RailDrawer({ item, onClose, children }: RailDrawerProps) {
  useEffect(() => {
    function handleKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [onClose]);

  return (
    <div
      data-testid="rail-drawer"
      data-type={item.type}
      className="rail-drawer-slide-in fixed top-0 right-0 h-full w-[480px] max-w-[80vw] z-50 bg-page-bg border-l-4 border-border-strong shadow-2xl flex flex-col"
    >
      <header className="flex items-center justify-between px-4 py-2 border-b-4 border-border-strong bg-panel-dark-bg text-panel-dark-fg font-mono text-xs tracking-widest">
        <span className="font-bold">{item.type} · {item.label}</span>
        <button
          data-testid="rail-drawer-close"
          aria-label="Close drawer"
          onClick={onClose}
          className="px-2 py-0.5 hover:bg-accent hover:text-page-fg"
        >
          ✕
        </button>
      </header>
      <div className="flex-1 min-h-0 overflow-auto">
        {children}
      </div>
    </div>
  );
}
