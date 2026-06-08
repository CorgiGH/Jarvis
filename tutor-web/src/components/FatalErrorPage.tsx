/** FatalErrorPage — full-page fatal error surface (surfaces 13 + 14).
 *
 *  Shown when the app hits an unrecoverable error (crash boundary, 500, auth failure).
 *  Provides a "Reîncarcă" button (calls onRetry or reloads the page).
 *
 *  testids: fatal-error-page
 */
const DEFAULT_MESSAGE =
  "A apărut o problemă neașteptată în aplicație. Ne pare rău pentru neplăcere.";

interface Props {
  message?: string;
  onRetry?: () => void;
}

export function FatalErrorPage({ message, onRetry }: Props) {
  function handleRetry() {
    if (onRetry) {
      onRetry();
    } else {
      window.location.reload();
    }
  }

  return (
    <div
      data-testid="fatal-error-page"
      className="min-h-screen flex flex-col items-center justify-center gap-6 font-mono px-4 bg-page-bg text-page-fg"
    >
      {/* Error plate */}
      <div className="flex flex-col items-center gap-3 border border-red-500/40 px-8 py-8 max-w-sm w-full">
        <p className="text-[10px] font-bold tracking-widest uppercase text-red-400">
          Eroare fatală
        </p>
        <p className="text-4xl font-black text-red-500 tracking-tighter">!</p>
        <h1 className="text-sm font-bold tracking-wide text-center text-page-fg/80">
          {message ?? DEFAULT_MESSAGE}
        </h1>
      </div>

      {/* Reload CTA */}
      <button
        type="button"
        onClick={handleRetry}
        className="text-xs font-bold tracking-widest uppercase border border-border-strong px-6 py-2 hover:bg-page-fg hover:text-page-bg transition-colors"
      >
        Reîncarcă pagina
      </button>

      <p className="text-[10px] text-page-fg/30 tracking-widest">
        Dacă problema persistă, contactează suportul.
      </p>
    </div>
  );
}
