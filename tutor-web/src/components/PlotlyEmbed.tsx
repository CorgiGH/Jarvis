import { lazy, Suspense, useEffect, useRef } from "react";

// Lazy import keeps Plotly out of the base bundle. Plotly.js (~3MB)
// only loads when the first <PlotlyEmbed> mounts. Browser caches
// the chunk after first load; subsequent mounts are free.
const Plot = lazy(async () => {
  const factoryMod = await import("react-plotly.js/factory");
  const Plotly = await import("plotly.js-dist-min");
  const factory = (factoryMod as any).default ?? factoryMod;
  const PlotlyDefault = (Plotly as any).default ?? Plotly;
  return { default: factory(PlotlyDefault) };
});

export interface PlotlyFigure {
  data?: any[];
  layout?: any;
  config?: any;
}

export interface PlotlyFrame {
  name: string;
  data: any[];
  layout?: any;
}

export function PlotlyEmbed({
  figure,
  indexLabel,
  frames,
  animateOnMount = false,
}: {
  figure: PlotlyFigure;
  indexLabel?: string;
  frames?: PlotlyFrame[];
  animateOnMount?: boolean;
}) {
  // Caption: prefer figure.layout.title.text (set by the LLM in the
  // ```plotly envelope), fall back to the bare title string, then to
  // a generic FIG marker. Mirrors the brutalist-mono mockup's
  // "FIG 1 · LAPLACE DENSITY · μ=0" caption strip.
  const layoutTitle = figure.layout?.title;
  const titleText: string =
    (typeof layoutTitle === "string" && layoutTitle) ||
    (layoutTitle && typeof layoutTitle.text === "string" && layoutTitle.text) ||
    "";
  const caption = titleText
    ? `${indexLabel ?? "FIG"} · ${titleText}`
    : (indexLabel ?? "FIG");
  // Strip the layout title once we render it ourselves so plotly
  // doesn't double-render it inside the chart area.
  const layoutWithoutTitle = { ...(figure.layout ?? {}) };
  delete (layoutWithoutTitle as any).title;

  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!containerRef.current || !frames || !animateOnMount) return;
    const el = containerRef.current.querySelector(".js-plotly-plot");
    if (!el) return;
    import("plotly.js-dist-min").then((mod) => {
      const Plotly = (mod as any).default ?? mod;
      if (typeof Plotly.animate !== "function") return;
      const prefersReduced =
        typeof window !== "undefined" &&
        window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      const dur = prefersReduced ? 0 : 600;
      Plotly.animate(el, frames, {
        transition: { duration: dur, easing: "cubic-bezier(.4,0,.2,1)" },
        frame: { duration: dur },
      });
    });
  }, [frames, animateOnMount]);

  return (
    <div data-testid="plotly-embed" className="my-2 border-2 border-border-strong">
      <div data-testid="plotly-caption"
           className="bg-panel-dark-bg text-panel-dark-fg text-[10px] tracking-widest font-bold uppercase px-3 py-1">
        {caption}
      </div>
      <div ref={containerRef}>
        <Suspense fallback={<div className="text-xs text-page-fg/60 p-2">loading plot…</div>}>
          <Plot
            data={figure.data ?? []}
            layout={{ ...layoutWithoutTitle, autosize: true }}
            config={{ displayModeBar: false, ...(figure.config ?? {}) }}
            className="w-full max-w-[800px]"
            useResizeHandler
          />
        </Suspense>
      </div>
    </div>
  );
}
