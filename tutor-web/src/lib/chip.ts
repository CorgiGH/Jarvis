/**
 * Mockup-gap bridge step 3 — quick-action chips.
 *
 * The LLM may emit one or more chip envelopes per reply. Each chip
 * surfaces below the assistant message as a pill button; click fills
 * the chat input with `prompt` and focuses it (user reviews + sends).
 *
 * Envelope syntax:
 *   <chip>{"label":"Why?","prompt":"Why does this work?"}</chip>
 *
 * Both fields required. Length caps stop the LLM from stuffing entire
 * paragraphs into a chip (defeats the point — chips are quick prompts,
 * not essays). Malformed or oversize envelopes are silently dropped so
 * a buggy emission doesn't break the surrounding chat render.
 */

export interface QuickChip {
  /** Pill button text. ≤ 40 chars after trim. */
  label: string;
  /** Text inserted into the chat input on click. ≤ 200 chars after trim. */
  prompt: string;
}

export interface ParsedChipReply {
  /** Reply text with all `<chip>...</chip>` envelopes removed. */
  body: string;
  chips: QuickChip[];
}

const ENVELOPE = /<chip>\s*([\s\S]*?)\s*<\/chip>/g;
const LABEL_MAX = 40;
const PROMPT_MAX = 200;

export function parseChips(reply: string): ParsedChipReply {
  const chips: QuickChip[] = [];
  let m: RegExpExecArray | null;
  ENVELOPE.lastIndex = 0;
  while ((m = ENVELOPE.exec(reply)) !== null) {
    try {
      const parsed = JSON.parse(m[1]) as Partial<QuickChip>;
      const label = (parsed.label ?? "").trim();
      const prompt = (parsed.prompt ?? "").trim();
      if (
        label.length > 0 && label.length <= LABEL_MAX &&
        prompt.length > 0 && prompt.length <= PROMPT_MAX
      ) {
        chips.push({ label, prompt });
      }
    } catch {
      continue;
    }
  }
  const body = reply.replace(ENVELOPE, "").replace(/\n{3,}/g, "\n\n").trim();
  return { body, chips };
}
