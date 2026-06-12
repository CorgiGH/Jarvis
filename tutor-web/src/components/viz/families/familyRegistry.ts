import type { ReactNode } from "react";
import { GraphTreeFamily } from "./GraphTreeFamily";

/**
 * Plan-3 §5.1/§5.3 — a family renders (family_id, instance_data) and NOTHING else. It receives the
 * instance id (for error messages), the raw data_json (the family owns parsing/validation), and the
 * instance language (callouts are instance data, language-keyed). Playback is the shell's, not the
 * family's (§5.3) — the family only supplies frames + a renderFrame.
 *
 * This is a NEW channel (FigureBinding, spec §3.2). The legacy vizRegistry.ts / content/viz-ids.yaml /
 * Kotlin checkVizReferences lockstep is UNTOUCHED (plan core §0.5) — it dies with the V6 retirement.
 */
export type FamilyRendererProps = {
  instanceId: string;
  dataJson: string;
  language: string;
  /** RO chrome strings on the lesson surface; omitted → the shell's EN demo defaults. */
  labels?: import("../AlgoStepperShell").ShellLabels;
};
export type FamilyRenderer = (props: FamilyRendererProps) => ReactNode;

export const familyRegistry: Record<string, FamilyRenderer> = {
  "graph-tree": GraphTreeFamily,
};
