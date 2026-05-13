/** Shape of the envelope sent to POST /api/v1/sidekick/ask.
 *  Field names use snake_case to match the backend SidekickEnvelope. */
export interface SidekickEnvelope {
  task_id?: string;
  problem_id?: string;
  card_id?: string;
  card_title?: string;
  anchor_id?: string;
  anchor_text?: string;
  selection?: string;
  user_question: string;
  /** Active drill statement, used server-side by SelectionQueryBuilder to
   *  detect drill-self-paste (Jaccard >= 0.7) and short-circuit the LLM call. */
  drill_statement?: string;
}

export interface BuildEnvelopeArgs {
  taskId?: string;
  problemId?: string;
  cardId?: string;
  cardTitle?: string;
  anchorId?: string;
  anchorText?: string;
  selection?: string;
  userQuestion: string;
  drillStatement?: string;
}

/** Build a SidekickEnvelope from camelCase args, dropping undefined fields. */
export function buildSidekickEnvelope(args: BuildEnvelopeArgs): SidekickEnvelope {
  const env: SidekickEnvelope = { user_question: args.userQuestion };
  if (args.taskId !== undefined) env.task_id = args.taskId;
  if (args.problemId !== undefined) env.problem_id = args.problemId;
  if (args.cardId !== undefined) env.card_id = args.cardId;
  if (args.cardTitle !== undefined) env.card_title = args.cardTitle;
  if (args.anchorId !== undefined) env.anchor_id = args.anchorId;
  if (args.anchorText !== undefined) env.anchor_text = args.anchorText;
  if (args.selection !== undefined) env.selection = args.selection;
  if (args.drillStatement !== undefined) env.drill_statement = args.drillStatement;
  return env;
}

/** Walk up the DOM from `node` to check if it's inside a `.card-body`. */
function insideCardBody(node: Node): boolean {
  let cur: Node | null = node;
  while (cur) {
    if (cur instanceof Element && cur.classList.contains("card-body")) return true;
    cur = cur.parentNode;
  }
  return false;
}

/**
 * True when the selection sits inside an ACTIVE DRILL card body. The chip must
 * not fire here — DRILL is the closed-book test surface, and the existing
 * "Type your answer + CHECK ANSWER" textarea below is the legitimate predict-
 * then-reveal primitive. A chip on this surface would let the LLM solve the
 * drill for the user, inverting the testing-effect rule (memory-pinned).
 *
 * Returns true ONLY when the nearest `[data-testid="drill-card"]` ancestor
 * has BOTH `data-card-type="DRILL"` AND `data-state="open"`. Locked + complete
 * DRILL cards still allow the chip (locked has no body content; complete is
 * post-answer where clarification IS useful).
 */
export function insideActiveDrillCard(node: Node): boolean {
  let cur: Node | null = node;
  while (cur) {
    if (cur instanceof Element && cur.getAttribute("data-testid") === "drill-card") {
      return (
        cur.getAttribute("data-card-type") === "DRILL" &&
        cur.getAttribute("data-state") === "open"
      );
    }
    cur = cur.parentNode;
  }
  return false;
}

/** Attach a `mouseup` listener on `root` that fires `onAsk(selectedText, rect)`
 *  when the user finishes a selection of ≥ 3 chars inside a `.card-body` element.
 *  Returns a detach function — call it in the component's cleanup effect. */
export function attachSelectionListener(
  root: HTMLElement,
  onAsk: (selectedText: string, rect: DOMRect) => void,
): () => void {
  function handleMouseUp() {
    const sel = window.getSelection();
    if (!sel || sel.rangeCount === 0) return;
    const range = sel.getRangeAt(0);
    const text = range.toString();
    if (text.length < 3) return;
    if (!insideCardBody(range.commonAncestorContainer)) return;
    // Drill-surface scoping rule (council 2026-05-13 mitigation A):
    // Suppress the chip when the selection sits inside an ACTIVE DRILL card.
    // DRILL is the closed-book test; the existing CHECK ANSWER textarea +
    // grader is the legitimate predict-then-reveal primitive there.
    if (insideActiveDrillCard(range.commonAncestorContainer)) return;
    const rect = range.getBoundingClientRect();
    onAsk(text, rect);
  }

  root.addEventListener("mouseup", handleMouseUp);
  return () => root.removeEventListener("mouseup", handleMouseUp);
}
