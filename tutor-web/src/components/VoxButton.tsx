/** VoxButton — voice read-aloud button tombstone (disabled).
 *
 *  Voice feature is deferred (ElevenLabs / voice-clone not yet active).
 *  This tombstone renders but is always disabled, with a tooltip explaining why.
 *
 *  testids: vox-btn-disabled
 */
export function VoxButton() {
  return (
    <div data-testid="vox-btn-disabled" className="inline-flex items-center gap-2">
      <button
        type="button"
        disabled
        title="Voce indisponibilă — activare viitoare"
        aria-label="Citire cu voce (indisponibil)"
        className="flex items-center gap-1 px-2 py-1 text-[10px] font-mono font-bold tracking-widest uppercase
          border border-border-strong text-page-fg/30 cursor-not-allowed opacity-40 select-none"
      >
        {/* Speaker icon (inline SVG — no external dep) */}
        <svg
          width="12"
          height="12"
          viewBox="0 0 12 12"
          fill="currentColor"
          aria-hidden="true"
        >
          <path d="M2 4h2l3-3v10L4 8H2V4zm8 2a4 4 0 0 0-1.17-2.83l-.7.7A3 3 0 0 1 9 6a3 3 0 0 1-.87 2.13l.7.7A4 4 0 0 0 10 6z" />
        </svg>
        Voce indisponibil
      </button>
    </div>
  );
}
