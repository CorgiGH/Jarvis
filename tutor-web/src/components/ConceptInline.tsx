import { useState } from "react";
import { ConceptDrawer } from "./ConceptDrawer";

/**
 * Phase 7.1: inline-link affordance for assistant-emitted
 * <concept>name</concept> envelopes. Click opens the ConceptDrawer
 * with past gaps + corpus references for the term.
 *
 * Confidence-gating threshold (env JARVIS_CONCEPT_LINK_CONFIDENCE_
 * THRESHOLD) deferred until KnowledgeState confidence query is
 * plumbed through the tutor surface — for now every concept renders
 * as an inline link.
 */
export function ConceptInline({ name }: { name: string }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        data-testid="concept-inline"
        data-concept={name}
        onClick={() => setOpen(true)}
        className="underline decoration-dotted text-page-fg hover:bg-accent-soft"
      >
        {name}
      </button>
      {open && <ConceptDrawer concept={name} onClose={() => setOpen(false)} />}
    </>
  );
}
