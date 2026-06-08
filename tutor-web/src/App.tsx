import { useEffect, useRef, useState } from "react";
import { useSearchParams, Link, useNavigate, useLocation } from "react-router-dom";
import { TutorWorkspace } from "./components/TutorWorkspace";
import { TaskQuickStart } from "./components/TaskQuickStart";
import { ActiveTaskDashboard } from "./components/ActiveTaskDashboard";
import { FsrsReview } from "./components/FsrsReview";
import { TasksScreen } from "./components/TasksScreen";
import { TrustSettings } from "./components/TrustSettings";
import { SettingsMe } from "./components/SettingsMe";
import { DaemonHealthPill } from "./components/DaemonHealthPill";
import { KnowledgeLedger } from "./components/KnowledgeLedger";
import { LoginPage } from "./components/LoginPage";
import { AiLiteracyGate } from "./components/AiLiteracyGate";
import { AppShell } from "./components/AppShell";
import { jarvisFetch } from "./lib/api";
import { recordTelemetry } from "./lib/telemetry";

const LAST_TASK_KEY = "jarvis.lastTaskId";

// Daily review batch cap. The nav count shows min(dueNow, cap) — surfacing the
// raw FSRS backlog (can be 800+ right after a corpus seed) reads as a cram pile,
// the metacognitive illusion spaced repetition exists to fight. One day's ration.
const REVIEW_DAILY_CAP = 20;

/** Bootstrap a tutor session via /api/v1/tutor/auto-session — sets
 *  jarvis_session + csrf cookies if missing. Idempotent server-side.
 *
 *  S-30 fix: the prior early-exit on `if (csrf) return;` was unsound. The
 *  csrf cookie is non-httpOnly (visible to JS) but jarvis_session is
 *  httpOnly. If the server-side session expired in the DB while the csrf
 *  cookie was still cached client-side, the early-exit skipped /auto-session
 *  and the first /api/v1/last-task GET hit with a dead session → 401.
 *  Always call /auto-session; the server is idempotent (returns OK without
 *  rotating when session+csrf are both valid).
 *
 *  Gate 2: returns the HTTP status so the caller can redirect on 401. */
async function ensureTutorSession(): Promise<number> {
  try {
    const r = await fetch("/api/v1/tutor/auto-session", { credentials: "include" });
    return r.status;
  } catch (_) {
    return 0; // network error — treat as unknown, don't redirect
  }
}

export function App() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const here = useLocation();
  const explicitTaskId = params.get("taskId");
  const pickMode = params.get("pick") === "1";  // explicit "go to QuickStart" flag
  const dedupedFlag = params.get("deduped") === "1";  // surfaced when POST /tasks deduped
  const debug = params.get("debug") === "1";  // ?debug=1 shows DaemonHealthPill + full domain footer
  const [sessionReady, setSessionReady] = useState(false);
  const [gateLang, setGateLang] = useState<"ro" | "en">("ro");
  const [ledgerOpen, setLedgerOpen] = useState(false);

  // Strip ?deduped=1 after a tick so a refresh doesn't re-flash the banner.
  useEffect(() => {
    if (!dedupedFlag) return;
    const t = setTimeout(() => {
      setParams(prev => {
        const next = new URLSearchParams(prev);
        next.delete("deduped");
        return next;
      }, { replace: true });
    }, 3000);
    return () => clearTimeout(t);
  }, [dedupedFlag, setParams]);

  const sessionBootstrapped = useRef(false);
  useEffect(() => {
    if (sessionBootstrapped.current) return;
    sessionBootstrapped.current = true;
    const pathname = here.pathname;
    ensureTutorSession().then(async status => {
      if (
        status === 401 &&
        pathname !== "/login" &&
        pathname !== "/welcome/ai-literacy"
      ) {
        navigate("/login", { replace: true });
        return;
      }
      // 401 → already redirected to /login above. status 0 → network error: skip the
      // literacy gate and proceed. Any other status → established session: check literacy.
      if (status !== 401 && status !== 0) {
        try {
          const r = await jarvisFetch("/api/v1/me/export");
          if (r.ok) {
            const data: {
              aiLiteracyConfirmed: boolean;
              user?: { lang?: string } | null;
            } = await r.json();
            // Derive lang from the user record (falls back to "ro").
            const userLang = data.user?.lang;
            if (userLang === "en") setGateLang("en");
            if (
              !data.aiLiteracyConfirmed &&
              pathname !== "/welcome/ai-literacy"
            ) {
              navigate("/welcome/ai-literacy", { replace: true });
              return;
            }
          }
        } catch (_) {
          // export fetch failed — don't block the user; gate skipped
        }
      }
      setSessionReady(true);
    }).catch(() => setSessionReady(true));
  }, [navigate]);

  // Phase 4.4 cross-device sync: read jarvis_last_task server cookie on
  // cold mount. Server cookie wins over localStorage when both present.
  // Gate on sessionReady: /api/v1/last-task requires jarvis_session cookie.
  // Without the gate, the GET fires before ensureTutorSession() completes
  // and gets a 401 (harmless but noisy — breaks the ?debug=1 gate check).
  const [serverLastTask, setServerLastTask] = useState<string | null>(null);
  const [serverLastTaskLoaded, setServerLastTaskLoaded] = useState(false);
  useEffect(() => {
    if (!sessionReady) return;
    let cancelled = false;
    jarvisFetch("/api/v1/last-task")
      .then(r => r.ok ? r.json() : null)
      .then((d: { taskId?: string } | null) => {
        if (cancelled) return;
        setServerLastTask(d?.taskId ?? null);
        setServerLastTaskLoaded(true);
      })
      .catch(() => { if (!cancelled) setServerLastTaskLoaded(true); });
    return () => { cancelled = true; };
  }, [sessionReady]);

  // Cold-start: restore last-used taskId. Explicit URL params don't gate on
  // /last-task (so direct ?taskId= still persists immediately). When no
  // explicit param is present, server cookie wins, then localStorage.
  const restoredOnce = useRef(false);
  useEffect(() => {
    if (restoredOnce.current) return;
    // Only restore the workspace taskId on the workspace route. On /tasks etc.
    // a setParams() here would graft ?taskId= onto a non-workspace URL.
    if (here.pathname !== "/") return;
    if (explicitTaskId) {
      restoredOnce.current = true;
      try { localStorage.setItem(LAST_TASK_KEY, explicitTaskId); } catch (_) {}
      return;
    }
    if (pickMode) { restoredOnce.current = true; return; }
    if (!serverLastTaskLoaded) return;  // only wait when restoring from absence
    restoredOnce.current = true;
    if (serverLastTask) {
      setParams({ taskId: serverLastTask }, { replace: true });
      return;
    }
    try {
      const last = localStorage.getItem(LAST_TASK_KEY);
      if (last && last !== "TEST-TASK-A") {
        setParams({ taskId: last }, { replace: true });
      }
    } catch (_) {}
  }, [explicitTaskId, pickMode, setParams, serverLastTask, serverLastTaskLoaded, here.pathname]);

  // Persist subsequent explicit taskId changes to localStorage so cold-reload
  // returns here. Not in the cold-start effect — that runs once. This fires
  // when the user navigates between real tasks.
  useEffect(() => {
    if (!restoredOnce.current) return;
    if (explicitTaskId) {
      try { localStorage.setItem(LAST_TASK_KEY, explicitTaskId); } catch (_) {}
    }
  }, [explicitTaskId]);

  // FSRS due count for the nav-link wayfinding indicator. Refetched on route
  // change so the count drops after a review session. Gated on sessionReady —
  // /api/v1/fsrs/forecast needs the jarvis_session cookie.
  const [reviewDue, setReviewDue] = useState(0);
  useEffect(() => {
    if (!sessionReady) return;
    let cancelled = false;
    jarvisFetch("/api/v1/fsrs/forecast")
      .then(r => (r.ok ? r.json() : null))
      .then((d: { dueNow?: number } | null) => {
        if (!cancelled) setReviewDue(d?.dueNow ?? 0);
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [sessionReady, here.pathname]);

  // POST cookie when explicit task id changes so other devices sync.
  // Gate on sessionReady: CSRF token only exists after ensureTutorSession()
  // completes (auto-session sets the csrf cookie). Without this gate the POST
  // fires on first render without the csrf header and gets a 403.
  useEffect(() => {
    if (!sessionReady || !explicitTaskId || explicitTaskId === "TEST-TASK-A") return;
    jarvisFetch("/api/v1/last-task", {
      method: "POST",
      body: JSON.stringify({ taskId: explicitTaskId }),
    }).catch(() => {});
  }, [sessionReady, explicitTaskId]);

  const taskId = explicitTaskId ?? "TEST-TASK-A";
  const isDefault = taskId === "TEST-TASK-A" || pickMode;

  const [taskExists, setTaskExists] = useState<boolean | null>(null);
  useEffect(() => {
    if (isDefault) { setTaskExists(false); return; }
    // Gate on sessionReady: /api/v1/tasks requires jarvis_session cookie which is
    // minted by ensureTutorSession(). Without the gate the request fires before
    // the session cookie lands, gets 401, and permanently shows QuickStart.
    if (!sessionReady) return;
    let cancelled = false;
    jarvisFetch("/api/v1/tasks")
      .then(async r => {
        if (cancelled) return;
        if (!r.ok) { setTaskExists(false); return; }
        const data: { tasks: { id: string }[] } = await r.json();
        setTaskExists(data.tasks.some(t => t.id === taskId));
      })
      .catch(() => { if (!cancelled) setTaskExists(false); });
    return () => { cancelled = true; };
  }, [taskId, isDefault, sessionReady]);

  const showQuickStart = isDefault || taskExists === false;
  // Surface a one-shot banner when /last-task restored a taskId that no longer
  // exists on this device's view — distinguishes from a fresh user landing.
  const missingPinnedTask = !isDefault && taskExists === false;

  function pickAnotherTask() {
    try { localStorage.removeItem(LAST_TASK_KEY); } catch (_) {}
    navigate("/?pick=1", { replace: true });
  }

  return (
    <>
      <AppShell
        nav={
          <nav className="flex items-center gap-4 text-xs font-bold tracking-widest">
            <Link
              to={explicitTaskId && !pickMode ? `/?taskId=${explicitTaskId}` : "/?pick=1"}
              aria-current={here.pathname === "/" ? "page" : undefined}
              className="hover:underline aria-[current=page]:bg-accent aria-[current=page]:text-page-fg aria-[current=page]:px-2 aria-[current=page]:py-0.5"
            >
              workspace
            </Link>
            <Link
              to="/tasks"
              aria-current={here.pathname === "/tasks" ? "page" : undefined}
              className="hover:underline aria-[current=page]:bg-accent aria-[current=page]:text-page-fg aria-[current=page]:px-2 aria-[current=page]:py-0.5"
            >
              tasks
            </Link>
            <Link
              to="/oggi"
              aria-current={here.pathname === "/oggi" ? "page" : undefined}
              className="hover:underline aria-[current=page]:bg-accent aria-[current=page]:text-page-fg aria-[current=page]:px-2 aria-[current=page]:py-0.5"
            >
              azi
            </Link>
            <Link
              to="/review"
              aria-current={here.pathname === "/review" ? "page" : undefined}
              aria-label={reviewDue > 0
                ? `review, ${Math.min(reviewDue, REVIEW_DAILY_CAP)} cards due`
                : "review"}
              className="hover:underline aria-[current=page]:bg-accent aria-[current=page]:text-page-fg aria-[current=page]:px-2 aria-[current=page]:py-0.5"
            >
              review
              {reviewDue > 0 && (
                <span aria-hidden="true" className="opacity-70 font-bold">
                  {" · "}{Math.min(reviewDue, REVIEW_DAILY_CAP)}
                </span>
              )}
            </Link>
            <Link
              to="/settings/trust"
              aria-current={here.pathname === "/settings/trust" ? "page" : undefined}
              className="hover:underline aria-[current=page]:bg-accent aria-[current=page]:text-page-fg aria-[current=page]:px-2 aria-[current=page]:py-0.5"
            >
              trust
            </Link>
            <Link
              to="/me"
              aria-current={here.pathname === "/me" ? "page" : undefined}
              className="hover:underline aria-[current=page]:bg-accent aria-[current=page]:text-page-fg aria-[current=page]:px-2 aria-[current=page]:py-0.5"
            >
              me
            </Link>
            <button
              data-testid="header-ledger-btn"
              onClick={() => {
                // 2026-05-17 hot-work #4: telemetry ping per council 1778988899
                // Devil's Advocate carry-over. If this counter stays at 0 by
                // 2026-05-31, Option B (delete KnowledgeLedger entirely) fires.
                recordTelemetry("ledger.opened");
                setLedgerOpen(true);
              }}
              aria-label="Open knowledge ledger"
              aria-haspopup="dialog"
              aria-expanded={ledgerOpen}
              className="hover:underline"
            >
              ledger
            </button>
            {!showQuickStart && (
              <button
                onClick={pickAnotherTask}
                data-testid="pick-another-task-btn"
                className="text-xs tracking-widest bg-accent text-page-fg px-3 py-2 sm:px-2 sm:py-0.5 hover:bg-accent-hover"
              >
                × close
              </button>
            )}
            {debug ? (
              <>
                <DaemonHealthPill />
                <span data-testid="domain-footer" className="text-[11px] tracking-widest text-panel-dark-fg/85">
                  READY · CTRL+ENTER · CORGFLIX.DUCKDNS.ORG
                </span>
              </>
            ) : (
              <span className="text-[11px] tracking-widest text-panel-dark-fg/85">READY</span>
            )}
          </nav>
        }
      >
        {missingPinnedTask && (
          <div data-testid="missing-pinned-task"
               role="status" aria-live="polite"
               className="bg-accent border-b-4 border-border-strong text-page-fg font-mono text-xs font-bold tracking-widest px-4 py-1.5">
            couldn't open last task ({taskId}) — pick another below
          </div>
        )}
        {here.pathname === "/login"
          ? <LoginPage />
          : here.pathname === "/welcome/ai-literacy"
            ? <AiLiteracyGate lang={gateLang} onConfirmed={() => { setSessionReady(true); navigate("/"); }} />
            : here.pathname === "/review"
            ? <FsrsReview streak={0} />
            : here.pathname === "/tasks"
              ? <TasksScreen />
              : here.pathname === "/settings/trust"
                ? <TrustSettings />
                : here.pathname === "/me"
                ? <SettingsMe />
                : !sessionReady
                  ? <div className="p-6 font-mono text-sm text-page-fg/80">setting up tutor session…</div>
                  : showQuickStart
                    ? <ActiveTaskDashboard />
                    : <TutorWorkspace pdfUrl={`/api/v1/tasks/${encodeURIComponent(taskId)}/pdf`} taskId={taskId} dedupedNotice={dedupedFlag} />}
      </AppShell>
      {ledgerOpen && <KnowledgeLedger onClose={() => setLedgerOpen(false)} />}
    </>
  );
}
