import type { ReactNode } from "react";
import { RecursionTree } from "./RecursionTree";

/**
 * E3 routing: viz id → component. The key set MUST equal content/viz-ids.yaml
 * (enforced by vizRegistry.test.ts). Add an entry here AND in content/viz-ids.yaml
 * together. Most components are zero-prop concept-level illustrations.
 */
export const vizRegistry: Record<string, () => ReactNode> = {
  "recursion-tree": RecursionTree,
};
