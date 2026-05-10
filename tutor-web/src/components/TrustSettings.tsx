import { useEffect, useState } from "react";
import { jarvisFetch } from "../lib/api";
import { useInFlight } from "../lib/inFlight";

interface GrantView {
  id: string;
  scope: string[];
  ops: string[];
  expiresAt: string;
  callsUsed: number;
  maxCalls: number;
  revokedAt?: string | null;
}

/**
 * Tutor Layer B1 — minimal trust grant management UI.
 *
 * Spec §4 item 4: explicit always-confirm by default; trust grant rows
 * are explicit DB rows w/ TTL (default 1h, max 8h); revoke = single-row
 * delete. NO blanket trust-class toggle.
 *
 * This screen lets the user create scoped grants (which paths the
 * effector may write under, for how long, capped at how many calls)
 * and revoke them one at a time.
 */
export function TrustSettings() {
  const [grants, setGrants] = useState<GrantView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [scope, setScope] = useState("file:///c/Users/User/work/**");
  const [ttlMinutes, setTtlMinutes] = useState(60);
  const [maxCalls, setMaxCalls] = useState(10);
  const grant = useInFlight();

  async function refresh() {
    setLoading(true);
    setError(null);
    try {
      const r = await jarvisFetch("/api/v1/grants");
      if (!r.ok) {
        throw new Error(`HTTP ${r.status}`);
      }
      const data: { grants: GrantView[] } = await r.json();
      setGrants(data.grants);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    refresh();
  }, []);

  async function createGrant() {
    setError(null);
    try {
      await grant.run(async () => {
        const r = await jarvisFetch("/api/v1/grants", {
          method: "POST",
          body: JSON.stringify({
            scope: [scope],
            ops: ["APPLY_EDIT"],
            ttlSeconds: ttlMinutes * 60,
            maxCalls,
          }),
        });
        if (!r.ok) {
          throw new Error(`HTTP ${r.status}: ${(await r.text()).slice(0, 200)}`);
        }
        await refresh();
      });
    } catch (e) {
      setError((e as Error).message);
    }
  }

  async function revokeGrant(id: string) {
    setError(null);
    try {
      const r = await jarvisFetch(`/api/v1/grants/${id}/revoke`, { method: "POST" });
      if (!r.ok && r.status !== 204) {
        throw new Error(`HTTP ${r.status}`);
      }
      await refresh();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  return (
    <div data-testid="trust-settings" className="p-4 font-mono">
      <h1 className="text-lg font-bold tracking-widest mb-3">TRUST GRANTS</h1>
      <p className="text-xs text-page-fg/60 mb-4">
        Each grant gives the daemon permission to write to one or more
        paths for a limited time + call count. Default 1h / 10 calls.
        Cap 8h. Revoke any time.
      </p>

      <form data-testid="trust-create-form"
            onSubmit={e => { e.preventDefault(); createGrant(); }}
            className="border-2 border-border-strong p-3 mb-6">
        <div className="text-xs font-bold tracking-widest mb-2">NEW GRANT</div>
        <label htmlFor="trust-scope-id" className="block text-xs mb-1">Scope (file:// glob)</label>
        <input
          id="trust-scope-id"
          data-testid="trust-scope-input"
          className="w-full border border-border-thin px-2 py-1 text-sm mb-2"
          value={scope}
          onChange={e => setScope(e.target.value)}
        />
        <div className="flex gap-2 mb-2">
          <div className="flex-1">
            <label htmlFor="trust-ttl-id" className="block text-xs mb-1">TTL minutes (max 480 = 8h)</label>
            <input
              id="trust-ttl-id"
              data-testid="trust-ttl-input"
              type="number" min={1} max={480}
              className="w-full border border-border-thin px-2 py-1 text-sm"
              value={ttlMinutes}
              onChange={e => setTtlMinutes(Math.max(1, parseInt(e.target.value, 10) || 60))}
            />
          </div>
          <div className="flex-1">
            <label htmlFor="trust-max-calls-id" className="block text-xs mb-1">Max calls</label>
            <input
              id="trust-max-calls-id"
              data-testid="trust-max-calls-input"
              type="number" min={1} max={1000}
              className="w-full border border-border-thin px-2 py-1 text-sm"
              value={maxCalls}
              onChange={e => setMaxCalls(Math.max(1, parseInt(e.target.value, 10) || 10))}
            />
          </div>
        </div>
        <button
          type="submit"
          data-testid="trust-create-btn"
          disabled={grant.inFlight}
          className="text-xs font-bold tracking-widest bg-panel-dark-bg text-panel-dark-fg px-3 py-1 disabled:opacity-50"
        >
          GRANT
        </button>
        {grant.showSpinner && (
          <span data-testid="trust-create-spinner" aria-live="polite" className="ml-2 text-xs text-page-fg/60">granting…</span>
        )}
      </form>

      <div className="text-xs font-bold tracking-widest mb-2">ACTIVE ({grants.filter(g => !g.revokedAt).length})</div>
      {loading ? (
        <div className="text-sm">loading…</div>
      ) : grants.length === 0 ? (
        <div role="status" className="text-sm text-page-fg/60">no grants yet — fill the NEW GRANT form above to add one</div>
      ) : (
        <ul className="space-y-2" data-testid="trust-grants-list">
          {grants.map(g => (
            <li key={g.id}
                data-testid="trust-grant-row" data-grant-id={g.id}
                className={`border ${g.revokedAt ? "border-border-thin opacity-50" : "border-border-strong"} p-2`}>
              <div className="text-xs font-bold tracking-widest">
                {g.ops.join("+")} · expires {g.expiresAt.slice(0, 19)} ·
                {" "}{g.callsUsed}/{g.maxCalls} calls
                {g.revokedAt ? <span className="ml-2 text-danger-text">REVOKED</span> : null}
              </div>
              <div className="text-xs mt-1 break-all">{g.scope.join(", ")}</div>
              {!g.revokedAt && (
                <button
                  data-testid="trust-revoke-btn"
                  onClick={() => revokeGrant(g.id)}
                  className="mt-2 text-xs font-bold tracking-widest bg-page-bg text-page-fg border border-border-strong px-2 py-0.5"
                >
                  REVOKE
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      {error && (
        <div data-testid="trust-error" className="mt-4 text-xs text-danger-text">
          {error}
        </div>
      )}
    </div>
  );
}
