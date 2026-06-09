import { useState, type ReactNode } from "react";
import {
  PALETTES,
  effectivePrimary,
  effectiveSecondary,
  type ThemeChoice,
} from "./palettes";

// The recolor control: a literal colored circle pinned bottom-right. Click it
// and a popup opens with the layout toggle, palette swatches, and custom color
// pickers. The circle itself shows the current accent. This is demo chrome —
// it floats OVER the door and is not part of the door artifact.

export type Skin = "brutalist" | "warm";

const PANEL_BG = "#141418";
const PANEL_LINE = "#3a3a42";
const PANEL_INK = "#ECECEC";
const PANEL_MUTED = "#9a9aa6";

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div style={{ marginBottom: 18 }}>
      <div
        style={{
          fontSize: 10,
          letterSpacing: "0.18em",
          textTransform: "uppercase",
          color: PANEL_MUTED,
          marginBottom: 9,
        }}
      >
        {label}
      </div>
      {children}
    </div>
  );
}

export function ThemePicker({
  skin,
  onSkin,
  choice,
  onChoice,
  concepts,
  conceptId,
  onConcept,
  size = 30,
}: {
  skin: Skin;
  onSkin: (s: Skin) => void;
  choice: ThemeChoice;
  onChoice: (c: ThemeChoice) => void;
  concepts: { id: string; label: string }[];
  conceptId: string;
  onConcept: (id: string) => void;
  size?: number;
}): ReactNode {
  const [open, setOpen] = useState(false);
  const primary = effectivePrimary(choice);
  const secondary = effectiveSecondary(choice);

  return (
    <div
      data-testid="theme-picker"
      style={{
        position: "relative",
        display: "inline-flex",
        fontFamily: 'ui-monospace, "JetBrains Mono", Menlo, Consolas, monospace',
      }}
    >
      {/* ── the colored circle (sits in the brand row) ── */}
      <button
        type="button"
        aria-label="Open theme picker"
        data-testid="theme-fab"
        onClick={() => setOpen((o) => !o)}
        style={{
          appearance: "none",
          width: size,
          height: size,
          borderRadius: "50%",
          cursor: "pointer",
          background: `conic-gradient(from 220deg, ${primary} 0deg 250deg, ${secondary} 250deg 360deg)`,
          border: "2px solid rgba(255,255,255,0.85)",
          boxShadow: "0 4px 12px -3px rgba(0,0,0,0.6)",
          padding: 0,
          flex: "none",
        }}
      />

      {/* ── popup panel, opens downward from the circle ── */}
      {open && (
        <div
          data-testid="theme-panel"
          style={{
            position: "absolute",
            top: size + 12,
            left: 0,
            zIndex: 1000,
            width: 288,
            background: PANEL_BG,
            border: `1px solid ${PANEL_LINE}`,
            color: PANEL_INK,
            padding: "18px 18px 8px",
            boxShadow: "0 18px 50px -12px rgba(0,0,0,0.8)",
          }}
        >
          <div
            style={{
              fontSize: 11,
              letterSpacing: "0.22em",
              textTransform: "uppercase",
              fontWeight: 700,
              marginBottom: 18,
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
            }}
          >
            <span>Theme</span>
            <button
              type="button"
              aria-label="Close theme panel"
              onClick={() => setOpen(false)}
              style={{
                appearance: "none",
                background: "none",
                border: "none",
                color: PANEL_MUTED,
                cursor: "pointer",
                fontSize: 16,
                lineHeight: 1,
                fontFamily: "inherit",
              }}
            >
              ✕
            </button>
          </div>

          {concepts.length > 0 && (
          <Row label="Concept">
            <div
              style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 6 }}
            >
              {concepts.map((c) => {
                const active = c.id === conceptId;
                return (
                  <button
                    key={c.id}
                    type="button"
                    data-testid={`concept-${c.id}`}
                    onClick={() => onConcept(c.id)}
                    style={{
                      appearance: "none",
                      cursor: "pointer",
                      fontFamily: "inherit",
                      fontSize: 10.5,
                      letterSpacing: "0.04em",
                      textAlign: "left",
                      padding: "7px 9px",
                      color: active ? "#0b0b0d" : PANEL_INK,
                      background: active ? primary : "transparent",
                      border: `1px solid ${active ? primary : PANEL_LINE}`,
                      fontWeight: active ? 700 : 400,
                    }}
                  >
                    {c.label}
                  </button>
                );
              })}
            </div>
          </Row>
          )}

          <Row label="Layout">
            <div style={{ display: "flex", gap: 8 }}>
              {(["brutalist", "warm"] as Skin[]).map((s) => {
                const active = s === skin;
                const deferred = s === "warm";
                return (
                  <button
                    key={s}
                    type="button"
                    onClick={deferred ? undefined : () => onSkin(s)}
                    data-testid={`skin-${s}`}
                    disabled={deferred}
                    aria-disabled={deferred}
                    title={deferred ? "în curând" : undefined}
                    style={{
                      flex: 1,
                      appearance: "none",
                      cursor: deferred ? "not-allowed" : "pointer",
                      fontFamily: "inherit",
                      fontSize: 11,
                      letterSpacing: "0.1em",
                      textTransform: "uppercase",
                      padding: "9px 0",
                      color: deferred ? PANEL_MUTED : active ? "#0b0b0d" : PANEL_INK,
                      background: deferred ? "transparent" : active ? primary : "transparent",
                      border: `1px solid ${deferred ? "#3a3a42" : active ? primary : PANEL_LINE}`,
                      fontWeight: active ? 700 : 400,
                      opacity: deferred ? 0.4 : 1,
                      transition: "all 150ms",
                    }}
                  >
                    {s}
                    {deferred && (
                      <span
                        style={{
                          display: "block",
                          fontSize: 9,
                          letterSpacing: "0.05em",
                          textTransform: "lowercase",
                          marginTop: 2,
                          color: PANEL_MUTED,
                          opacity: 0.8,
                        }}
                      >
                        în curând
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          </Row>

          <Row label="Palette">
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(6, 1fr)",
                gap: 10,
              }}
            >
              {PALETTES.map((p) => {
                const active = !choice.customPrimary && p.id === choice.paletteId;
                return (
                  <button
                    key={p.id}
                    type="button"
                    title={p.label}
                    aria-label={p.label}
                    data-testid={`palette-${p.id}`}
                    onClick={() =>
                      onChoice({ paletteId: p.id })
                    }
                    style={{
                      appearance: "none",
                      cursor: "pointer",
                      width: 32,
                      height: 32,
                      borderRadius: "50%",
                      position: "relative",
                      background: p.primary,
                      border: active
                        ? "2px solid #fff"
                        : `1px solid ${PANEL_LINE}`,
                      boxShadow: active ? "0 0 0 2px #141418" : "none",
                      padding: 0,
                    }}
                  >
                    <span
                      style={{
                        position: "absolute",
                        right: 3,
                        bottom: 3,
                        width: 11,
                        height: 11,
                        borderRadius: "50%",
                        background: p.secondary,
                        border: "1.5px solid rgba(0,0,0,0.35)",
                      }}
                    />
                  </button>
                );
              })}
            </div>
          </Row>

          <Row label="Custom colors">
            <div style={{ display: "flex", gap: 14 }}>
              <label
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 8,
                  fontSize: 11,
                  color: PANEL_MUTED,
                }}
              >
                <input
                  type="color"
                  value={primary}
                  data-testid="custom-primary"
                  onChange={(e) =>
                    onChoice({ ...choice, customPrimary: e.target.value })
                  }
                  style={{
                    width: 28,
                    height: 28,
                    padding: 0,
                    border: `1px solid ${PANEL_LINE}`,
                    background: "none",
                    cursor: "pointer",
                  }}
                />
                Accent
              </label>
              <label
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 8,
                  fontSize: 11,
                  color: PANEL_MUTED,
                }}
              >
                <input
                  type="color"
                  value={secondary}
                  data-testid="custom-secondary"
                  onChange={(e) =>
                    onChoice({ ...choice, customSecondary: e.target.value })
                  }
                  style={{
                    width: 28,
                    height: 28,
                    padding: 0,
                    border: `1px solid ${PANEL_LINE}`,
                    background: "none",
                    cursor: "pointer",
                  }}
                />
                2nd
              </label>
              {(choice.customPrimary || choice.customSecondary) && (
                <button
                  type="button"
                  onClick={() => onChoice({ paletteId: choice.paletteId })}
                  style={{
                    marginLeft: "auto",
                    appearance: "none",
                    background: "none",
                    border: "none",
                    color: PANEL_MUTED,
                    fontSize: 10,
                    letterSpacing: "0.1em",
                    textTransform: "uppercase",
                    cursor: "pointer",
                    fontFamily: "inherit",
                  }}
                >
                  reset
                </button>
              )}
            </div>
          </Row>
        </div>
      )}
    </div>
  );
}
