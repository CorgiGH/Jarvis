/** SubjectMap — /subjects route screen (surface 0b).
 *
 *  Fetches GET /api/v1/mastery and renders one SubjectCard per subject.
 *  Layout: ASYMMETRIC (R6 equal-column exception PARKED per plan).
 *  Each card's avg EWMA = mean of kc.ewma_score across all KCs.
 *  Retention gaps = KCs with ewma_score < 0.3 (low band threshold).
 *
 *  testids:
 *   subject-map
 *   subject-card-{subjectId}         — one per subject (from SubjectCard)
 *   retention-gap-badge-{subjectId}  — when retention gaps exist
 *   subject-map-empty                — zero subjects
 *   subject-map-error                — fetch failed
 */
import { useEffect, useState } from "react";
import { jarvisFetch } from "../lib/api";
import { SubjectCard } from "./SubjectCard";

interface ApiMasteryKc {
  kc_id: string;
  ewma_score: number;
  observations: number;
  verification_status: string;
}

interface ApiMasterySubject {
  subject_id: string;
  subject_name_ro: string;
  subject_name_en: string;
  kcs: ApiMasteryKc[];
}

const RETENTION_GAP_THRESHOLD = 0.3;
const MASTERED_THRESHOLD = 0.8;

function avgEwma(kcs: ApiMasteryKc[]): number {
  if (kcs.length === 0) return 0;
  return kcs.reduce((s, k) => s + k.ewma_score, 0) / kcs.length;
}

function retentionGaps(kcs: ApiMasteryKc[]): number {
  return kcs.filter((k) => k.ewma_score < RETENTION_GAP_THRESHOLD && k.observations > 0).length;
}

function masteredCount(kcs: ApiMasteryKc[]): number {
  return kcs.filter((k) => k.ewma_score >= MASTERED_THRESHOLD).length;
}

type State =
  | { status: "loading" }
  | { status: "ok"; subjects: ApiMasterySubject[] }
  | { status: "empty" }
  | { status: "error"; message: string };

export function SubjectMap() {
  const [state, setState] = useState<State>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;
    jarvisFetch("/api/v1/mastery")
      .then((r) => {
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json() as Promise<{ subjects: ApiMasterySubject[] }>;
      })
      .then((data) => {
        if (cancelled) return;
        if (!data.subjects || data.subjects.length === 0) {
          setState({ status: "empty" });
        } else {
          setState({ status: "ok", subjects: data.subjects });
        }
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setState({
          status: "error",
          message: err instanceof Error ? err.message : String(err),
        });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div
      data-testid="subject-map"
      className="h-full overflow-y-auto p-6 font-mono"
    >
      {/* Header */}
      <div className="mb-6 border-b border-border-strong pb-3">
        <h1 className="text-xs font-bold tracking-widest uppercase text-page-fg">
          Materii
        </h1>
      </div>

      {state.status === "loading" && (
        <div className="text-xs text-page-fg/50 tracking-widest">
          Se încarcă materiile…
        </div>
      )}

      {state.status === "empty" && (
        <div
          data-testid="subject-map-empty"
          className="text-xs text-page-fg/60 tracking-widest"
        >
          Nicio materie disponibilă.
        </div>
      )}

      {state.status === "error" && (
        <div
          data-testid="subject-map-error"
          className="text-xs text-red-400 tracking-widest"
        >
          Eroare la încărcarea materiilor: {state.message}
        </div>
      )}

      {state.status === "ok" && (
        <div className="flex flex-col gap-3 max-w-2xl">
          {state.subjects.map((sub) => (
            <SubjectCard
              key={sub.subject_id}
              subjectId={sub.subject_id}
              subjectNameRo={sub.subject_name_ro}
              subjectNameEn={sub.subject_name_en}
              kcCount={sub.kcs.length}
              masteredCount={masteredCount(sub.kcs)}
              avgEwma={avgEwma(sub.kcs)}
              retentionGapCount={retentionGaps(sub.kcs)}
            />
          ))}
        </div>
      )}
    </div>
  );
}
