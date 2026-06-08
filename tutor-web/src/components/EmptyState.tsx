/** EmptyState — 3-variant empty content placeholder (surfaces 13 + 14).
 *
 *  Variants: "no-queue" | "no-content" | "no-results"
 *  Optional custom message overrides the default copy.
 *
 *  testids: empty-state · empty-state-variant-{variant}
 */
export type EmptyVariant = "no-queue" | "no-content" | "no-results";

const DEFAULT_COPY: Record<EmptyVariant, string> = {
  "no-queue":
    "Ai terminat tot pentru azi. Revino mâine sau explorează alte materii.",
  "no-content":
    "Nu există conținut disponibil pentru această materie momentan.",
  "no-results":
    "Nu există rezultate pentru căutarea ta. Încearcă alți termeni.",
};

interface Props {
  variant: EmptyVariant;
  message?: string;
}

export function EmptyState({ variant, message }: Props) {
  const copy = message ?? DEFAULT_COPY[variant];

  return (
    <div
      data-testid="empty-state"
      className="flex flex-col items-center justify-center gap-3 py-12 font-mono text-center px-4"
    >
      <div
        data-testid={`empty-state-variant-${variant}`}
        className="flex flex-col items-center gap-2"
      >
        {/* Minimal icon — dashed square */}
        <div className="w-10 h-10 border-2 border-dashed border-border-strong flex items-center justify-center">
          <div className="w-4 h-4 bg-page-fg/10" />
        </div>

        <p className="text-xs text-page-fg/50 tracking-wide max-w-xs leading-relaxed">
          {copy}
        </p>
      </div>
    </div>
  );
}
