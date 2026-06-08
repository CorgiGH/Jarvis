import { jarvisFetch } from "./api";
import type { DrillProvenance } from "../components/DrillStack";
import type { VerificationStatus, SourceRef } from "./drillGrader";

/** Wire reply for GET /api/v1/verify/{kcId}/status — grep `data class ApiVerifyStatusReply` in TrustRoutes.kt.
 *  badge_text is PINNED server-side — never "verified correct". */
export interface CitedClaim {
  claimKind: "DEFINITION" | "INVARIANT" | "GRADER_RULE" | "MISCONCEPTION_REFUTATION" | "STEM";
  status: VerificationStatus;
  source: SourceRef;
}
export type HonestFloor = "FAITHFUL_TO_SOURCE" | "UNVERIFIED";
export interface ApiVerifyStatusReply {
  verification_status: VerificationStatus;
  badge_text: string;
  claims: CitedClaim[];
  honest_floor: HonestFloor;
}

/** Wire reply for GET /api/v1/teaching/{kcId} (sibling backend plan Task 7).
 *  FAIL-LOUD: 404 when the KC is non-faithful / disputed / unknown. */
export interface ApiTeachingReply {
  kcId: string;
  name_ro: string;
  explanation_ro: string | null;
  worked_example_ro: string | null;
  provenance: DrillProvenance; // always {type:"authored", hasBeenFaithfulChecked:true}
}

/** GET /api/v1/verify/{kcId}/status. Returns null on non-2xx (badge falls closed to unverified). */
export async function getVerifyStatus(kcId: string): Promise<ApiVerifyStatusReply | null> {
  const res = await jarvisFetch(`/api/v1/verify/${encodeURIComponent(kcId)}/status`);
  if (!res.ok) return null;
  return res.json() as Promise<ApiVerifyStatusReply>;
}

/** GET /api/v1/teaching/{kcId}. Returns null on 404 (non-faithful/disputed FAIL-LOUD gate). */
export async function getTeaching(kcId: string): Promise<ApiTeachingReply | null> {
  const res = await jarvisFetch(`/api/v1/teaching/${encodeURIComponent(kcId)}`);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`getTeaching ${res.status}: ${await res.text().catch(() => "")}`);
  return res.json() as Promise<ApiTeachingReply>;
}
