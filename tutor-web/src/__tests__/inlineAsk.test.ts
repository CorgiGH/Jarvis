import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import {
  buildSidekickEnvelope,
  attachSelectionListener,
} from "../lib/inlineAsk";

// ─── buildSidekickEnvelope ────────────────────────────────────────────────────

describe("buildSidekickEnvelope", () => {
  test("maps all args to SidekickEnvelope fields", () => {
    const env = buildSidekickEnvelope({
      taskId: "task-01",
      problemId: "A3",
      cardId: "card-1",
      cardTitle: "③ DRILL · YOUR TURN",
      anchorId: "drill-statement",
      anchorText: "Sample x = (3,7,8,9,14). What is μ̂?",
      selection: "MLE",
      userQuestion: "what does MLE mean?",
    });
    expect(env.task_id).toBe("task-01");
    expect(env.problem_id).toBe("A3");
    expect(env.card_id).toBe("card-1");
    expect(env.card_title).toBe("③ DRILL · YOUR TURN");
    expect(env.anchor_id).toBe("drill-statement");
    expect(env.anchor_text).toBe("Sample x = (3,7,8,9,14). What is μ̂?");
    expect(env.selection).toBe("MLE");
    expect(env.user_question).toBe("what does MLE mean?");
  });

  test("omits undefined optional fields", () => {
    const env = buildSidekickEnvelope({
      taskId: "task-02",
      userQuestion: "explain this",
    });
    expect(env.task_id).toBe("task-02");
    expect(env.problem_id).toBeUndefined();
    expect(env.selection).toBeUndefined();
    expect(env.user_question).toBe("explain this");
  });

  test("plumbs drillStatement -> drill_statement (snake_case wire)", () => {
    const drill = "Scrie codul R pentru a simula 10000 observatii din distributia Laplace.";
    const env = buildSidekickEnvelope({
      taskId: "task-03",
      selection: "Laplace",
      userQuestion: "Laplace",
      drillStatement: drill,
    });
    expect(env.drill_statement).toBe(drill);
  });

  test("omits drill_statement when drillStatement undefined", () => {
    const env = buildSidekickEnvelope({
      taskId: "task-04",
      userQuestion: "explain",
    });
    expect(env.drill_statement).toBeUndefined();
  });
});

// ─── attachSelectionListener ──────────────────────────────────────────────────

describe("attachSelectionListener", () => {
  let root: HTMLElement;
  let cardBody: HTMLElement;
  let onAsk: ReturnType<typeof vi.fn>;
  let detach: () => void;

  beforeEach(() => {
    root = document.createElement("div");
    cardBody = document.createElement("div");
    cardBody.className = "card-body";
    cardBody.textContent = "Sample x = (3,7,8,9,14). What is MLE?";
    root.appendChild(cardBody);
    document.body.appendChild(root);
    onAsk = vi.fn();
    detach = attachSelectionListener(root, onAsk);
  });

  afterEach(() => {
    detach();
    document.body.removeChild(root);
    vi.restoreAllMocks();
  });

  test("calls onAsk with rect when selection ≥ 3 chars inside .card-body", () => {
    const mockRange = {
      toString: () => "MLE",
      getBoundingClientRect: () => ({
        top: 100, left: 50, bottom: 116, right: 80, width: 30, height: 16,
      } as DOMRect),
      commonAncestorContainer: cardBody.firstChild as Node,
    } as unknown as Range;

    vi.spyOn(window, "getSelection").mockReturnValue({
      rangeCount: 1,
      getRangeAt: () => mockRange,
      toString: () => "MLE",
    } as unknown as Selection);

    root.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));

    expect(onAsk).toHaveBeenCalledOnce();
    const [selectedText, rect] = onAsk.mock.calls[0];
    expect(selectedText).toBe("MLE");
    expect(rect.top).toBe(100);
  });

  test("does not call onAsk when selection is fewer than 3 chars", () => {
    vi.spyOn(window, "getSelection").mockReturnValue({
      rangeCount: 1,
      getRangeAt: () => ({
        toString: () => "ab",
        getBoundingClientRect: () => ({ top: 0, left: 0, bottom: 0, right: 0, width: 0, height: 0 } as DOMRect),
        commonAncestorContainer: cardBody.firstChild as Node,
      } as unknown as Range),
      toString: () => "ab",
    } as unknown as Selection);

    root.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));
    expect(onAsk).not.toHaveBeenCalled();
  });

  test("does not call onAsk when selection is outside .card-body", () => {
    const outside = document.createElement("p");
    outside.textContent = "outside paragraph";
    root.appendChild(outside);

    vi.spyOn(window, "getSelection").mockReturnValue({
      rangeCount: 1,
      getRangeAt: () => ({
        toString: () => "outside paragraph",
        getBoundingClientRect: () => ({ top: 0, left: 0, bottom: 0, right: 0, width: 0, height: 0 } as DOMRect),
        commonAncestorContainer: outside.firstChild as Node,
      } as unknown as Range),
      toString: () => "outside paragraph",
    } as unknown as Selection);

    root.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));
    expect(onAsk).not.toHaveBeenCalled();
  });

  test("does NOT call onAsk when selection sits inside an active DRILL card", () => {
    // Wrap the existing card-body in a drill-card article with the active-DRILL attrs.
    const article = document.createElement("article");
    article.setAttribute("data-testid", "drill-card");
    article.setAttribute("data-card-type", "DRILL");
    article.setAttribute("data-state", "open");
    cardBody.parentNode?.insertBefore(article, cardBody);
    article.appendChild(cardBody);

    vi.spyOn(window, "getSelection").mockReturnValue({
      rangeCount: 1,
      getRangeAt: () => ({
        toString: () => "MLE",
        getBoundingClientRect: () => ({ top: 0, left: 0, bottom: 0, right: 0, width: 0, height: 0 } as DOMRect),
        commonAncestorContainer: cardBody.firstChild as Node,
      } as unknown as Range),
      toString: () => "MLE",
    } as unknown as Selection);

    root.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));
    expect(onAsk).not.toHaveBeenCalled();
  });

  test("DOES call onAsk when selection sits inside a WORKED (non-DRILL) card", () => {
    const article = document.createElement("article");
    article.setAttribute("data-testid", "drill-card");
    article.setAttribute("data-card-type", "WORKED");
    article.setAttribute("data-state", "open");
    cardBody.parentNode?.insertBefore(article, cardBody);
    article.appendChild(cardBody);

    vi.spyOn(window, "getSelection").mockReturnValue({
      rangeCount: 1,
      getRangeAt: () => ({
        toString: () => "MLE",
        getBoundingClientRect: () => ({ top: 0, left: 0, bottom: 0, right: 0, width: 0, height: 0 } as DOMRect),
        commonAncestorContainer: cardBody.firstChild as Node,
      } as unknown as Range),
      toString: () => "MLE",
    } as unknown as Selection);

    root.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));
    expect(onAsk).toHaveBeenCalledOnce();
  });

  test("DOES call onAsk when DRILL card is complete (not actively-open)", () => {
    const article = document.createElement("article");
    article.setAttribute("data-testid", "drill-card");
    article.setAttribute("data-card-type", "DRILL");
    article.setAttribute("data-state", "complete");
    cardBody.parentNode?.insertBefore(article, cardBody);
    article.appendChild(cardBody);

    vi.spyOn(window, "getSelection").mockReturnValue({
      rangeCount: 1,
      getRangeAt: () => ({
        toString: () => "MLE",
        getBoundingClientRect: () => ({ top: 0, left: 0, bottom: 0, right: 0, width: 0, height: 0 } as DOMRect),
        commonAncestorContainer: cardBody.firstChild as Node,
      } as unknown as Range),
      toString: () => "MLE",
    } as unknown as Selection);

    root.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));
    expect(onAsk).toHaveBeenCalledOnce();
  });

  test("detach removes the mouseup listener", () => {
    detach();
    vi.spyOn(window, "getSelection").mockReturnValue({
      rangeCount: 1,
      getRangeAt: () => ({
        toString: () => "MLE here",
        getBoundingClientRect: () => ({ top: 10, left: 10, bottom: 26, right: 40, width: 30, height: 16 } as DOMRect),
        commonAncestorContainer: cardBody.firstChild as Node,
      } as unknown as Range),
      toString: () => "MLE here",
    } as unknown as Selection);

    root.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));
    expect(onAsk).not.toHaveBeenCalled();
  });
});
