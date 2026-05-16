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
  /** Optional uploadUrl to PUT a fresh PDF to (matching the GET url shape).
   *  When provided, the empty-state surface shows an upload button. */
  uploadUrl?: string;
}

export function PdfPane({ url, onPdfSelectionGap, uploadUrl }: PdfPaneProps) {
  const [error, setError] = useState<string | null>(null);
  const [size, setSize] = useState<number | null>(null);
  const [numPages, setNumPages] = useState<number | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [tooltip, setTooltip] = useState<{ x: number; y: number; text: string; page: number } | null>(null);
  const tooltipBtnRef = useRef<HTMLButtonElement>(null);
  const tooltipDebounceRef = useRef<number | null>(null);

  async function uploadFile(f: File) {
    if (!uploadUrl) return;
    setUploading(true); setUploadError(null);
    try {
      const r = await jarvisFetch(uploadUrl, {
        method: "PUT",
        headers: { "Content-Type": "application/pdf" },
        body: await f.arrayBuffer(),
      });
      if (!r.ok) throw new Error(`HTTP ${r.status}: ${(await r.text()).slice(0, 200)}`);
      setError(null);
      setSize(null);
      setReloadKey(k => k + 1);
    } catch (e) {
      setUploadError((e as Error).message);
    } finally {
      setUploading(false);
    }
  }

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
  }, [url, reloadKey]);

  useEffect(() => {
    if (!onPdfSelectionGap) return;
    function compute() {
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
        // Edge clamp: tooltip is ~180px wide × ~26px tall (text-xs + py-2 + content).
        // Flip above the selection when bottom-anchored would overflow; clamp x to
        // container right edge minus tooltip width minus a 8px gutter.
        const TIP_W = 180, TIP_H = 26, GUTTER = 8;
        const rawX = rect.left - containerRect.left;
        const rawY = rect.bottom - containerRect.top + 4;
        const containerW = containerRef.current!.clientWidth;
        const containerH = containerRef.current!.clientHeight;
        x = Math.max(GUTTER, Math.min(rawX, containerW - TIP_W - GUTTER));
        y = rawY + TIP_H > containerH
          ? (rect.top - containerRect.top - TIP_H - 4)
          : rawY;
      } catch (_) { /* jsdom doesn't implement getBoundingClientRect on Range */ }
      setTooltip({ x, y, text: text.slice(0, 200), page });
    }
    function onSelectionChange() {
      // Debounce: real-time setTooltip per pixel-of-drag was causing flicker on
      // mobile / older devices. Wait 100ms for the selection to stabilize before
      // measuring the range.
      if (tooltipDebounceRef.current != null) window.clearTimeout(tooltipDebounceRef.current);
      tooltipDebounceRef.current = window.setTimeout(() => {
        tooltipDebounceRef.current = null;
        compute();
      }, 100);
    }
    document.addEventListener("selectionchange", onSelectionChange);
    return () => {
      document.removeEventListener("selectionchange", onSelectionChange);
      if (tooltipDebounceRef.current != null) window.clearTimeout(tooltipDebounceRef.current);
    };
  }, [onPdfSelectionGap]);

  // Focus the tooltip button when it appears so keyboard users (shift+arrow
  // selectors) can press Enter to flag without reaching for the mouse.
  useEffect(() => {
    if (tooltip) tooltipBtnRef.current?.focus();
  }, [tooltip]);

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
        <p className="text-xs text-page-fg/60 mb-3">
          No PDF attached to this task yet.
        </p>
        {uploadUrl && (
          <div className="space-y-2">
            <input
              ref={fileRef}
              type="file"
              accept="application/pdf,.pdf"
              data-testid="pdf-upload-input"
              className="hidden"
              onChange={e => {
                const f = e.target.files?.[0];
                if (f) uploadFile(f);
                e.currentTarget.value = "";
              }}
            />
            <button
              data-testid="pdf-upload-button"
              onClick={() => fileRef.current?.click()}
              disabled={uploading}
              className="text-xs font-bold tracking-widest bg-accent text-page-fg px-3 py-1 border border-border-strong disabled:opacity-50">
              {uploading ? "UPLOADING…" : "UPLOAD PDF"}
            </button>
            {uploadError && (
              <div data-testid="pdf-upload-error" className="text-xs text-danger-text">
                upload failed: {uploadError}
              </div>
            )}
          </div>
        )}
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
          ref={tooltipBtnRef}
          data-testid="pdf-selection-tooltip"
          aria-describedby="pdf-tooltip-hint"
          onClick={() => {
            onPdfSelectionGap?.({ text: tooltip.text, page: tooltip.page });
            setTooltip(null);
            window.getSelection()?.removeAllRanges();
          }}
          className="absolute z-10 bg-panel-dark-bg text-panel-dark-fg text-xs font-bold tracking-widest px-2 py-2 sm:py-1"
          style={{ left: tooltip.x, top: tooltip.y }}
        >
          🤷 I don't know this
          <span id="pdf-tooltip-hint" className="sr-only"> — press Enter to flag this selection as a knowledge gap</span>
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
