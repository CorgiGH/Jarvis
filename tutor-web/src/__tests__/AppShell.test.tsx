import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { test, expect, beforeEach } from "vitest";
import { ThemeProvider } from "../theme/ThemeProvider";
import { AppShell } from "../components/AppShell";

beforeEach(() => {
  document.documentElement.removeAttribute("style");
  document.documentElement.removeAttribute("data-reduce-motion");
  localStorage.clear();
});

test("renders the brand circle (theme fab), brand word, nav slot and children", () => {
  render(
    <ThemeProvider>
      <AppShell nav={<a href="#" data-testid="nav-probe">workspace</a>}>
        <div data-testid="content">body</div>
      </AppShell>
    </ThemeProvider>,
  );
  expect(screen.getByTestId("app-shell")).toBeTruthy();
  expect(screen.getByTestId("theme-fab")).toBeTruthy();
  expect(screen.getByText("TUTOR")).toBeTruthy();
  expect(screen.getByTestId("nav-probe")).toBeTruthy();
  expect(screen.getByTestId("content")).toBeTruthy();
});

test("clicking the brand circle opens the palette picker (no concept row)", async () => {
  render(
    <ThemeProvider>
      <AppShell nav={<span />}>
        <span />
      </AppShell>
    </ThemeProvider>,
  );
  await userEvent.click(screen.getByTestId("theme-fab"));
  expect(screen.getByTestId("palette-brand-yellow")).toBeTruthy();
  expect(screen.queryByText("Concept")).toBeNull();
});

test("masthead motion toggle forces reduced motion (data-reduce-motion + aria-pressed)", async () => {
  render(
    <ThemeProvider>
      <AppShell nav={<span />}>
        <span />
      </AppShell>
    </ThemeProvider>,
  );
  const toggle = screen.getByTestId("motion-toggle");
  expect(toggle.getAttribute("aria-pressed")).toBe("false");
  expect(document.documentElement.hasAttribute("data-reduce-motion")).toBe(false);
  await userEvent.click(toggle);
  expect(toggle.getAttribute("aria-pressed")).toBe("true");
  expect(document.documentElement.hasAttribute("data-reduce-motion")).toBe(true);
});
