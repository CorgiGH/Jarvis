import { test, expect, beforeEach } from "vitest";
import { applyThemeToRoot, loadTheme, saveTheme } from "../theme/applyTheme";

beforeEach(() => {
  document.documentElement.removeAttribute("style");
  localStorage.clear();
});

test("applyThemeToRoot writes the palette accent onto :root", () => {
  applyThemeToRoot({ paletteId: "magenta-lime" });
  // magenta-lime primary is #FF5DA8 (from palettes.ts)
  expect(
    document.documentElement.style.getPropertyValue("--color-accent").toUpperCase(),
  ).toBe("#FF5DA8");
  expect(
    document.documentElement.style.getPropertyValue("--color-panel-dark-fg").toUpperCase(),
  ).toBe("#FF5DA8");
});

test("customPrimary overrides the palette accent", () => {
  applyThemeToRoot({ paletteId: "brand-yellow", customPrimary: "#123456" });
  expect(document.documentElement.style.getPropertyValue("--color-accent")).toBe("#123456");
});

test("save then load round-trips the choice", () => {
  saveTheme({ paletteId: "forest-gold", customPrimary: "#abcdef" });
  expect(loadTheme()).toEqual({ paletteId: "forest-gold", customPrimary: "#abcdef" });
});

test("loadTheme returns the brand-yellow default when nothing is stored", () => {
  expect(loadTheme()).toEqual({ paletteId: "brand-yellow" });
});

test("loadTheme tolerates corrupt storage", () => {
  localStorage.setItem("jarvis.theme", "{not json");
  expect(loadTheme()).toEqual({ paletteId: "brand-yellow" });
});
