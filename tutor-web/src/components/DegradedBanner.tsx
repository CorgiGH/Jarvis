/** DegradedBanner — sticky top banner shown in degraded / partial-offline mode (surface 14).
 *
 *  Renders an amber-toned warning strip.
 *  Romanian copy by default; custom message prop for specificity.
 *
 *  testids: degraded-banner
 */
const DEFAULT_MESSAGE =
  "Mod degradat — unele funcții sunt indisponibile din cauza problemelor de conexiune.";

interface Props {
  message?: string;
}

export function DegradedBanner({ message }: Props) {
  return (
    <div
      data-testid="degraded-banner"
      role="status"
      aria-live="polite"
      className="w-full bg-yellow-900/30 border-b border-yellow-600/40 px-4 py-2 font-mono flex items-center gap-3"
    >
      {/* Warning icon */}
      <span className="text-yellow-500 font-bold text-xs" aria-hidden="true">
        ⚠
      </span>
      <p className="text-[11px] text-yellow-400/90 tracking-wide">
        {message ?? DEFAULT_MESSAGE}
      </p>
    </div>
  );
}
