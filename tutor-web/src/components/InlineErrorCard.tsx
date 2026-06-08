/** InlineErrorCard — compact inline error display (surfaces 13 + 14).
 *
 *  Renders a bordered error card with message and optional retry button.
 *
 *  testids: inline-error-card
 */
interface Props {
  message: string;
  onRetry?: () => void;
}

export function InlineErrorCard({ message, onRetry }: Props) {
  return (
    <div
      data-testid="inline-error-card"
      role="alert"
      className="border-l-4 border-red-500 bg-red-500/10 px-4 py-3 font-mono flex items-start gap-3"
    >
      {/* Error icon */}
      <span className="text-red-400 font-bold text-sm mt-0.5" aria-hidden="true">
        ✕
      </span>

      <div className="flex flex-col gap-2 flex-1">
        <p className="text-xs text-red-300 tracking-wide leading-snug">{message}</p>

        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="self-start text-[10px] font-bold tracking-widest uppercase border border-red-500/50 text-red-400 px-2 py-0.5 hover:bg-red-500/20"
          >
            Încearcă din nou
          </button>
        )}
      </div>
    </div>
  );
}
