export interface ConceptRef { name: string; raw: string; }

const ENVELOPE = /<concept>([^<]+)<\/concept>/g;

/**
 * Phase 7.1: parses <concept>name</concept> envelopes out of an
 * assistant reply. Returns the body with envelopes replaced by
 * sentinels (CONCEPT0, CONCEPT1, ...) plus the list of concept refs
 * in order. ChatPane swaps each sentinel for a <ConceptInline> at
 * render time.
 */
export function parseConcepts(text: string): {
  body: string;
  concepts: ConceptRef[];
} {
  const concepts: ConceptRef[] = [];
  let i = 0;
  const body = text.replace(ENVELOPE, (raw, name: string) => {
    concepts.push({ name: name.trim(), raw });
    return `CONCEPT${i++}`;
  });
  return { body, concepts };
}
