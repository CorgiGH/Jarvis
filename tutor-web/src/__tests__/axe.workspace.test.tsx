import { render } from "@testing-library/react";
import { test, expect, vi, beforeEach, afterEach } from "vitest";
import { axe } from "vitest-axe";
import { MemoryRouter } from "react-router-dom";
import { TutorWorkspace } from "../components/TutorWorkspace";

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn(async () => new Response("{}", { status: 200 })));
  Object.defineProperty(document, "cookie", { value: "csrf=zzz", configurable: true, writable: true });
});
afterEach(() => { vi.unstubAllGlobals(); });

test("TutorWorkspace has no axe violations (color-contrast disabled — jsdom can't measure)", async () => {
  const { container } = render(
    <MemoryRouter><TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" /></MemoryRouter>,
  );
  const results = await axe(container, {
    rules: { "color-contrast": { enabled: false } },
    // jsdom doesn't implement cross-frame messaging; PdfPane renders an
    // <iframe> which axe tries to traverse — skip it.
    iframes: false,
  });
  expect(results).toHaveNoViolations();
});

test("TutorWorkspace with deduped banner has no axe violations", async () => {
  const { container } = render(
    <MemoryRouter>
      <TutorWorkspace pdfUrl="/sample.pdf" taskId="T1" dedupedNotice={true} />
    </MemoryRouter>,
  );
  const results = await axe(container, {
    rules: { "color-contrast": { enabled: false } },
    // jsdom doesn't implement cross-frame messaging; PdfPane renders an
    // <iframe> which axe tries to traverse — skip it.
    iframes: false,
  });
  expect(results).toHaveNoViolations();
});
