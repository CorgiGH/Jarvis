export interface PlotlyBlock { json: any; raw: string; }

const FENCE = /```plotly\s*\n([\s\S]*?)\n```/g;

/**
 * Phase 8.1: parses ```plotly fenced JSON blocks out of an assistant
 * reply. Returns the body with each block replaced by sentinels
 * (PLOTLY0, PLOTLY1, ...) plus the parsed Plotly.js Figure JSON
 * objects. Invalid JSON in a block is silently skipped (raw text
 * stays in body — user/LLM can see what was rejected).
 */
export function parsePlotly(text: string): { body: string; plots: PlotlyBlock[] } {
  const plots: PlotlyBlock[] = [];
  let i = 0;
  const body = text.replace(FENCE, (raw, jsonText: string) => {
    try {
      const j = JSON.parse(jsonText);
      const idx = i++;
      plots.push({ json: j, raw });
      return `PLOTLY${idx}`;
    } catch (_) {
      return raw;
    }
  });
  return { body, plots };
}
