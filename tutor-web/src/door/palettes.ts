import type { CSSProperties } from "react";

// Palette set for the door theme playground. Warm families are lifted verbatim
// from the doors-lab B3 color variants Alex already saw. Each palette also
// carries a `primary`/`secondary` pair used for swatches and for the brutalist
// accent swap (brutalist = one accent on black, so it only takes `primary`).

export interface WarmFamily {
  bg: string;
  ink: string;
  inkSoft: string;
  inkFaint: string;
  coral: string;
  coralDeep: string;
  teal: string;
  tealText: string;
  tealTint: string;
  coralTint: string;
  card: string;
  line: string;
}

export interface Palette {
  id: string;
  label: string;
  primary: string; // swatch main + brutalist accent
  secondary: string; // swatch dot + warm secondary
  warm: WarmFamily;
}

export const PALETTES: Palette[] = [
  {
    id: "coral-teal",
    label: "Coral + Teal",
    primary: "#FF7A5C",
    secondary: "#5BD7C4",
    warm: { bg: "#1C1714", ink: "#F4EDE4", inkSoft: "#B7A998", inkFaint: "#9D927E", coral: "#FF7A5C", coralDeep: "#FF9576", teal: "#5BD7C4", tealText: "#74E0CF", tealTint: "#143029", coralTint: "#3A231C", card: "#251E19", line: "#473C33" },
  },
  {
    id: "indigo-mint",
    label: "Indigo + Mint",
    primary: "#8B7CFF",
    secondary: "#5BE0B0",
    warm: { bg: "#15161F", ink: "#ECEEF6", inkSoft: "#9CA0B8", inkFaint: "#8488A0", coral: "#8B7CFF", coralDeep: "#A99CFF", teal: "#5BE0B0", tealText: "#76E9C2", tealTint: "#0F2C26", coralTint: "#211C3A", card: "#1D1E2A", line: "#34364A" },
  },
  {
    id: "amber-sky",
    label: "Amber + Sky",
    primary: "#F5B544",
    secondary: "#5AB8E8",
    warm: { bg: "#1A1712", ink: "#F5EFE3", inkSoft: "#BBAF98", inkFaint: "#9A8E78", coral: "#F5B544", coralDeep: "#FFC95E", teal: "#5AB8E8", tealText: "#84CCEF", tealTint: "#0F2A38", coralTint: "#352812", card: "#241F16", line: "#473F2E" },
  },
  {
    id: "magenta-lime",
    label: "Magenta + Lime",
    primary: "#FF5DA8",
    secondary: "#B6F05A",
    warm: { bg: "#161018", ink: "#F3E9F0", inkSoft: "#B79FB2", inkFaint: "#957F90", coral: "#FF5DA8", coralDeep: "#FF86C0", teal: "#B6F05A", tealText: "#C8F57F", tealTint: "#222F0F", coralTint: "#33172A", card: "#1F1722", line: "#41324A" },
  },
  {
    id: "forest-gold",
    label: "Forest + Gold",
    primary: "#EBC15A",
    secondary: "#5FBF8A",
    warm: { bg: "#12180F", ink: "#ECF1E6", inkSoft: "#A2B398", inkFaint: "#869479", coral: "#EBC15A", coralDeep: "#FFD46E", teal: "#5FBF8A", tealText: "#84CFA4", tealTint: "#10301F", coralTint: "#33290F", card: "#1A2113", line: "#3C4733" },
  },
  {
    id: "brand-yellow",
    label: "Brand Yellow",
    primary: "#FDE047",
    secondary: "#5BD7C4",
    warm: { bg: "#1A1712", ink: "#F6F0DF", inkSoft: "#BCB08F", inkFaint: "#9A8E70", coral: "#FDE047", coralDeep: "#FCE56B", teal: "#5BD7C4", tealText: "#74E0CF", tealTint: "#143029", coralTint: "#34300F", card: "#231F14", line: "#46402C" },
  },
];

export interface ThemeChoice {
  paletteId: string;
  customPrimary?: string; // overrides primary when set (the color picker)
  customSecondary?: string;
}

export function getPalette(id: string): Palette {
  return PALETTES.find((p) => p.id === id) ?? PALETTES[0];
}

export function effectivePrimary(c: ThemeChoice): string {
  return c.customPrimary ?? getPalette(c.paletteId).primary;
}
export function effectiveSecondary(c: ThemeChoice): string {
  return c.customSecondary ?? getPalette(c.paletteId).secondary;
}

// CSS var overrides for the BRUTALIST door root (one accent on black).
export function brutalistVars(c: ThemeChoice): CSSProperties {
  const accent = effectivePrimary(c);
  return {
    ["--color-accent" as string]: accent,
    ["--color-accent-hover" as string]: accent,
    ["--color-panel-dark-fg" as string]: accent,
  } as CSSProperties;
}

// CSS var overrides for the WARM door root (full coral/teal family).
export function warmVars(c: ThemeChoice): CSSProperties {
  const p = getPalette(c.paletteId).warm;
  const coral = c.customPrimary ?? p.coral;
  const teal = c.customSecondary ?? p.teal;
  return {
    ["--bg" as string]: p.bg,
    ["--ink" as string]: p.ink,
    ["--ink-soft" as string]: p.inkSoft,
    ["--ink-faint" as string]: p.inkFaint,
    ["--coral" as string]: coral,
    ["--coral-deep" as string]: c.customPrimary ?? p.coralDeep,
    ["--teal" as string]: teal,
    ["--teal-text" as string]: c.customSecondary ?? p.tealText,
    ["--teal-tint" as string]: p.tealTint,
    ["--coral-tint" as string]: p.coralTint,
    ["--card" as string]: p.card,
    ["--line" as string]: p.line,
  } as CSSProperties;
}
