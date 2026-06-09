import { useEffect, useRef, useState } from "react";
import { getCsrfToken } from "../lib/api";
import { useInFlight } from "../lib/inFlight";
import { GraderProviderToggle } from "./GraderProviderToggle";
import { RightsSidebar } from "./RightsSidebar";

/**
 * SettingsMe — Settings / Me tab (surface #12).
 *
 * Brutalist mono style (JetBrains Mono, Ink/Paper/Accent tokens).
 * Displays: account details, AI-literacy status, consent events,
 * hint-mode preference, and three GDPR action controls:
 *   1. Export my data (download link)
 *   2. Pause / Resume logging (POST /api/v1/me/restrict)
 *   3. Delete my account (POST /api/v1/me/delete) — two-click confirm
 *
 * The delete button is NOT the yellow CTA — it is a bordered danger control.
 * In-flight guards on all POST buttons.
 */

interface ConsentEvent {
  consentType: string;
  granted: boolean;
  recordedAt: string;
}

interface Preferences {
  hintMode: string;
  loggingPausedUntil: string | null;
}

interface MeExport {
  user: {
    id: string;
    name: string;
    email: string;
    scope: string;
    lang: string;
  };
  consentEvents: ConsentEvent[];
  preferences: Preferences;
  aiLiteracyConfirmed: boolean;
  exportedAt: string;
}

export function SettingsMe() {
  const [data, setData] = useState<MeExport | null>(null);
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);

  // Logging pause state — derived from data on load, then updated optimistically
  const [loggingPaused, setLoggingPaused] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  // Delete confirm: "idle" | "confirm" (waiting for second click)
  const [deleteConfirmState, setDeleteConfirmState] = useState<"idle" | "confirm">("idle");
  // Timer ref for resetting the confirm state — ref avoids extra re-renders on set/clear
  const deleteConfirmTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const pauseFlight = useInFlight();
  const deleteFlight = useInFlight();

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setFetchError(null);
    fetch("/api/v1/me/export", { credentials: "include" })
      .then(r => {
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json() as Promise<MeExport>;
      })
      .then(d => {
        if (cancelled) return;
        setData(d);
        // Derive loggingPaused: paused if loggingPausedUntil is set and in the future
        const until = d.preferences.loggingPausedUntil;
        setLoggingPaused(!!until && new Date(until) > new Date());
      })
      .catch(e => {
        if (cancelled) return;
        setFetchError((e as Error).message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, []);

  // Clean up delete-confirm timer on unmount ([] dep = registers once, fires once)
  useEffect(() => () => {
    if (deleteConfirmTimerRef.current) clearTimeout(deleteConfirmTimerRef.current);
  }, []);

  async function handlePauseToggle() {
    setActionError(null);
    try {
      await pauseFlight.run(async () => {
        const r = await fetch("/api/v1/me/restrict", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-CSRF-Token": getCsrfToken() ?? "",
          },
          credentials: "include",
        });
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        const result: { loggingPaused: boolean } = await r.json();
        setLoggingPaused(result.loggingPaused);
      });
    } catch (e) {
      setActionError((e as Error).message);
    }
  }

  function handleDeleteClick() {
    if (deleteConfirmState === "idle") {
      // First click — enter confirm state with 5s auto-reset
      if (deleteConfirmTimerRef.current) clearTimeout(deleteConfirmTimerRef.current);
      setDeleteConfirmState("confirm");
      deleteConfirmTimerRef.current = setTimeout(() => {
        setDeleteConfirmState("idle");
        deleteConfirmTimerRef.current = null;
      }, 5000);
    } else {
      // Second click — execute delete
      if (deleteConfirmTimerRef.current) {
        clearTimeout(deleteConfirmTimerRef.current);
        deleteConfirmTimerRef.current = null;
      }
      executeDelete();
    }
  }

  async function executeDelete() {
    setActionError(null);
    try {
      await deleteFlight.run(async () => {
        const r = await fetch("/api/v1/me/delete", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-CSRF-Token": getCsrfToken() ?? "",
          },
          credentials: "include",
        });
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        // Account deleted — redirect to login
        window.location.href = "/tutor/login";
      });
    } catch (e) {
      setDeleteConfirmState("idle");
      setActionError((e as Error).message);
    }
  }

  if (loading) {
    return (
      <div className="p-6 font-mono text-sm text-page-fg/80">
        loading account data…
      </div>
    );
  }

  if (fetchError || !data) {
    return (
      <div className="p-6 font-mono text-sm text-danger-text">
        failed to load account data: {fetchError ?? "unknown error"}
      </div>
    );
  }

  return (
    <div data-testid="settings-me" className="p-4 font-mono">
      <h1 className="text-lg font-bold tracking-widest mb-4">ACCOUNT &amp; PRIVACY</h1>

      {/* 2-col layout: settings left (7fr), RightsSidebar right (3fr). Stacks on mobile. */}
      <div className="flex flex-col md:flex-row gap-4 items-start">
      {/* ── LEFT: main settings content ── */}
      <div className="flex-1 min-w-0">

      {/* Account section */}
      <section className="border-2 border-border-strong p-3 mb-4">
        <div className="text-xs font-bold tracking-widest mb-2">ACCOUNT</div>
        <dl className="text-sm space-y-1">
          <div className="flex gap-2">
            <dt className="text-page-fg/60 w-16 shrink-0">name</dt>
            <dd data-testid="me-name">{data.user.name}</dd>
          </div>
          <div className="flex gap-2">
            <dt className="text-page-fg/60 w-16 shrink-0">email</dt>
            <dd data-testid="me-email">{data.user.email}</dd>
          </div>
          <div className="flex gap-2">
            <dt className="text-page-fg/60 w-16 shrink-0">scope</dt>
            <dd data-testid="me-scope">{data.user.scope}</dd>
          </div>
          <div className="flex gap-2">
            <dt className="text-page-fg/60 w-16 shrink-0">lang</dt>
            <dd data-testid="me-lang">{data.user.lang}</dd>
          </div>
        </dl>
      </section>

      {/* AI literacy status */}
      <section className="border-2 border-border-strong p-3 mb-4">
        <div className="text-xs font-bold tracking-widest mb-2">AI STATUS</div>
        <p className="text-sm" data-testid="me-literacy-status">
          AI literacy:{" "}
          <span className={data.aiLiteracyConfirmed ? "text-page-fg font-bold" : "text-danger-text"}>
            {data.aiLiteracyConfirmed ? "confirmed" : "not confirmed"}
          </span>
        </p>
      </section>

      {/* Consent events */}
      <section className="border-2 border-border-strong p-3 mb-4">
        <div className="text-xs font-bold tracking-widest mb-2">CONSENT LOG</div>
        {data.consentEvents.length === 0 ? (
          <p className="text-xs text-page-fg/60" data-testid="me-consent-empty">
            no consent events recorded
          </p>
        ) : (
          <ul className="space-y-1" data-testid="me-consent-list">
            {data.consentEvents.map((ev, i) => (
              <li key={i} className="text-xs">
                <span className="font-bold">{ev.consentType}</span>
                {" · "}
                <span className={ev.granted ? "text-page-fg" : "text-danger-text"}>
                  {ev.granted ? "granted" : "revoked"}
                </span>
                {" · "}
                <span className="text-page-fg/60">
                  {ev.recordedAt.slice(0, 19).replace("T", " ")}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* Preferences */}
      <section className="border-2 border-border-strong p-3 mb-4">
        <div className="text-xs font-bold tracking-widest mb-2">PREFERENCES</div>
        <p className="text-xs" data-testid="me-hint-mode">
          hint mode: <span className="font-bold">{data.preferences.hintMode}</span>
        </p>
        <p className="text-xs mt-1" data-testid="me-logging-status">
          logging:{" "}
          <span className={loggingPaused ? "text-page-fg/60" : "font-bold"}>
            {loggingPaused ? "paused" : "active"}
          </span>
        </p>
      </section>

      {/* GDPR action controls */}
      <section className="border-2 border-border-strong p-3 mb-4">
        <div className="text-xs font-bold tracking-widest mb-3">DATA &amp; PRIVACY</div>

        {/* Export — yellow CTA (the ONE yellow CTA for this surface) */}
        <div className="mb-4">
          <div className="text-xs text-page-fg/60 mb-1">Download a copy of all your data.</div>
          <a
            href="/api/v1/me/export"
            download
            data-testid="me-export-btn"
            className="inline-block text-xs font-bold tracking-widest bg-accent text-page-fg px-4 py-2 hover:bg-accent-hover"
          >
            EXPORT MY DATA
          </a>
        </div>

        {/* Pause / Resume logging */}
        <div className="mb-4">
          <div className="text-xs text-page-fg/60 mb-1">
            Temporarily stop recording your learning interactions.
          </div>
          <button
            type="button"
            data-testid="me-pause-btn"
            onClick={handlePauseToggle}
            disabled={pauseFlight.inFlight || deleteFlight.inFlight}
            className="text-xs font-bold tracking-widest bg-page-bg text-page-fg border-2 border-border-strong px-4 py-2 hover:bg-panel-dark-bg hover:text-panel-dark-fg disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loggingPaused ? "RESUME LOGGING" : "PAUSE LOGGING"}
          </button>
          {pauseFlight.showSpinner && (
            <span aria-live="polite" className="ml-2 text-xs text-page-fg/60">updating…</span>
          )}
        </div>

        {/* Delete my account — danger bordered control, NOT the yellow CTA */}
        <div>
          <div className="text-xs text-page-fg/60 mb-1">
            Permanently remove your account and all associated data. This cannot be undone.
          </div>
          <button
            type="button"
            data-testid="me-delete-btn"
            onClick={handleDeleteClick}
            disabled={deleteFlight.inFlight || pauseFlight.inFlight}
            className="text-xs font-bold tracking-widest bg-page-bg text-danger-text border-2 border-danger-text px-4 py-2 hover:bg-danger-text hover:text-page-bg disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {deleteConfirmState === "confirm"
              ? "CLICK AGAIN TO CONFIRM DELETION"
              : "DELETE MY ACCOUNT"}
          </button>
          {deleteFlight.showSpinner && (
            <span aria-live="polite" className="ml-2 text-xs text-page-fg/60">deleting…</span>
          )}
          {deleteConfirmState === "confirm" && (
            <p className="text-xs text-danger-text mt-1" role="alert">
              This will permanently delete your account. Click the button again to confirm.
            </p>
          )}
        </div>
      </section>

      {actionError && (
        <div data-testid="me-action-error" className="mt-2 text-xs text-danger-text">
          {actionError}
        </div>
      )}

      {/* Grader provider selector */}
      <section className="mb-4">
        <GraderProviderToggle />
      </section>

      </div>{/* ── end LEFT column ── */}

      {/* ── RIGHT: GDPR + AI Act rights sidebar (3fr, sticky top) ── */}
      <aside className="w-full md:w-80 md:shrink-0 md:sticky md:top-4">
        <RightsSidebar />
      </aside>

      </div>{/* ── end 2-col flex ── */}
    </div>
  );
}
