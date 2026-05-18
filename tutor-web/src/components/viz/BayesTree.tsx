import type { ReactNode } from "react";
import { AlgoStepperShell, type Frame } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";

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
  frames.push(mk(2, 0.01, true, false, false, false, "Joint P(D ∩ +) = P(D) \xd7 P(+|D) = 0.01 \xd7 0.95 = 0.0095.", "DP"));
  // Step 3: joint HP
  frames.push(mk(3, 0.01, true, true, false, false, "Joint P(H ∩ +) = P(H) \xd7 P(+|H) = 0.99 \xd7 0.05 = 0.0495.", "HP"));
  // Step 4: total positive
  frames.push(mk(4, 0.01, true, true, true, false, "Total P(+) = 0.0095 + 0.0495 = 0.0590.", "ALL"));
  // Step 5: posterior
  frames.push(mk(5, 0.01, true, true, true, true, "Bayes: P(D|+) = P(D ∩ +) / P(+) = 0.0095 / 0.0590 ≈ 0.161 (16.1%)."));
  // Step 6: surprise
  frames.push(mk(6, 0.01, true, true, true, true, "Surprise: a POSITIVE test only gives 16% chance of disease — base rate dominates!"));
  // Step 7: prior 0.10
  frames.push(mk(7, 0.10, true, true, true, true, "If prior were P(D)=0.10 (less rare), posterior jumps to ~0.679 (67.9%)."));
  // Step 8: prior 0.50
  frames.push(mk(8, 0.50, true, true, true, true, "If prior P(D)=0.50, posterior ≈ 0.950. Strong evidence \xd7 moderate prior."));
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
const TREE_W = 230;
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

function pct(x: number): string {
  return (x * 100).toFixed(2) + "%";
}

function renderFrame(frame: Frame<BayesState>): ReactNode {
  const {
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

  // Tree layout
  const rootX = TREE_X + 20;
  const branch1Y = TREE_Y + 80;
  const branch2Y = TREE_Y + 160;
  const rootY = (branch1Y + branch2Y) / 2;
  const branchX1 = TREE_X + 120;
  const leafX = TREE_X + 200;

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

      {/* Root node */}
      <circle cx={rootX} cy={rootY} r={5} fill={INK} />
      {/* Branches to D / H */}
      <line x1={rootX} y1={rootY} x2={branchX1} y2={branch1Y} stroke={INK} strokeWidth={1.5} />
      <line x1={rootX} y1={rootY} x2={branchX1} y2={branch2Y} stroke={INK} strokeWidth={1.5} />
      {/* D node */}
      <circle cx={branchX1} cy={branch1Y} r={5} fill={ACCENT} stroke={INK} strokeWidth={1} />
      <text
        x={branchX1 + 8}
        y={branch1Y + 4}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fontWeight={700}
        fill={INK}
      >
        D \xb7 P={pct(prior)}
      </text>
      {/* H node */}
      <circle cx={branchX1} cy={branch2Y} r={5} fill="#fff" stroke={INK} strokeWidth={1} />
      <text
        x={branchX1 + 8}
        y={branch2Y + 4}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fill={INK}
      >
        H \xb7 P={pct(pH)}
      </text>

      {/* Sub-branches: D → +/-, H → +/- */}
      <line x1={branchX1} y1={branch1Y} x2={leafX} y2={branch1Y - 18} stroke={INK} strokeWidth={1} opacity={0.6} />
      <line x1={branchX1} y1={branch1Y} x2={leafX} y2={branch1Y + 14} stroke={INK} strokeWidth={1} opacity={0.6} />
      <line x1={branchX1} y1={branch2Y} x2={leafX} y2={branch2Y - 18} stroke={INK} strokeWidth={1} opacity={0.6} />
      <line x1={branchX1} y1={branch2Y} x2={leafX} y2={branch2Y + 14} stroke={INK} strokeWidth={1} opacity={0.6} />
      <text x={leafX + 4} y={branch1Y - 14} fontFamily={FONT_FAMILY} fontSize={9} fill={INK}>
        + \xb7 {pct(sensitivity)}
      </text>
      <text x={leafX + 4} y={branch1Y + 18} fontFamily={FONT_FAMILY} fontSize={9} fill={INK} opacity={0.5}>
        − \xb7 {pct(1 - sensitivity)}
      </text>
      <text x={leafX + 4} y={branch2Y - 14} fontFamily={FONT_FAMILY} fontSize={9} fill={INK}>
        + \xb7 {pct(fpr)}
      </text>
      <text x={leafX + 4} y={branch2Y + 18} fontFamily={FONT_FAMILY} fontSize={9} fill={INK} opacity={0.5}>
        − \xb7 {pct(1 - fpr)}
      </text>

      {/* === AREA PANE === */}
      {/* 100\xd7100 unit square; map disease/healthy split + positive/negative */}
      <rect x={AREA_X} y={AREA_Y} width={AREA_W} height={AREA_H} fill={PAPER} stroke={INK} strokeWidth={1} />

      {/* Disease ∩ positive (top-left of area) */}
      <rect
        x={AREA_X}
        y={AREA_Y}
        width={AREA_W * prior}
        height={AREA_H * sensitivity}
        fill={ACCENT}
        stroke={INK}
        strokeWidth={highlightArea === "DP" || highlightArea === "ALL" ? 2 : 0.5}
        opacity={showJointDP ? 1 : 0.15}
      />
      {/* Disease ∩ negative (bottom-left) */}
      <rect
        x={AREA_X}
        y={AREA_Y + AREA_H * sensitivity}
        width={AREA_W * prior}
        height={AREA_H * (1 - sensitivity)}
        fill={ACCENT}
        stroke={INK}
        strokeWidth={highlightArea === "DN" ? 2 : 0.5}
        opacity={0.2}
      />
      {/* Healthy ∩ positive (top-right) */}
      <rect
        x={AREA_X + AREA_W * prior}
        y={AREA_Y}
        width={AREA_W * pH}
        height={AREA_H * fpr}
        fill={INK}
        stroke={INK}
        strokeWidth={highlightArea === "HP" || highlightArea === "ALL" ? 2 : 0.5}
        opacity={showJointHP ? 0.8 : 0.15}
      />
      {/* Healthy ∩ negative (bottom-right) */}
      <rect
        x={AREA_X + AREA_W * prior}
        y={AREA_Y + AREA_H * fpr}
        width={AREA_W * pH}
        height={AREA_H * (1 - fpr)}
        fill="#fff"
        stroke={INK}
        strokeWidth={highlightArea === "HN" ? 2 : 0.5}
      />

      {/* Labels on area */}
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
        x={AREA_X + AREA_W * prior + 8}
        y={AREA_Y + (AREA_H * fpr) / 2 + 6}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={"#fff"}
      >
        H ∩ +
      </text>
      <text
        x={AREA_X + AREA_W * prior + 8}
        y={AREA_Y + AREA_H * (fpr + (1 - fpr) / 2) + 4}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fill={INK}
        opacity={0.6}
      >
        H ∩ −
      </text>

      {/* === NUMERIC PANE === */}
      <text x={NUM_X} y={NUM_Y + 12} fontFamily={FONT_FAMILY} fontSize={10} fill={INK}>
        P(D) = {pct(prior)} \xb7 P(+|D) = {pct(sensitivity)} \xb7 P(+|H) = {pct(fpr)}
      </text>
      {showJointDP && (
        <text x={NUM_X} y={NUM_Y + 28} fontFamily={FONT_FAMILY} fontSize={10} fontWeight={700} fill={INK}>
          P(D ∩ +) = {pct(prior)} \xd7 {pct(sensitivity)} = {pct(pDP)}
        </text>
      )}
      {showJointHP && (
        <text x={NUM_X} y={NUM_Y + 44} fontFamily={FONT_FAMILY} fontSize={10} fontWeight={700} fill={INK}>
          P(H ∩ +) = {pct(pH)} \xd7 {pct(fpr)} = {pct(pHP)}
        </text>
      )}
      {showTotal && (
        <text x={NUM_X} y={NUM_Y + 60} fontFamily={FONT_FAMILY} fontSize={10} fontWeight={700} fill={INK}>
          P(+) = {pct(pDP)} + {pct(pHP)} = {pct(pPlus)}
        </text>
      )}
      {showPosterior && (
        <g>
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
            P(D | +) = P(D ∩ +) / P(+) = {pct(pDP)} / {pct(pPlus)} = {pct(posterior)}
          </text>
        </g>
      )}

      {/* Message */}
      <text x={NUM_X} y={MSG_Y} fontFamily={FONT_FAMILY} fontSize={10} fontWeight={700} fill={INK}>
        {message}
      </text>

      {/* Bounds anchor */}
      <rect x={0} y={0} width={480} height={360} fill="none" stroke="none" />
    </>
  );
}

export function BayesTree(): ReactNode {
  return (
    <AlgoStepperShell<BayesState>
      title="PS-2 \xb7 Bayes tree + area overlay ⭐"
      desc="Disease/test scenario. Forward tree shows P(D)\xb7P(+|D); area shows joint probabilities; posterior P(D|+) computed via Bayes."
      frames={FRAMES}
      renderFrame={renderFrame}
      testIdPrefix="bayes-tree"
    />
  );
}
