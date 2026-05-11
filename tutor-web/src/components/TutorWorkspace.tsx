import { useEffect, useRef, useState } from "react";
import { StatusBar } from "./StatusBar";
import { InlineAskChip } from "./InlineAskChip";
import { Sidekick } from "./Sidekick";
import { DaemonHealthPill } from "./DaemonHealthPill";
import { ProblemStepper, parseProblemParam } from "./ProblemStepper";
import { ProgressStrip } from "./ProgressStrip";
import { DrillStack } from "./DrillStack";
import type { DrillContent } from "./DrillStack";
import { CompileSubmitCard } from "./CompileSubmitCard";
import { ResourceRail } from "./ResourceRail";
import { attachSelectionListener, buildSidekickEnvelope } from "../lib/inlineAsk";
import type { SidekickEnvelope } from "../lib/inlineAsk";
import { getTaskPrep, triggerReprep } from "../lib/taskPrep";
import type { TaskPrepReply, RailItem } from "../lib/taskPrep";
import { useSearchParams } from "react-router-dom";

interface Problem {
  problemId: string;  // camelCase matches the JSON produced by the Kotlin serializer
  page: number;
  statement: string;
  equationRefs?: string[];
  dataGivens?: string[];
}

const POLL_INTERVAL_MS = 2000;

export function TutorWorkspace({ pdfUrl: _pdfUrl, taskId, dedupedNotice = false }:
  { pdfUrl: string; taskId: string; dedupedNotice?: boolean }) {

  const workspaceRef = useRef<HTMLDivElement>(null);
  const [chipState, setChipState] = useState<{ rect: DOMRect; envelope: SidekickEnvelope } | null>(null);
  const [sidekickEnvelope, setSidekickEnvelope] = useState<SidekickEnvelope | undefined>(undefined);

  const [prep, setPrep] = useState<TaskPrepReply | null>(null);
  const [prepError, setPrepError] = useState<string | null>(null);
  const [searchParams] = useSearchParams();

  // ── Bootstrap: fetch prep, trigger reprep on miss, poll until present ──
  useEffect(() => {
    let cancelled = false;
    let pollHandle: number | null = null;

    async function bootstrap() {
      try {
        const result = await getTaskPrep(taskId);
        if (cancelled) return;
        if (result) {
          setPrep(result);
          return;
        }
        // Miss — trigger reprep, poll until present
        try { await triggerReprep(taskId); } catch (_) { /* tolerate */ }
        pollHandle = window.setInterval(async () => {
          try {
            const r = await getTaskPrep(taskId);
            if (cancelled) return;
            if (r) {
              setPrep(r);
              if (pollHandle != null) {
                window.clearInterval(pollHandle);
                pollHandle = null;
              }
            }
          } catch (e) {
            // tolerate poll errors; keep retrying
          }
        }, POLL_INTERVAL_MS);
      } catch (e) {
        if (!cancelled) setPrepError(e instanceof Error ? e.message : String(e));
      }
    }
    bootstrap();
    return () => {
      cancelled = true;
      if (pollHandle != null) window.clearInterval(pollHandle);
    };
  }, [taskId]);

  // ── Inline help: selection chip → sidekick (kept from Slice 1 E3) ──
  useEffect(() => {
    const root = workspaceRef.current;
    if (!root) return;
    const detach = attachSelectionListener(root, (selectedText, rect) => {
      const env = buildSidekickEnvelope({ taskId, selection: selectedText, userQuestion: selectedText });
      setChipState({ rect, envelope: env });
    });
    function handlePointerDown(e: PointerEvent) {
      const target = e.target as HTMLElement;
      if (!target.closest(".ask-chip-fade-in")) setChipState(null);
    }
    document.addEventListener("pointerdown", handlePointerDown);
    return () => {
      detach();
      document.removeEventListener("pointerdown", handlePointerDown);
    };
  }, [taskId]);

  // ── Parse prep payload ──
  const problems: Problem[] = prep
    ? (() => { try { return JSON.parse(prep.problemsJson); } catch { return []; } })()
    : [];
  const drillsByProblem: Record<string, DrillContent> = prep
    ? (() => { try { return JSON.parse(prep.drillsJson); } catch { return {}; } })()
    : {};
  const railItems: RailItem[] = prep
    ? (() => { try { return JSON.parse(prep.railJson); } catch { return []; } })()
    : [];

  const activeIndex = parseProblemParam(searchParams.get("problem"));
  const activeProblem = problems[activeIndex] ?? problems[0];

  const [completedProblems, setCompletedProblems] = useState<Set<string>>(new Set());
  function handleProblemComplete(problemId: string) {
    setCompletedProblems(prev => new Set(prev).add(problemId));
  }

  // CitationPill → ResourceRail drawer bridge.
  // Set by Sidekick.onCitationClick; cleared when the drawer is closed.
  const [pendingDrawerPath, setPendingDrawerPath] = useState<string | null>(null);

  const allDone = problems.length > 0 && completedProblems.size >= problems.length;
  const debugMode = searchParams.get("debug") === "1";

  // ── Skeleton when prep is loading ──
  if (!prep && !prepError) {
    return (
      <div ref={workspaceRef} className="flex flex-col h-full bg-page-bg text-page-fg">
        <header data-testid="tutor-header"
                className="flex items-center justify-between px-4 py-1 border-b-4 border-border-strong bg-panel-dark-bg text-panel-dark-fg text-[10px] font-mono tracking-widest">
          <span className="font-bold">JARVIS · TUTOR</span>
          {debugMode && <DaemonHealthPill />}
        </header>
        <div data-testid="workspace-skeleton" aria-busy="true"
             className="flex-1 flex flex-col items-center justify-center gap-4 p-12 font-mono text-page-fg/60 tracking-widest">
          <p>preparing drill stack…</p>
          <p className="text-xs">LLM extracting problems from your PDF · poll every 2s</p>
        </div>
      </div>
    );
  }

  if (prepError) {
    return (
      <div ref={workspaceRef} className="flex flex-col h-full bg-page-bg text-page-fg p-12 font-mono">
        <p className="text-danger-text tracking-widest" role="alert">
          (couldn't load task prep — {prepError})
        </p>
      </div>
    );
  }

  return (
    <div ref={workspaceRef} className="flex flex-col h-full bg-page-bg text-page-fg">
      <header data-testid="tutor-header"
              className="flex items-center justify-between px-4 py-1 border-b-4 border-border-strong bg-panel-dark-bg text-panel-dark-fg text-[10px] font-mono tracking-widest">
        <span className="font-bold">JARVIS · TUTOR · {taskId}</span>
        {debugMode && <DaemonHealthPill />}
      </header>

      {dedupedNotice && (
        <div data-testid="deduped-notice"
             role="status"
             aria-live="polite"
             className="bg-accent border-b-4 border-border-strong text-page-fg font-mono text-xs font-bold tracking-widest px-4 py-1.5">
          OPENED EXISTING TASK · same subject + title already on file
        </div>
      )}

      <ProblemStepper
        problems={problems.map(p => ({ problemId: p.problemId, label: p.problemId ?? "—" }))}
        activeProblemIndex={activeIndex}
      />

      <ProgressStrip
        outer={{ done: completedProblems.size, total: problems.length }}
        inner={{
          done: 0,  // Slice 1.5: drill-stack-internal phase doesn't lift up yet
          total: 4, // DRILL + WORKED + DEFINITION + CHECK
        }}
        currentProblemLabel={activeProblem?.problemId ?? "—"}
      />

      <div className="flex-1 min-h-0 flex">
        <main className="flex-1 min-w-0 flex flex-col overflow-y-auto p-4 gap-4">
          {activeProblem && drillsByProblem[activeProblem.problemId] && (
            <DrillStack
              key={activeProblem.problemId}
              taskId={taskId}
              problemId={activeProblem.problemId}
              content={drillsByProblem[activeProblem.problemId]}
              onProblemComplete={handleProblemComplete}
            />
          )}

          <Sidekick
            envelope={sidekickEnvelope}
            onCitationClick={(c) => setPendingDrawerPath(c.path)}
          />

          {allDone && (
            <CompileSubmitCard
              taskId={taskId}
              answers={problems.map(p => ({
                problemId: p.problemId,
                attempt: drillsByProblem[p.problemId]?.drill ?? "",
              }))}
              onSubmitted={() => { /* no-op for Slice 1.5; tasks page reflects status */ }}
            />
          )}
        </main>

        <ResourceRail
          taskId={taskId}
          items={railItems}
          forceOpenPath={pendingDrawerPath}
          onDrawerClosed={() => setPendingDrawerPath(null)}
        />
      </div>

      <StatusBar />

      {chipState && (
        <InlineAskChip
          selectionRect={chipState.rect}
          envelope={chipState.envelope}
          onAsk={(env) => { setSidekickEnvelope(env); setChipState(null); }}
        />
      )}
    </div>
  );
}
