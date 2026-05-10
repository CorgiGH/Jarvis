import { useEffect, useState } from "react";
import { jarvisFetch } from "../lib/api";

export function PdfPane({ url }: { url: string }) {
  const [error, setError] = useState<string | null>(null);
  const [size, setSize] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;
    jarvisFetch(url)
      .then(async r => {
        if (cancelled) return;
        if (!r.ok) {
          setError(`HTTP ${r.status}`);
          return;
        }
        const blob = await r.blob();
        setSize(blob.size);
        if (blob.size < 200) setError("placeholder PDF (<200 bytes)");
      })
      .catch(e => { if (!cancelled) setError((e as Error).message); });
    return () => { cancelled = true; };
  }, [url]);

  return (
    <div data-testid="pdf-pane"
         tabIndex={0}
         aria-label="PDF viewer"
         className="h-full bg-surface-muted overflow-auto relative">
      {error ? (
        <div className="p-6 font-mono text-sm">
          <div className="text-xs font-bold tracking-widest text-page-fg/70 mb-2">PDF</div>
          <div className="text-page-fg/80 mb-2 break-all">{url}</div>
          <div className="bg-accent-soft border-l-4 border-accent-rule p-3 mb-3">
            <div className="font-bold text-xs tracking-widest mb-1">PDF NOT VIEWABLE</div>
            <div className="text-xs text-page-fg/70">{error}</div>
          </div>
          <p className="text-xs text-page-fg/60">
            The PDF is empty or your browser can't render it inline. Drop a real
            problem PDF in <code>tutor-web/public/</code> with the same filename
            (or update the task to point at a different file).
          </p>
        </div>
      ) : (
        <>
          <iframe src={url} title="PDF document for current task" className="w-full h-full border-0" />
          {size != null && (
            <div className="absolute bottom-1 right-1 bg-page-fg/70 text-overlay-fg text-xs px-2 py-0.5 rounded">
              {(size / 1024).toFixed(1)} KB
            </div>
          )}
        </>
      )}
    </div>
  );
}
