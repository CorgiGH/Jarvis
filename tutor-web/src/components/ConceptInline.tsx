import { useEffect, useState } from "react";
import { jarvisFetch } from "../lib/api";
import { ConceptDrawer } from "./ConceptDrawer";

/**
 * Phase 7.1: inline-link affordance for assistant-emitted
 * <concept>name</concept> envelopes. Click opens the ConceptDrawer
 * with past gaps + corpus references for the term.
 *
 * Confidence-gating (server-driven via env JARVIS_CONCEPT_LINK_
 * CONFIDENCE_THRESHOLD, default 0.7): on mount, GET /api/v1/concept-
 * confidence?name=... If `linked` is false (concept is well-known to
 * the user), render plain text — affordance only surfaces for weak/
 * unknown concepts. Per [[Progressive Disclosure]] +
 * [[Recognition Over Recall]].
 *
 * Failure mode: if the lookup fails or is in flight, default to
 * showing the link — better to over-surface than hide context.
 */
export function ConceptInline({ name }: { name: string }) {
  const [open, setOpen] = useState(false);
  const [linked, setLinked] = useState(true);
  useEffect(() => {
    let cancelled = false;
    jarvisFetch(`/api/v1/concept-confidence?name=${encodeURIComponent(name)}`)
      .then(r => r.ok ? r.json() : null)
      .then((data: { linked?: boolean } | null) => {
        if (cancelled || !data) return;
        if (data.linked === false) setLinked(false);
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [name]);

  if (!linked) {
    return <span data-testid="concept-plain" data-concept={name}>{name}</span>;
  }
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
