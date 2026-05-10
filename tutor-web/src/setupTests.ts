import "@testing-library/jest-dom";
import { expect, vi } from "vitest";
import * as matchers from "vitest-axe/matchers";
import React from "react";

expect.extend(matchers);

// jsdom 25 does not implement PointerEvent. Polyfill it so that
// fireEvent.pointerDown/Move/Up carry clientX/Y through MouseEvent.
if (typeof window !== "undefined" && !window.PointerEvent) {
  class PointerEventPolyfill extends MouseEvent {
    readonly pointerId: number;
    constructor(type: string, init: PointerEventInit = {}) {
      super(type, init);
      this.pointerId = init.pointerId ?? 0;
    }
  }
  (window as any).PointerEvent = PointerEventPolyfill;
}

// react-pdf depends on DOMMatrix + Worker which jsdom doesn't provide.
// Stub at module level so any test that mounts <PdfPane> (transitively via
// TutorWorkspace etc.) doesn't try to load the real pdfjs-dist worker.
vi.mock("react-pdf", () => ({
  Document: ({ children, file }: any) =>
    React.createElement("div", { "data-testid": "mock-pdf-document", "data-file": file }, children),
  Page: ({ pageNumber }: any) =>
    React.createElement("div", { "data-page-number": pageNumber }, `mock pdf page ${pageNumber}`),
  pdfjs: { GlobalWorkerOptions: {}, version: "5.0.0" },
}));
vi.mock("react-pdf/dist/Page/AnnotationLayer.css", () => ({}));
vi.mock("react-pdf/dist/Page/TextLayer.css", () => ({}));

// Plotly + WebGL aren't available under jsdom. Stub the lazy-loaded
// react-plotly.js factory + plotly.js-dist-min so PlotlyEmbed renders
// without trying to load the real ~3MB Plotly bundle.
vi.mock("react-plotly.js/factory", () => ({
  default: () => () =>
    React.createElement("div", { "data-testid": "mock-plotly" }, "mock plot"),
}));
vi.mock("plotly.js-dist-min", () => ({ default: {} }));
