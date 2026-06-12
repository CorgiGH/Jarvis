import { type CSSProperties, type ReactNode } from "react";
import type { DoorConcept } from "./concept";
import { Figure } from "./figures";

// CONCEPT DOOR — warm editorial skin (the "what if we change the brand" option).
// Faithful fusion of doors-lab M1 (warm coral/teal structure: topbar, kicker pill,
// monumental Fraunces title with a coral focal line, teal math chip, coral BEGIN)
// with M8's OPEN diagram treatment (the tree is PLACED behind a hairline rule with
// level rails + caption — not the boxed rounded treecard M1 used).
//
// NOTE: this skin INTENTIONALLY violates DESIGN.md (serif, multi-hue, gradient,
// radius, soft shadow). All styles are scoped under `.door-warm`. Fraunces/Nunito
// are self-hosted globally via index.css @font-face (Plan 4a §0.9A) — no runtime CDN
// <link> — so nothing here leaks into the real brutalist brand tokens.

const WARM_CSS = `
.door-warm{
  --bg:#1C1714; --ink:#F4EDE4; --ink-soft:#B7A998; --ink-faint:#9d927e;
  --coral:#FF7A5C; --coral-deep:#FF9576; --teal:#5BD7C4; --teal-text:#74E0CF;
  --teal-tint:#143029; --coral-tint:#3A231C; --card:#251E19; --line:#473C33;
  background:var(--bg); color:var(--ink); min-height:100vh;
  font-family:'Nunito',system-ui,sans-serif; -webkit-font-smoothing:antialiased;
  overflow-x:hidden; display:flex; flex-direction:column;
  background-image:
    radial-gradient(900px 720px at 90% 6%, color-mix(in srgb, var(--coral) 16%, transparent) 0%, transparent 60%),
    radial-gradient(820px 820px at 2% 98%, color-mix(in srgb, var(--teal) 12%, transparent) 0%, transparent 55%);
}
.door-warm *{margin:0;padding:0;box-sizing:border-box;}
.door-warm .wrap{
  flex:1 0 auto; width:100%; max-width:1600px; margin:0 auto;
  padding:0 clamp(28px,6vw,120px); display:flex; flex-direction:column;
}
.door-warm .topbar{
  display:flex; align-items:center; justify-content:space-between; gap:16px;
  padding:clamp(20px,4vh,40px) 0 0;
}
.door-warm .brand{display:flex;align-items:center;gap:11px;font-weight:900;font-size:17px;}
.door-warm .brand .mark{
  width:32px;height:32px;border-radius:12px;background:var(--coral);
  box-shadow:0 8px 22px rgba(255,122,92,.42); display:grid;place-items:center;flex:none;
}
.door-warm .progress{
  display:flex;align-items:center;gap:10px;color:var(--ink-soft);font-size:13px;font-weight:800;
  background:var(--card);border:1px solid var(--line);padding:8px 15px 8px 12px;border-radius:999px;
}
.door-warm .pips{display:flex;gap:5px}
.door-warm .pips i{width:8px;height:8px;border-radius:50%;background:var(--line);display:block}
.door-warm .pips i.on{background:var(--teal)}
.door-warm .pips i.now{background:var(--coral);transform:scale(1.3)}
.door-warm .spread{
  flex:1 0 auto; display:grid;
  grid-template-columns:minmax(0,1.05fr) minmax(0,0.95fr);
  gap:clamp(32px,5vw,88px); align-items:center; padding:clamp(36px,7vh,90px) 0;
}
.door-warm .kicker{
  display:inline-flex;align-items:center;gap:11px;width:max-content;
  font-size:12.5px;font-weight:800;letter-spacing:.2em;text-transform:uppercase;
  color:var(--coral-deep);background:var(--coral-tint);padding:9px 17px;border-radius:999px;
  margin-bottom:clamp(22px,4vh,40px);
}
.door-warm .kicker .dot{width:6px;height:6px;border-radius:50%;background:var(--coral);
  flex:none;box-shadow:0 0 0 4px rgba(255,122,92,.20);}
.door-warm .kicker .sep{width:4px;height:4px;border-radius:50%;background:var(--coral);opacity:.6;flex:none}
.door-warm .kicker .first{color:var(--teal-text)}
.door-warm .title{
  font-family:'Fraunces',Georgia,serif;font-weight:900;
  font-size:clamp(48px,8vw,124px);line-height:.88;letter-spacing:-.02em;
  color:var(--ink);margin-bottom:clamp(26px,4vh,42px);
}
.door-warm .title .l1{display:block}
.door-warm .title .l2{display:block;color:var(--coral);font-style:italic}
.door-warm .gist{
  position:relative;max-width:40ch;padding-left:clamp(18px,1.4vw,24px);
  border-left:2px solid var(--coral);margin-bottom:clamp(30px,5vh,52px);
}
.door-warm .gist .lead{
  font-size:clamp(16px,1.45vw,20px);line-height:1.55;font-weight:600;color:var(--ink-soft);
}
.door-warm .gist .lead .verb{color:var(--coral-deep);font-weight:800}
.door-warm .gist .eq{
  display:block;font-family:'Fraunces',serif;font-weight:600;color:var(--teal-text);
  background:var(--teal-tint);padding:3px 10px;border-radius:8px;width:max-content;
  margin-top:12px;font-size:clamp(15px,1.3vw,19px);
}
.door-warm .action{display:flex;align-items:center;gap:clamp(18px,2vw,30px);flex-wrap:wrap}
.door-warm .begin{
  appearance:none;border:none;cursor:pointer;font-family:'Nunito',sans-serif;font-weight:900;
  font-size:18px;letter-spacing:.01em;color:#241712;background:var(--coral);
  padding:18px 40px;border-radius:22px;display:inline-flex;align-items:center;gap:14px;
  box-shadow:0 16px 38px rgba(255,122,92,.36), inset 0 1px 0 rgba(255,255,255,.25);
  transition:transform .15s ease, box-shadow .15s ease, background .15s ease;
}
.door-warm .begin:hover{background:#FF8E72;transform:translateY(-2px)}
.door-warm .hint{display:flex;align-items:center;gap:10px;color:var(--ink-soft);font-size:13.5px;font-weight:700}
.door-warm .hint kbd{
  font-family:'Nunito',sans-serif;font-size:12px;font-weight:800;color:var(--ink);
  background:var(--card);border:1px solid var(--line);border-bottom-width:2px;
  border-radius:8px;padding:5px 9px;
}
.door-warm .plate{
  min-width:0;align-self:stretch;display:flex;flex-direction:column;justify-content:center;
  border-left:1px solid var(--line);padding-left:clamp(28px,3.5vw,72px);
}
.door-warm .fig-label{
  font-family:'Fraunces',serif;font-style:italic;font-size:clamp(13px,1vw,15px);
  letter-spacing:.04em;color:var(--ink-faint);margin-bottom:clamp(22px,4vh,44px);
}
.door-warm .fig-label b{font-style:normal;color:var(--coral-deep);font-weight:700}
.door-warm .plate svg{width:100%;height:auto;display:block;max-height:60vh}
.door-warm .caption{
  margin-top:clamp(24px,4vh,44px);padding-top:clamp(16px,2vh,22px);border-top:1px solid var(--line);
  font-family:'Fraunces',serif;font-style:italic;font-size:clamp(14px,1.04vw,16px);
  line-height:1.55;color:var(--ink-faint);max-width:40ch;
}
.door-warm .caption b{font-style:normal;color:var(--ink-soft);font-weight:600}
.door-warm .colophon{
  display:flex;justify-content:flex-end;padding:18px 0 clamp(24px,4vh,42px);
  border-top:1px solid var(--line);font-family:'Fraunces',serif;font-style:italic;
  font-size:clamp(12px,.9vw,14px);color:var(--ink-faint);letter-spacing:.02em;
}
.door-warm .colophon .dot{color:var(--coral);padding:0 8px}
@media (max-width:900px){
  .door-warm .spread{grid-template-columns:1fr;gap:clamp(32px,6vh,56px)}
  .door-warm .plate{border-left:none;border-top:1px solid var(--line);padding-left:0;padding-top:clamp(28px,5vh,44px)}
}
`;

// The figure's color contract for the warm editorial surface (maps to the
// active warm palette; serif figure type to match the door).
const WARM_FIG_VARS: CSSProperties = {
  ["--fig-accent" as string]: "var(--coral)",
  ["--fig-accent-ink" as string]: "#241712",
  ["--fig-ink" as string]: "var(--ink)",
  ["--fig-line" as string]: "var(--line)",
  ["--fig-rail" as string]: "var(--line)",
  ["--fig-node-fill" as string]: "var(--card)",
  ["--fig-node-ink" as string]: "var(--ink)",
  ["--fig-muted" as string]: "var(--ink-faint)",
  ["--fig-font" as string]: "'Fraunces', Georgia, serif",
} as CSSProperties;

export function DoorWarm({
  concept,
  theme,
  brandMark,
}: {
  concept: DoorConcept;
  theme?: CSSProperties;
  brandMark?: ReactNode;
}): ReactNode {
  // Fonts (Fraunces/Nunito) are self-hosted via index.css @font-face (Plan 4a §0.9A).
  // The former runtime CDN <link> injection is removed — see council reason in index.css.
  const pips = Array.from({ length: concept.progress.total }, (_, i) => i);
  return (
    <div
      className="door-warm"
      data-testid="door-warm"
      style={{ ...WARM_FIG_VARS, ...theme }}
    >
      <style>{WARM_CSS}</style>
      <div className="wrap">
        <header className="topbar">
          <div className="brand">
            {brandMark ?? (
              <span className="mark" aria-hidden>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                  <path
                    d="M5 13l4 4L19 7"
                    stroke="#241712"
                    strokeWidth="3.2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </span>
            )}
            <span>Tutor</span>
          </div>
          <div className="progress">
            <span className="pips" aria-hidden>
              {pips.map((i) => (
                <i
                  key={i}
                  className={
                    i === concept.progress.current - 1
                      ? "now"
                      : i < concept.progress.current - 1
                        ? "on"
                        : ""
                  }
                />
              ))}
            </span>
            <span>
              Concept {concept.progress.current} of {concept.progress.total}
            </span>
          </div>
        </header>

        <div className="spread">
          <section style={{ minWidth: 0 }}>
            <p className="kicker">
              <span className="dot" aria-hidden />
              <span>{concept.subject}</span>
              <span className="sep" aria-hidden />
              <span>{concept.track}</span>
              <span className="sep" aria-hidden />
              <span className="first">{concept.familiarity}</span>
            </p>

            <h1 className="title">
              <span className="l1">{concept.titleTop}</span>
              <span className="l2">{concept.titleAccent}</span>
            </h1>

            <div className="gist">
              <p className="lead">
                {(() => {
                  const at = concept.gistLead.indexOf(concept.gistVerb);
                  if (at < 0) return concept.gistLead;
                  return (
                    <>
                      {concept.gistLead.slice(0, at)}
                      <span className="verb">{concept.gistVerb}</span>
                      {concept.gistLead.slice(at + concept.gistVerb.length)}
                    </>
                  );
                })()}
                <span className="eq">{concept.equation}</span>
              </p>
            </div>

            <div className="action">
              <button className="begin" type="button" data-testid="door-begin">
                Begin
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden>
                  <path
                    d="M5 12h13M13 6l6 6-6 6"
                    stroke="#241712"
                    strokeWidth="2.6"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </button>
              <p className="hint">
                <span>No rush — or press</span>
                <kbd>Ctrl</kbd>
                <kbd>Enter</kbd>
              </p>
            </div>
          </section>

          <aside className="plate">
            <div className="fig-label">
              <b>Fig. 1</b> · {concept.figureLabel}
            </div>
            <Figure spec={concept.figure} />
            <p className="caption">{concept.caption}</p>
          </aside>
        </div>

        <footer className="colophon">
          {concept.footer.map((f, i) => (
            <span key={f}>
              {i > 0 && <span className="dot">·</span>}
              {f}
            </span>
          ))}
        </footer>
      </div>
    </div>
  );
}
