// Brutalist mono theme tokens — used by all V1-V20 viz primitives.
// Source of truth for ink + paper + accent.
// Any viz importing from elsewhere is non-compliant per spec §6.4.

export const INK = "#0a0a0a";
export const PAPER = "#f5f5f0";
export const ACCENT = "#facc15";

export const STROKE_DEFAULT = 1;
export const STROKE_FOCUS = 2;
export const STROKE_THICK = 4;

export const FONT_FAMILY = '"JetBrains Mono", ui-monospace, Consolas, monospace';

export const FONT_SIZE_TINY = 9;
export const FONT_SIZE_LABEL = 11;
export const FONT_SIZE_BODY = 13;
export const FONT_SIZE_VALUE = 18;

// Hatch fills — V6 "hatching density for magnitude/category" (NOT opacity).
// Reference via fill={HATCH_LIGHT}; the consuming SVG must render <HatchDefs/>.
export const HATCH_LIGHT = "url(#hatch-light)";
export const HATCH_DENSE = "url(#hatch-dense)";
