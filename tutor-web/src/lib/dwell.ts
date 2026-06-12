/** min(5500, max(1400, round(900 + words*320))) ms — a FLOOR, never a ceiling (spec §4.1). */
export function readMs(text: string): number {
  const w = text.trim().split(/\s+/).filter(Boolean).length;
  return Math.min(5500, Math.max(1400, Math.round(900 + w * 320)));
}
