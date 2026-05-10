/**
 * Tutor Layer B0 — knowledge-gap inline cards.
 *
 * The LLM emits typed JSON envelopes alongside <edit> blocks when it
 * detects (or is asked to detect) a knowledge gap during chat:
 *
 *   <gap>{"id":"g1","topic":"...","language":"kotlin","type":"CONCEPT",
 *         "trigger":"EXPLICIT_ASK","content":"...","exampleCode":"..."}</gap>
 *
 * The frontend extracts these into KnowledgeGap entries, persists each
 * via POST /api/v1/gap, and renders an inline card with INSERT →
 * SCRATCHPAD + MARK RESOLVED + FLAG WRONG actions.
 *
 * Council R3 / spec §4 item 6: INSERT → SCRATCHPAD goes to the user's
 * scratchpad slot, NOT the user's editor — preserves the generation
 * effect (user has to re-type to use it, deepening recall).
 */

export type GapType = "COMMAND" | "CONCEPT" | "SYNTAX" | "LIBRARY" | "THEOREM";
export type GapTrigger = "EXPLICIT_ASK" | "SYNTAX_ERROR" | "REPEATED_FAILURE" | "MANUAL_FLAG";
export type GapResolved = "USER_TYPED" | "USER_DISMISSED" | "USER_MARKED_DONE";

export interface KnowledgeGap {
  id: string;
  topic: string;
  language?: string;
  type: GapType;
  trigger: GapTrigger;
  content: string;
  exampleCode?: string;
  sourceCitation?: string;
  resolvedBy?: GapResolved;
  /** Phase 7.5: set after the user promotes the gap to FSRS. UI uses
   *  this to suppress the promote button on subsequent renders. */
  fsrsCardId?: string | null;
}

export interface ParsedGapReply {
  /** Reply with all <gap>...</gap> envelopes removed. */
  body: string;
  gaps: KnowledgeGap[];
}

const GAP_TYPES: Set<string> = new Set(["COMMAND", "CONCEPT", "SYNTAX", "LIBRARY", "THEOREM"]);
const GAP_TRIGGERS: Set<string> = new Set([
  "EXPLICIT_ASK", "SYNTAX_ERROR", "REPEATED_FAILURE", "MANUAL_FLAG",
]);

const ENVELOPE = /<gap>\s*([\s\S]*?)\s*<\/gap>/g;

export function parseKnowledgeGaps(reply: string): ParsedGapReply {
  const gaps: KnowledgeGap[] = [];
  let m: RegExpExecArray | null;
  ENVELOPE.lastIndex = 0;
  while ((m = ENVELOPE.exec(reply)) !== null) {
    try {
      const parsed = JSON.parse(m[1]) as Partial<KnowledgeGap>;
      if (
        typeof parsed.id === "string" && parsed.id.length > 0 &&
        typeof parsed.topic === "string" && parsed.topic.length > 0 &&
        typeof parsed.content === "string" &&
        typeof parsed.type === "string" && GAP_TYPES.has(parsed.type) &&
        typeof parsed.trigger === "string" && GAP_TRIGGERS.has(parsed.trigger)
      ) {
        gaps.push({
          id: parsed.id,
          topic: parsed.topic,
          language: parsed.language,
          type: parsed.type as GapType,
          trigger: parsed.trigger as GapTrigger,
          content: parsed.content,
          exampleCode: parsed.exampleCode,
          sourceCitation: parsed.sourceCitation,
        });
      }
    } catch {
      continue;
    }
  }
  const body = reply.replace(ENVELOPE, "").replace(/\n{3,}/g, "\n\n").trim();
  return { body, gaps };
}
