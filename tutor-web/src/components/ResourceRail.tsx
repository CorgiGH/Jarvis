import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { RailDrawer } from "./RailDrawer";
import { PdfPane } from "./PdfPane";
import { ScratchpadDrawer } from "./ScratchpadDrawer";
import { ConceptDrawer } from "./ConceptDrawer";
import { KnowledgeGapCard } from "./KnowledgeGapCard";
import { jarvisFetch } from "../lib/api";
import type { RailItem } from "../lib/taskPrep";
import type { KnowledgeGap } from "../lib/knowledgeGap";

interface ResourceRailProps {
  taskId: string;
  items: RailItem[];
  /** When set, the rail immediately opens the drawer for this archival path.
   *  If the path matches a known rail item it uses that item; otherwise a
   *  transient CONCEPT drawer-only item is synthesised (not shown in the rail list). */
  forceOpenPath?: string | null;
  /** Fired when the drawer opened via forceOpenPath is closed. */
  onDrawerClosed?: () => void;
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
  if (!gap) return <div className="p-4 text-xs text-page-fg/80">loading gap…</div>;
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
export function ResourceRail({ taskId, items, forceOpenPath, onDrawerClosed }: ResourceRailProps) {
  const [openDrawer, setOpenDrawer] = useState<RailItem | null>(null);
  // Tracks whether the current openDrawer was triggered by forceOpenPath,
  // so we know to fire onDrawerClosed when it's closed.
  const [forcedOpen, setForcedOpen] = useState(false);
  const navigate = useNavigate();

  // When forceOpenPath changes to a non-null value, open the matching drawer.
  useEffect(() => {
    if (!forceOpenPath) return;
    // Find the matching rail item by path in payload
    const match = items.find(
      (it) => (it.payload.path as string | undefined) === forceOpenPath,
    );
    if (match) {
      setOpenDrawer(match);
    } else {
      // Synthesise a transient CONCEPT drawer item — NOT added to the rail list.
      const basename = forceOpenPath.split("/").pop() ?? forceOpenPath;
      const synthetic: RailItem = {
        type: "CONCEPT",
        label: basename,
        action: "OPEN_DRAWER",
        payload: { path: forceOpenPath, conceptId: basename },
      };
      setOpenDrawer(synthetic);
    }
    setForcedOpen(true);
  }, [forceOpenPath]); // items intentionally omitted — only re-run when the path itself changes

  function closeDrawer() {
    setOpenDrawer(null);
    if (forcedOpen) {
      setForcedOpen(false);
      onDrawerClosed?.();
    }
  }

  function handleClick(item: RailItem) {
    if (item.action === "NAVIGATE") {
      const route = (item.payload.route as string) || "/";
      navigate(route);
      return;
    }
    setForcedOpen(false);
    setOpenDrawer(item);
  }

  function renderDrawerContent(item: RailItem) {
    switch (item.type) {
      case "PDF": {
        const pdfUrl = `/api/v1/tasks/${encodeURIComponent(taskId)}/pdf`;
        return (
          <PdfPane
            url={pdfUrl}
            uploadUrl={pdfUrl}
            onPdfSelectionGap={async () => {}}
          />
        );
      }
      case "SCRATCHPAD": {
        // ScratchpadDrawer owns hydration ref + debounced PUT + status pill.
        return <ScratchpadDrawer taskId={taskId} />;
      }
      case "CONCEPT": {
        // ConceptDrawer takes `concept: string` (the term name) + `onClose`.
        // We use item.label as the concept string since that's what the user
        // should see; for richer concept-id lookup, Slice 2 can add a route.
        const conceptTerm = (item.payload.conceptId as string) || item.label;
        return (
          <ConceptDrawer
            concept={conceptTerm}
            onClose={closeDrawer}
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
            <span className="block text-[10px] tracking-widest text-page-fg/80">{item.type}</span>
            <span className="block text-sm">{item.label}</span>
          </button>
        ))}
      </aside>
      {openDrawer && (
        <RailDrawer item={openDrawer} onClose={closeDrawer}>
          {renderDrawerContent(openDrawer)}
        </RailDrawer>
      )}
    </>
  );
}
