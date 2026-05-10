import { useEffect, useRef, useState } from "react";
import { Document, Page, pdfjs } from "react-pdf";
import "react-pdf/dist/Page/AnnotationLayer.css";
import "react-pdf/dist/Page/TextLayer.css";
import { jarvisFetch } from "../lib/api";

// pdf.js worker. Load from CDN bundled with the same pdfjs-dist version
// the package.json pinned. Build-time bundling would be cleaner but
// requires Vite worker plugin setup; CDN is acceptable for now.
pdfjs.GlobalWorkerOptions.workerSrc =
  `https://cdn.jsdelivr.net/npm/pdfjs-dist@${pdfjs.version}/build/pdf.worker.min.mjs`;

export interface PdfPaneProps {
  url: string;
  /** Phase 4.3: called when user highlights ≥3 chars in the text layer
   *  and clicks the "I don't know this" tooltip. Parent posts a gap. */
  onPdfSelectionGap?: (selection: { text: string; page: number }) => void;
}

export function PdfPane({ url, onPdfSelectionGap }: PdfPaneProps) {
  const [error, setError] = useState<string | null>(null);
  const [size, setSize] = useState<number | null>(null);
  const [numPages, setNumPages] = useState<number | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [tooltip, setTooltip] = useState<{ x: number; y: number; text: string; page: number } | null>(null);

  useEffect(() => {
    let cancelled = false;
    jarvisFetch(url)
      .then(async r => {
        if (cancelled) return;
        if (!r.ok) { setError(`HTTP ${r.status}`); return; }
        const blob = await r.blob();
        setSize(blob.size);
        if (blob.size < 200) setError("placeholder PDF (<200 bytes)");
      })
      .catch(e => { if (!cancelled) setError((e as Error).message); });
    return () => { cancelled = true; };
  }, [url]);

  useEffect(() => {
    if (!onPdfSelectionGap) return;
    function onSelectionChange() {
      const sel = window.getSelection();
      if (!sel || sel.rangeCount === 0 || sel.isCollapsed) {
        setTooltip(null); return;
      }
      const text = sel.toString().trim();
      if (text.length < 3) { setTooltip(null); return; }
      const range = sel.getRangeAt(0);
      if (!containerRef.current?.contains(range.commonAncestorContainer)) {
        setTooltip(null); return;
      }
      let node: Node | null = range.commonAncestorContainer;
      let page = 1;
      while (node && node !== containerRef.current) {
        if (node instanceof HTMLElement && node.dataset.pageNumber) {
          page = parseInt(node.dataset.pageNumber, 10) || 1;
          break;
        }
        node = node.parentNode;
      }
      let x = 0, y = 0;
      try {
        const rect = range.getBoundingClientRect();
        const containerRect = containerRef.current!.getBoundingClientRect();
        x = rect.left - containerRect.left;
        y = rect.bottom - containerRect.top + 4;
      } catch (_) { /* jsdom doesn't implement getBoundingClientRect on Range */ }
      setTooltip({ x, y, text: text.slice(0, 200), page });
    }
    document.addEventListener("selectionchange", onSelectionChange);
    return () => document.removeEventListener("selectionchange", onSelectionChange);
  }, [onPdfSelectionGap]);

  if (error) {
    return (
      <div data-testid="pdf-pane"
           tabIndex={0}
           aria-label="PDF viewer"
           className="h-full bg-surface-muted overflow-auto relative p-6 font-mono text-sm">
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
    );
  }

  return (
    <div data-testid="pdf-pane"
         tabIndex={0}
         role="region"
         aria-label="PDF viewer"
         ref={containerRef}
         className="h-full bg-surface-muted overflow-auto relative">
      <Document
        file={url}
        onLoadSuccess={({ numPages: n }) => setNumPages(n)}
        onLoadError={(e) => setError(e.message)}
        loading={<div className="p-6 text-xs">loading PDF…</div>}
      >
        {numPages && Array.from({ length: numPages }, (_, i) => i + 1).map(p => (
          <Page key={p} pageNumber={p} renderTextLayer={true} renderAnnotationLayer={false} />
        ))}
      </Document>
      {tooltip && (
        <button
          data-testid="pdf-selection-tooltip"
          onClick={() => {
            onPdfSelectionGap?.({ text: tooltip.text, page: tooltip.page });
            setTooltip(null);
            window.getSelection()?.removeAllRanges();
          }}
          className="absolute z-10 bg-panel-dark-bg text-panel-dark-fg text-xs font-bold tracking-widest px-2 py-1"
          style={{ left: tooltip.x, top: tooltip.y }}
        >
          🤷 I don't know this
        </button>
      )}
      {size != null && (
        <div className="absolute bottom-1 right-1 bg-page-fg/70 text-overlay-fg text-xs px-2 py-0.5 rounded">
          {(size / 1024).toFixed(1)} KB
        </div>
      )}
    </div>
  );
}
