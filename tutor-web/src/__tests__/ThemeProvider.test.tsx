import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { test, expect, beforeEach } from "vitest";
import { ThemeProvider, useTheme, useReducedMotion } from "../theme/ThemeProvider";

beforeEach(() => {
  document.documentElement.removeAttribute("style");
  document.documentElement.removeAttribute("data-reduce-motion");
  localStorage.clear();
});

function Probe() {
  const { choice, setChoice } = useTheme();
  return (
    <div>
      <span data-testid="pid">{choice.paletteId}</span>
      <button onClick={() => setChoice({ paletteId: "indigo-mint" })}>indigo</button>
    </div>
  );
}

test("provider applies the default palette to :root on mount", () => {
  render(
    <ThemeProvider>
      <Probe />
    </ThemeProvider>,
  );
  expect(screen.getByTestId("pid").textContent).toBe("brand-yellow");
  // brand-yellow primary #FDE047
  expect(
    document.documentElement.style.getPropertyValue("--color-accent").toUpperCase(),
  ).toBe("#FDE047");
});

test("setChoice recolors :root and persists", async () => {
  render(
    <ThemeProvider>
      <Probe />
    </ThemeProvider>,
  );
  await userEvent.click(screen.getByText("indigo"));
  expect(screen.getByTestId("pid").textContent).toBe("indigo-mint");
  // indigo-mint primary #8B7CFF
  expect(
    document.documentElement.style.getPropertyValue("--color-accent").toUpperCase(),
  ).toBe("#8B7CFF");
  expect(JSON.parse(localStorage.getItem("jarvis.theme")!).paletteId).toBe("indigo-mint");
});

test("provider restores a persisted choice on mount", () => {
  localStorage.setItem("jarvis.theme", JSON.stringify({ paletteId: "amber-sky" }));
  render(
    <ThemeProvider>
      <Probe />
    </ThemeProvider>,
  );
  expect(screen.getByTestId("pid").textContent).toBe("amber-sky");
});

function MotionProbe() {
  const { motionPreference, setMotionPreference } = useTheme();
  const reduced = useReducedMotion();
  return (
    <div>
      <span data-testid="mpref">{motionPreference}</span>
      <span data-testid="mreduced">{String(reduced)}</span>
      <button onClick={() => setMotionPreference("reduced")}>reduce</button>
      <button onClick={() => setMotionPreference("system")}>system</button>
    </div>
  );
}

test("motion preference defaults to system — no data-reduce-motion on mount", () => {
  render(
    <ThemeProvider>
      <MotionProbe />
    </ThemeProvider>,
  );
  expect(screen.getByTestId("mpref").textContent).toBe("system");
  expect(document.documentElement.hasAttribute("data-reduce-motion")).toBe(false);
});

test("forcing reduced motion sets the root attribute, persists, and toggles back", async () => {
  render(
    <ThemeProvider>
      <MotionProbe />
    </ThemeProvider>,
  );
  await userEvent.click(screen.getByText("reduce"));
  expect(screen.getByTestId("mpref").textContent).toBe("reduced");
  expect(screen.getByTestId("mreduced").textContent).toBe("true");
  expect(document.documentElement.hasAttribute("data-reduce-motion")).toBe(true);
  expect(localStorage.getItem("jarvis.motion")).toBe("reduced");
  await userEvent.click(screen.getByText("system"));
  expect(document.documentElement.hasAttribute("data-reduce-motion")).toBe(false);
});

test("restores a persisted reduced-motion preference on mount", () => {
  localStorage.setItem("jarvis.motion", "reduced");
  render(
    <ThemeProvider>
      <MotionProbe />
    </ThemeProvider>,
  );
  expect(screen.getByTestId("mpref").textContent).toBe("reduced");
  expect(document.documentElement.hasAttribute("data-reduce-motion")).toBe(true);
});
