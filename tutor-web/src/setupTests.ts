import "@testing-library/jest-dom";
import { expect, vi } from "vitest";
import * as matchers from "vitest-axe/matchers";
import React from "react";

expect.extend(matchers);

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
