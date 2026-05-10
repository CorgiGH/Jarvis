import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { RailDrawer } from "./RailDrawer";
import { PdfPane } from "./PdfPane";
import { Scratchpad } from "./Scratchpad";
import { ConceptDrawer } from "./ConceptDrawer";
import { KnowledgeGapCard } from "./KnowledgeGapCard";
import { jarvisFetch } from "../lib/api";
import type { RailItem } from "../lib/taskPrep";
import type { KnowledgeGap } from "../lib/knowledgeGap";

interface ResourceRailProps {
  taskId: string;
  items: RailItem[];
}

/**
 * Thin adapter: fetches a gap by id from the API and renders
 * KnowledgeGapCard once loaded. Needed because KnowledgeGapCard
 * requires a full KnowledgeGap object, not just an id.
 * Used only in the PRIOR_GAP rail drawer slot.
 */
function PriorGapAdapter({ gapId }: { gapId: string }) {
  const [gap, setGap] = useState<KnowledgeGap | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useState(() => {
    jarvisFetch(`/api/v1/gap/${encodeURIComponent(gapId)}`)
      .then(r => (r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`))))
      .then((data: KnowledgeGap) => setGap(data))
      .catch((e: Error) => setErr(e.message));
  });

  if (err) return <div className="p-4 text-xs text-danger-text">gap load failed: {err}</div>;
  if (!gap) return <div className="p-4 text-xs text-page-fg/60">loading gap…</div>;
  return <KnowledgeGapCard gap={gap} />;
}

/**
 * 320px right-side rail. Renders one button per RailItem.
 * - action='NAVIGATE' → router.navigate(payload.route)
 * - action='OPEN_DRAWER' → mount RailDrawer with type-specific content
 *
 * Drawer-content components are existing Slice 0/1 components.
 *
 * Prop-signature adaptations vs. plan:
 * - ConceptDrawer: plan assumed `conceptId` prop; actual is `concept: string` + `onClose`.
 *   We pass item.label as the concept string and wire onClose to setOpenDrawer(null).
 * - KnowledgeGapCard: requires a full KnowledgeGap object; PRIOR_GAP slot uses
 *   PriorGapAdapter (defined above) to fetch and pass the object.
 */
export function ResourceRail({ taskId, items }: ResourceRailProps) {
  const [openDrawer, setOpenDrawer] = useState<RailItem | null>(null);
  const [scratch, setScratch] = useState<string>("");
  const navigate = useNavigate();

  function handleClick(item: RailItem) {
    if (item.action === "NAVIGATE") {
      const route = (item.payload.route as string) || "/";
      navigate(route);
      return;
    }
    setOpenDrawer(item);
  }

  function renderDrawerContent(item: RailItem) {
    switch (item.type) {
      case "PDF": {
        const path = (item.payload.path as string) || "";
        return (
          <PdfPane
            url={`/static/${path}`}
            uploadUrl={`/static/${path}`}
            onPdfSelectionGap={async () => {}}
          />
        );
      }
      case "SCRATCHPAD": {
        // Load from server on first open; persist via the existing PUT route.
        if (scratch === "" && taskId) {
          jarvisFetch(`/api/v1/tasks/${encodeURIComponent(taskId)}/scratchpad`)
            .then(r => r.ok ? r.json() : null)
            .then((data: { text?: string } | null) => {
              if (data?.text != null) setScratch(data.text);
            })
            .catch(() => { /* tolerate */ });
        }
        return (
          <Scratchpad
            value={scratch}
            onChange={(next: string) => {
              setScratch(next);
              jarvisFetch(`/api/v1/tasks/${encodeURIComponent(taskId)}/scratchpad`, {
                method: "PUT",
                body: JSON.stringify({ text: next }),
              }).catch(() => {});
            }}
          />
        );
      }
      case "CONCEPT": {
        // ConceptDrawer takes `concept: string` (the term name) + `onClose`.
        // We use item.label as the concept string since that's what the user
        // should see; for richer concept-id lookup, Slice 2 can add a route.
        const conceptTerm = (item.payload.conceptId as string) || item.label;
        return (
          <ConceptDrawer
            concept={conceptTerm}
            onClose={() => setOpenDrawer(null)}
          />
        );
      }
      case "PRIOR_GAP": {
        // KnowledgeGapCard requires a full KnowledgeGap object; delegate
        // fetch + render to PriorGapAdapter.
        const gapId = (item.payload.gapId as string) || "";
        return <PriorGapAdapter gapId={gapId} />;
      }
      case "FSRS_DUE":
        // Should not reach here — NAVIGATE action is handled above.
        return null;
      default:
        return null;
    }
  }

  return (
    <>
      <aside
        data-testid="resource-rail"
        className="w-[320px] shrink-0 border-l-4 border-border-strong bg-page-bg flex flex-col font-mono text-xs"
      >
        {items.map((item, i) => (
          <button
            key={`${item.type}-${i}`}
            data-testid={`rail-item-${item.type}`}
            onClick={() => handleClick(item)}
            className="text-left px-3 py-2 border-b-2 border-border-thin hover:bg-accent-soft focus:bg-accent-soft focus:outline-none"
          >
            <span className="block text-[10px] tracking-widest text-page-fg/60">{item.type}</span>
            <span className="block text-sm">{item.label}</span>
          </button>
        ))}
      </aside>
      {openDrawer && (
        <RailDrawer item={openDrawer} onClose={() => setOpenDrawer(null)}>
          {renderDrawerContent(openDrawer)}
        </RailDrawer>
      )}
    </>
  );
}
