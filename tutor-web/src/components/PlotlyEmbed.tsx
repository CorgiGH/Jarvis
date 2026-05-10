import { lazy, Suspense } from "react";

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

export function PlotlyEmbed({ figure }: { figure: PlotlyFigure }) {
  return (
    <div data-testid="plotly-embed" className="my-2">
      <Suspense fallback={<div className="text-xs text-page-fg/60">loading plot…</div>}>
        <Plot
          data={figure.data ?? []}
          layout={{ ...(figure.layout ?? {}), autosize: true }}
          config={{ displayModeBar: false, ...(figure.config ?? {}) }}
          style={{ width: "100%", maxWidth: 800 }}
          useResizeHandler
        />
      </Suspense>
    </div>
  );
}
