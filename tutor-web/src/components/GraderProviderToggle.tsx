/** GraderProviderToggle — selector for the drill-grader LLM provider.
 *
 *  GET /api/v1/me/grader-provider  — read current setting.
 *  PUT /api/v1/me/grader-provider  — write chosen setting.
 *
 *  Three options:
 *   "free"        — Gratuit (default, OpenRouter free-tier)
 *   "claude"      — Claude (uses the user's weekly Claude Max quota)
 *   "freellmapi"  — FreeLLM API (requires a configured endpoint)
 *
 *  testids:
 *   grader-provider-toggle            — the container
 *   grader-provider-option-free       — radio option
 *   grader-provider-option-claude     — radio option
 *   grader-provider-option-freellmapi — radio option
 *   grader-provider-save-btn          — save button
 *   grader-provider-status            — status/error message
 */
import { useEffect, useState } from "react";
import { jarvisFetch } from "../lib/api";

type GraderProvider = "free" | "claude" | "freellmapi";

interface Option {
  value: GraderProvider;
  labelRo: string;
  noteRo?: string;
}

const OPTIONS: Option[] = [
  {
    value: "free",
    labelRo: "Gratuit",
    noteRo: "implicit — OpenRouter gratuit (recomandat)",
  },
  {
    value: "claude",
    labelRo: "Claude",
    noteRo: "folosește limita ta săptămânală Claude",
  },
  {
    value: "freellmapi",
    labelRo: "FreeLLM API",
    noteRo: "necesită endpoint configurat",
  },
];

export function GraderProviderToggle() {
  const [current, setCurrent] = useState<GraderProvider>("free");
  const [selected, setSelected] = useState<GraderProvider>("free");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    jarvisFetch("/api/v1/me/grader-provider")
      .then((r) => {
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json() as Promise<{ provider: GraderProvider }>;
      })
      .then((d) => {
        if (cancelled) return;
        setCurrent(d.provider);
        setSelected(d.provider);
      })
      .catch((e: unknown) => {
        if (!cancelled) setStatus(`Eroare la încărcare: ${(e as Error).message}`);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleSave() {
    setSaving(true);
    setStatus(null);
    try {
      const r = await jarvisFetch("/api/v1/me/grader-provider", {
        method: "PUT",
        body: JSON.stringify({ provider: selected }),
      });
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      const d = (await r.json()) as { provider: GraderProvider };
      setCurrent(d.provider);
      setSelected(d.provider);
      setStatus("Salvat.");
    } catch (e) {
      setStatus(`Eroare la salvare: ${(e as Error).message}`);
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <div
        data-testid="grader-provider-toggle"
        className="text-xs text-page-fg/50 font-mono tracking-widest"
      >
        Se încarcă…
      </div>
    );
  }

  return (
    <div
      data-testid="grader-provider-toggle"
      className="border-2 border-border-strong p-3 font-mono"
    >
      <div className="text-xs font-bold tracking-widest mb-3 uppercase">
        Provider corector AI
      </div>

      <fieldset className="space-y-2 mb-4" aria-label="Provider corector AI">
        {OPTIONS.map((opt) => (
          <label
            key={opt.value}
            className="flex items-start gap-2 cursor-pointer"
          >
            <input
              type="radio"
              name="grader-provider"
              data-testid={`grader-provider-option-${opt.value}`}
              value={opt.value}
              checked={selected === opt.value}
              onChange={() => setSelected(opt.value)}
              className="mt-0.5 accent-[var(--accent)]"
            />
            <span className="text-xs leading-snug">
              <span className="font-bold">{opt.labelRo}</span>
              {opt.noteRo && (
                <span className="ml-1 text-page-fg/60">{`— ${opt.noteRo}`}</span>
              )}
            </span>
          </label>
        ))}
      </fieldset>

      <button
        type="button"
        data-testid="grader-provider-save-btn"
        onClick={handleSave}
        disabled={saving || selected === current}
        className="text-xs font-bold tracking-widest bg-accent text-page-fg px-4 py-2 hover:bg-accent-hover disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {saving ? "Se salvează…" : "Salvează"}
      </button>

      {status && (
        <p
          data-testid="grader-provider-status"
          className="mt-2 text-xs text-page-fg/70"
        >
          {status}
        </p>
      )}
    </div>
  );
}
