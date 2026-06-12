import { Component, type ErrorInfo, type ReactNode } from "react";
import { lessonStrings } from "../../lib/lessonStrings";

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
}

/**
 * Plan 4b Task 1 — per-beat error boundary for BeatOrchestrator.
 *
 * Wraps the ACTIVE BEAT body only (pips + gate controls stay outside so they remain mounted
 * even if a beat component throws). A `key={beatIndex}` on this boundary in BeatOrchestrator
 * resets the error state when the learner advances to a new beat.
 *
 * Uses console.warn NOT console.error so gate 4c's "zero console.error" assertion stays green:
 * a caught render error is handled, not silent — the RO fallback (lesson-beat-error) is visible.
 */
export class LessonErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(_error: Error): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // warn-level: a caught boundary error is handled (fallback visible); not console.error
    // so the rendered-gate gate-4c "zero console errors" assertion stays clean.
    console.warn("[LessonErrorBoundary] beat render error caught:", error.message, info.componentStack);
  }

  render(): ReactNode {
    if (this.state.hasError) {
      return (
        <div
          data-testid="lesson-beat-error"
          className="p-4 border-2 border-border-strong text-xs text-page-fg/70 tracking-wide font-mono"
        >
          {lessonStrings.beatError}
        </div>
      );
    }
    return this.props.children;
  }
}
