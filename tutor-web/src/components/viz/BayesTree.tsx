import type { ReactNode } from "react";
import { AlgoStepperShell, type Frame } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";
import {
  AnimatePresence,
  DrawLine,
  PopIn,
  TweenText,
  motion,
} from "./motion-helpers";

type BayesState = {
  step: number;
  prior: number;
  sensitivity: number;
  fpr: number;
  showJointDP: boolean;
  showJointHP: boolean;
  showTotal: boolean;
  showPosterior: boolean;
  message: string;
  highlightArea: "DP" | "HP" | "DN" | "HN" | "ALL" | null;
};

function buildFrames(): Frame<BayesState>[] {
  const sens = 0.95;
  const fpr = 0.05;
  const frames: Frame<BayesState>[] = [];

  const mk = (
    step: number,
    prior: number,
    showJointDP: boolean,
    showJointHP: boolean,
    showTotal: boolean,
    showPosterior: boolean,
    message: string,
    highlightArea: BayesState["highlightArea"] = null,
  ): Frame<BayesState> => ({
    state: {
      step,
      prior,
      sensitivity: sens,
      fpr,
      showJointDP,
      showJointHP,
      showTotal,
      showPosterior,
      message,
      highlightArea,
    },
    aria: message,
  });

  // Step 0: setup, prior only
  frames.push(mk(0, 0.01, false, false, false, false, "Setup: rare disease, P(D) = 0.01."));
  // Step 1: add conditionals
  frames.push(mk(1, 0.01, false, false, false, false, "Test sensitivity P(+|D) = 0.95. Specificity P(-|H) = 0.95 → P(+|H) = 0.05."));
  // Step 2: joint DP
  frames.push(mk(2, 0.01, true, false, false, false, "Joint P(D ∩ +) = P(D) × P(+|D) = 0.01 × 0.95 = 0.0095.", "DP"));
  // Step 3: joint HP
  frames.push(mk(3, 0.01, true, true, false, false, "Joint P(H ∩ +) = P(H) × P(+|H) = 0.99 × 0.05 = 0.0495.", "HP"));
  // Step 4: total positive
  frames.push(mk(4, 0.01, true, true, true, false, "Total P(+) = 0.0095 + 0.0495 = 0.0590.", "ALL"));
  // Step 5: posterior
  frames.push(mk(5, 0.01, true, true, true, true, "Bayes: P(D|+) = P(D ∩ +) / P(+) = 0.0095 / 0.0590 ≈ 0.161 (16.1%)."));
  // Step 6: surprise
  frames.push(mk(6, 0.01, true, true, true, true, "Surprise: a POSITIVE test only gives 16% chance of disease — base rate dominates!"));
  // Step 7: prior 0.10
  frames.push(mk(7, 0.10, true, true, true, true, "If prior were P(D)=0.10 (less rare), posterior jumps to ~0.679 (67.9%)."));
  // Step 8: prior 0.50
  frames.push(mk(8, 0.50, true, true, true, true, "If prior P(D)=0.50, posterior ≈ 0.950. Strong evidence × moderate prior."));
  // Step 9: summary
  frames.push(mk(9, 0.01, true, true, true, true, "Lesson: base rate (prior) matters enormously. Test alone isn’t enough."));

  return frames;
}

export const FRAME_COUNT = buildFrames().length;

const FRAMES = buildFrames();

const SVG_W = 480;

// Tree pane (left)
const TREE_X = 20;
const TREE_Y = 30;
const TREE_W = 220;
const TREE_H = 200;

// Area pane (right)
const AREA_X = 270;
const AREA_Y = 30;
const AREA_W = 200;
const AREA_H = 200;

// Numeric pane (middle-bottom)
const NUM_Y = 250;
const NUM_X = 20;

// Message (bottom)
const MSG_Y = 340;

function fmtPct(n: number): string {
  return (n * 100).toFixed(2) + "%";
}

function renderFrame(frame: Frame<BayesState>): ReactNode {
  const {
    step,
    prior,
    sensitivity,
    fpr,
    showJointDP,
    showJointHP,
    showTotal,
    showPosterior,
    message,
    highlightArea,
  } = frame.state;
  const pH = 1 - prior;
  const pDP = prior * sensitivity; // disease ∩ positive
  const pDN = prior * (1 - sensitivity); // disease ∩ negative (kept for completeness)
  void pDN; // not rendered but computed for clarity
  const pHP = pH * fpr; // healthy ∩ positive
  const pHN = pH * (1 - fpr); // healthy ∩ negative (kept for completeness)
  void pHN;
  const pPlus = pDP + pHP;
  const posterior = pPlus > 0 ? pDP / pPlus : 0;

  // Visual floor for disease column so tiny priors remain visible
  const PRIOR_MIN_VISUAL = 0.05;
  const visualPrior = Math.max(prior, PRIOR_MIN_VISUAL);

  // Tree layout
  const rootX = TREE_X + 20;
  const branch1Y = TREE_Y + 80;
  const branch2Y = TREE_Y + 160;
  const rootY = (branch1Y + branch2Y) / 2;
  const branchX1 = TREE_X + 90;
  const leafX = TREE_X + 160;

  // Sub-tree leaf endpoints
  const leafDP_y = branch1Y - 18;
  const leafDN_y = branch1Y + 14;
  const leafHP_y = branch2Y - 18;
  const leafHN_y = branch2Y + 14;

  // Highlight stroke widths for area rects
  const dpStroke = highlightArea === "DP" || highlightArea === "ALL" ? 2 : 0.5;
  const hpStroke = highlightArea === "HP" || highlightArea === "ALL" ? 2 : 0.5;
  const dnStroke = highlightArea === "DN" ? 2 : 0.5;
  const hnStroke = highlightArea === "HN" ? 2 : 0.5;

  return (
    <>
      {/* Pane labels */}
      <text
        x={TREE_X}
        y={TREE_Y - 12}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        PROB TREE
      </text>
      <text
        x={AREA_X}
        y={AREA_Y - 12}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        JOINT-PROBABILITY AREA (100% square)
      </text>

      {/* === TREE PANE === */}
      <rect
        x={TREE_X}
        y={TREE_Y}
        width={TREE_W}
        height={TREE_H}
        fill={PAPER}
        stroke={INK}
        strokeWidth={1}
        opacity={0.3}
      />

      {/* Root node (static) */}
      <circle cx={rootX} cy={rootY} r={5} fill={INK} />

      {/* Trunk branches (root → D / H) — draw on as frames begin */}
      <AnimatePresence>
        <DrawLine
          key="edge-root-D"
          x1={rootX}
          y1={rootY}
          x2={branchX1}
          y2={branch1Y}
          stroke={INK}
          strokeWidth={1.5}
          durationMs={400}
        />
        <DrawLine
          key="edge-root-H"
          x1={rootX}
          y1={rootY}
          x2={branchX1}
          y2={branch2Y}
          stroke={INK}
          strokeWidth={1.5}
          durationMs={400}
          delayMs={100}
        />
      </AnimatePresence>

      {/* D node */}
      <circle cx={branchX1} cy={branch1Y} r={5} fill={ACCENT} stroke={INK} strokeWidth={1} />
      <TweenText
        x={branchX1 + 8}
        y={branch1Y + 4}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fontWeight={700}
        fill={INK}
        value={prior}
        formatter={(v) => `D · P=${fmtPct(v)}`}
      />
      {/* H node */}
      <circle cx={branchX1} cy={branch2Y} r={5} fill="#fff" stroke={INK} strokeWidth={1} />
      <TweenText
        x={branchX1 + 8}
        y={branch2Y + 4}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fill={INK}
        value={pH}
        formatter={(v) => `H · P=${fmtPct(v)}`}
      />

      {/* Sub-branches: D → +/-, H → +/- — draw on each frame */}
      <AnimatePresence>
        <DrawLine
          key="edge-D-plus"
          x1={branchX1}
          y1={branch1Y}
          x2={leafX}
          y2={leafDP_y}
          stroke={INK}
          strokeWidth={1}
          opacity={0.6}
          durationMs={350}
          delayMs={200}
        />
        <DrawLine
          key="edge-D-minus"
          x1={branchX1}
          y1={branch1Y}
          x2={leafX}
          y2={leafDN_y}
          stroke={INK}
          strokeWidth={1}
          opacity={0.6}
          durationMs={350}
          delayMs={250}
        />
        <DrawLine
          key="edge-H-plus"
          x1={branchX1}
          y1={branch2Y}
          x2={leafX}
          y2={leafHP_y}
          stroke={INK}
          strokeWidth={1}
          opacity={0.6}
          durationMs={350}
          delayMs={300}
        />
        <DrawLine
          key="edge-H-minus"
          x1={branchX1}
          y1={branch2Y}
          x2={leafX}
          y2={leafHN_y}
          stroke={INK}
          strokeWidth={1}
          opacity={0.6}
          durationMs={350}
          delayMs={350}
        />
      </AnimatePresence>

      {/* Leaf labels — sign + percentage in a single TweenText so they
          can't drift apart or overlap each other. */}
      <TweenText
        x={leafX + 4}
        y={branch1Y - 14}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fill={INK}
        value={sensitivity}
        formatter={(v) => `+ · ${fmtPct(v)}`}
      />
      <TweenText
        x={leafX + 4}
        y={branch1Y + 18}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fill={INK}
        opacity={0.5}
        value={1 - sensitivity}
        formatter={(v) => `− · ${fmtPct(v)}`}
      />
      <TweenText
        x={leafX + 4}
        y={branch2Y - 14}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fill={INK}
        value={fpr}
        formatter={(v) => `+ · ${fmtPct(v)}`}
      />
      <TweenText
        x={leafX + 4}
        y={branch2Y + 18}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fill={INK}
        opacity={0.5}
        value={1 - fpr}
        formatter={(v) => `− · ${fmtPct(v)}`}
      />

      {/* === AREA PANE === */}
      {/* 100×100 unit square; map disease/healthy split + positive/negative */}
      <rect x={AREA_X} y={AREA_Y} width={AREA_W} height={AREA_H} fill={PAPER} stroke={INK} strokeWidth={1} />

      {/* Disease ∩ positive (top-left of area) — width tweens with prior */}
      <motion.rect
        x={AREA_X}
        y={AREA_Y}
        height={AREA_H * sensitivity}
        initial={false}
        animate={{
          width: AREA_W * visualPrior,
          opacity: showJointDP ? 1 : 0.15,
          strokeWidth: dpStroke,
        }}
        transition={{ duration: 0.55, ease: "easeInOut" }}
        fill={ACCENT}
        stroke={INK}
      />
      {/* Disease ∩ negative (bottom-left) */}
      <motion.rect
        x={AREA_X}
        y={AREA_Y + AREA_H * sensitivity}
        height={AREA_H * (1 - sensitivity)}
        initial={false}
        animate={{
          width: AREA_W * visualPrior,
          strokeWidth: dnStroke,
        }}
        transition={{ duration: 0.55, ease: "easeInOut" }}
        fill={ACCENT}
        stroke={INK}
        opacity={0.2}
      />
      {/* Healthy ∩ positive (top-right) */}
      <motion.rect
        y={AREA_Y}
        height={AREA_H * fpr}
        initial={false}
        animate={{
          x: AREA_X + AREA_W * visualPrior,
          width: AREA_W * (1 - visualPrior),
          fillOpacity: showJointHP ? 0.8 : 0.15,
          strokeWidth: hpStroke,
        }}
        transition={{ duration: 0.55, ease: "easeInOut" }}
        fill={INK}
        stroke={INK}
      />
      {/* Healthy ∩ negative (bottom-right) */}
      <motion.rect
        y={AREA_Y + AREA_H * fpr}
        height={AREA_H * (1 - fpr)}
        initial={false}
        animate={{
          x: AREA_X + AREA_W * visualPrior,
          width: AREA_W * (1 - visualPrior),
          strokeWidth: hnStroke,
        }}
        transition={{ duration: 0.55, ease: "easeInOut" }}
        fill="#fff"
        stroke={INK}
      />
      {/* Min-width note when prior is very small */}
      <AnimatePresence>
        {prior < PRIOR_MIN_VISUAL && (
          <PopIn key="min-width-note" durationMs={250}>
            <text x={AREA_X + 2} y={AREA_Y + AREA_H + 12} fontFamily={FONT_FAMILY} fontSize={7} fill={INK} opacity={0.6}>
              (min width 5% for visibility)
            </text>
          </PopIn>
        )}
      </AnimatePresence>

      {/* Labels on area — D-column labels only when the column is wide
          enough (>=15% of AREA_W). Otherwise the "D ∩ +" text overflows
          past the rect and collides with the H labels. */}
      <AnimatePresence>
        {visualPrior >= 0.15 && (
          <PopIn key="d-area-labels" durationMs={300}>
            <text
              x={AREA_X + 4}
              y={AREA_Y + (AREA_H * sensitivity) / 2 + 4}
              fontFamily={FONT_FAMILY}
              fontSize={9}
              fontWeight={700}
              fill={INK}
            >
              D ∩ +
            </text>
            <text
              x={AREA_X + 4}
              y={AREA_Y + AREA_H * (sensitivity + (1 - sensitivity) / 2) + 4}
              fontFamily={FONT_FAMILY}
              fontSize={9}
              fill={INK}
              opacity={0.6}
            >
              D ∩ −
            </text>
          </PopIn>
        )}
      </AnimatePresence>
      <motion.text
        y={AREA_Y + (AREA_H * fpr) / 2 + 6}
        initial={false}
        animate={{ x: AREA_X + AREA_W * visualPrior + 8 }}
        transition={{ type: "tween", duration: 0.55, ease: "easeInOut" }}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={"#fff"}
      >
        H ∩ +
      </motion.text>
      <motion.text
        y={AREA_Y + AREA_H * (fpr + (1 - fpr) / 2) + 4}
        initial={false}
        animate={{ x: AREA_X + AREA_W * visualPrior + 8 }}
        transition={{ type: "tween", duration: 0.55, ease: "easeInOut" }}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fill={INK}
        opacity={0.6}
      >
        H ∩ −
      </motion.text>

      {/* === NUMERIC PANE === */}
      {/* Top conditional line: P(D) = X · P(+|D) = Y · P(+|H) = Z  → split into static + TweenText */}
      <text x={NUM_X} y={NUM_Y + 12} fontFamily={FONT_FAMILY} fontSize={10} fill={INK}>
        P(D) =
      </text>
      <TweenText
        x={NUM_X + 38}
        y={NUM_Y + 12}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fill={INK}
        value={prior}
        formatter={fmtPct}
      />
      <text x={NUM_X + 84} y={NUM_Y + 12} fontFamily={FONT_FAMILY} fontSize={10} fill={INK}>
        · P(+|D) =
      </text>
      <TweenText
        x={NUM_X + 138}
        y={NUM_Y + 12}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fill={INK}
        value={sensitivity}
        formatter={fmtPct}
      />
      <text x={NUM_X + 184} y={NUM_Y + 12} fontFamily={FONT_FAMILY} fontSize={10} fill={INK}>
        · P(+|H) =
      </text>
      <TweenText
        x={NUM_X + 238}
        y={NUM_Y + 12}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fill={INK}
        value={fpr}
        formatter={fmtPct}
      />

      <AnimatePresence>
        {showJointDP && (
          <PopIn key="joint-dp" durationMs={300}>
            <text x={NUM_X} y={NUM_Y + 28} fontFamily={FONT_FAMILY} fontSize={10} fontWeight={700} fill={INK}>
              P(D ∩ +) =
            </text>
            <TweenText
              x={NUM_X + 56}
              y={NUM_Y + 28}
              fontFamily={FONT_FAMILY}
              fontSize={10}
              fontWeight={700}
              fill={INK}
              value={prior}
              formatter={fmtPct}
            />
            <text x={NUM_X + 100} y={NUM_Y + 28} fontFamily={FONT_FAMILY} fontSize={10} fontWeight={700} fill={INK}>
              ×
            </text>
            <TweenText
              x={NUM_X + 110}
              y={NUM_Y + 28}
              fontFamily={FONT_FAMILY}
              fontSize={10}
              fontWeight={700}
              fill={INK}
              value={sensitivity}
              formatter={fmtPct}
            />
            <text x={NUM_X + 156} y={NUM_Y + 28} fontFamily={FONT_FAMILY} fontSize={10} fontWeight={700} fill={INK}>
              =
            </text>
            <TweenText
              x={NUM_X + 166}
              y={NUM_Y + 28}
              fontFamily={FONT_FAMILY}
              fontSize={10}
              fontWeight={700}
              fill={INK}
              value={pDP}
              formatter={fmtPct}
            />
          </PopIn>
        )}
        {showJointHP && (
          <PopIn key="joint-hp" durationMs={300}>
            <text x={NUM_X} y={NUM_Y + 44} fontFamily={FONT_FAMILY} fontSize={10} fontWeight={700} fill={INK}>
              P(H ∩ +) =
            </text>
            <TweenText
              x={NUM_X + 56}
              y={NUM_Y + 44}
              fontFamily={FONT_FAMILY}
              fontSize={10}
              fontWeight={700}
              fill={INK}
              value={pH}
              formatter={fmtPct}
            />
            <text x={NUM_X + 100} y={NUM_Y + 44} fontFamily={FONT_FAMILY} fontSize={10} fontWeight={700} fill={INK}>
              ×
            </text>
            <TweenText
              x={NUM_X + 110}
              y={NUM_Y + 44}
              fontFamily={FONT_FAMILY}
              fontSize={10}
              fontWeight={700}
              fill={INK}
              value={fpr}
              formatter={fmtPct}
            />
            <text x={NUM_X + 156} y={NUM_Y + 44} fontFamily={FONT_FAMILY} fontSize={10} fontWeight={700} fill={INK}>
              =
            </text>
            <TweenText
              x={NUM_X + 166}
              y={NUM_Y + 44}
              fontFamily={FONT_FAMILY}
              fontSize={10}
              fontWeight={700}
              fill={INK}
              value={pHP}
              formatter={fmtPct}
            />
          </PopIn>
        )}
        {showTotal && (
          <PopIn key="total-plus" durationMs={300}>
            <text x={NUM_X} y={NUM_Y + 60} fontFamily={FONT_FAMILY} fontSize={10} fontWeight={700} fill={INK}>
              P(+) =
            </text>
            <TweenText
              x={NUM_X + 36}
              y={NUM_Y + 60}
              fontFamily={FONT_FAMILY}
              fontSize={10}
              fontWeight={700}
              fill={INK}
              value={pDP}
              formatter={fmtPct}
            />
            <text x={NUM_X + 80} y={NUM_Y + 60} fontFamily={FONT_FAMILY} fontSize={10} fontWeight={700} fill={INK}>
              +
            </text>
            <TweenText
              x={NUM_X + 90}
              y={NUM_Y + 60}
              fontFamily={FONT_FAMILY}
              fontSize={10}
              fontWeight={700}
              fill={INK}
              value={pHP}
              formatter={fmtPct}
            />
            <text x={NUM_X + 134} y={NUM_Y + 60} fontFamily={FONT_FAMILY} fontSize={10} fontWeight={700} fill={INK}>
              =
            </text>
            <TweenText
              x={NUM_X + 144}
              y={NUM_Y + 60}
              fontFamily={FONT_FAMILY}
              fontSize={10}
              fontWeight={700}
              fill={INK}
              value={pPlus}
              formatter={fmtPct}
            />
          </PopIn>
        )}
        {showPosterior && (
          <PopIn key="posterior" durationMs={400}>
            <rect
              x={NUM_X - 4}
              y={NUM_Y + 64}
              width={SVG_W - 40}
              height={20}
              fill={ACCENT}
              stroke={INK}
              strokeWidth={1}
            />
            <text x={NUM_X} y={NUM_Y + 78} fontFamily={FONT_FAMILY} fontSize={11} fontWeight={900} fill={INK}>
              P(D | +) = P(D ∩ +) / P(+) =
            </text>
            <TweenText
              x={NUM_X + 152}
              y={NUM_Y + 78}
              fontFamily={FONT_FAMILY}
              fontSize={11}
              fontWeight={900}
              fill={INK}
              value={pDP}
              formatter={fmtPct}
            />
            <text x={NUM_X + 198} y={NUM_Y + 78} fontFamily={FONT_FAMILY} fontSize={11} fontWeight={900} fill={INK}>
              /
            </text>
            <TweenText
              x={NUM_X + 208}
              y={NUM_Y + 78}
              fontFamily={FONT_FAMILY}
              fontSize={11}
              fontWeight={900}
              fill={INK}
              value={pPlus}
              formatter={fmtPct}
            />
            <text x={NUM_X + 254} y={NUM_Y + 78} fontFamily={FONT_FAMILY} fontSize={11} fontWeight={900} fill={INK}>
              =
            </text>
            <TweenText
              x={NUM_X + 264}
              y={NUM_Y + 78}
              fontFamily={FONT_FAMILY}
              fontSize={11}
              fontWeight={900}
              fill={INK}
              value={posterior}
              formatter={fmtPct}
            />
          </PopIn>
        )}
      </AnimatePresence>

      {/* Surprise badge — pops in only on step 6 */}
      <AnimatePresence>
        {step === 6 && (
          <PopIn key="surprise-badge" durationMs={500}>
            <rect
              x={SVG_W - 160}
              y={NUM_Y - 10}
              width={150}
              height={22}
              fill={ACCENT}
              stroke={INK}
              strokeWidth={1.5}
            />
            <text
              x={SVG_W - 85}
              y={NUM_Y + 5}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={10}
              fontWeight={900}
              fill={INK}
            >
              16% despite positive test!
            </text>
          </PopIn>
        )}
      </AnimatePresence>

      {/* Footer message (2-line wrap, cross-fades on change) */}
      <rect x={10} y={MSG_Y - 28} width={460} height={36} fill={PAPER} stroke={INK} strokeWidth={1} />
      <FooterMessage message={message} />

      {/* Bounds anchor */}
      <rect x={0} y={0} width={480} height={360} fill="none" stroke="none" />
    </>
  );
}

function FooterMessage({ message }: { message: string }) {
  const maxCharsPerLine = 78;
  const words = message.split(" ");
  let line1 = "";
  let line2 = "";
  let onLine2 = false;
  for (const w of words) {
    if (!onLine2) {
      if (!line1.length || (line1.length + w.length + 1) <= maxCharsPerLine) {
        line1 = line1 ? `${line1} ${w}` : w;
        continue;
      }
      onLine2 = true;
    }
    if (!line2.length || (line2.length + w.length + 1) <= maxCharsPerLine) {
      line2 = line2 ? `${line2} ${w}` : w;
    } else {
      line2 = line2 + "…";
      break;
    }
  }
  // tspan textContent concatenates without a separator — pad line1 so the
  // last word of line1 stays separated from the first word of line2.
  if (line2) {
    line1 = line1 + " ";
  }
  return (
    <AnimatePresence initial={false}>
      <motion.text
        key={message}
        x={16}
        y={MSG_Y - 12}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: 0.3 }}
      >
        <tspan x={16} dy={0}>
          {line1}
        </tspan>
        {line2 && (
          <tspan x={16} dy={12}>
            {line2}
          </tspan>
        )}
      </motion.text>
    </AnimatePresence>
  );
}

export function BayesTree(): ReactNode {
  return (
    <AlgoStepperShell<BayesState>
      title="PS-2 · Bayes tree + area overlay ⭐"
      desc="Disease/test scenario. Forward tree shows P(D)·P(+|D); area shows joint probabilities; posterior P(D|+) computed via Bayes."
      frames={FRAMES}
      renderFrame={renderFrame}
      testIdPrefix="bayes-tree"
    />
  );
}
