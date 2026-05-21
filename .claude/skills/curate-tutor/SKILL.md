---
name: curate-tutor
description: Use when authoring tutor content from a university lecture (PDF or markdown) into the git-tracked content/ knowledge-concept corpus. Triggers on "curate", "author KCs", "add a lecture to content/", or /curate-tutor.
---

# curate-tutor

Offline content-authoring pipeline. Turns one university lecture into validated,
git-tracked YAML knowledge concepts under `content/{subject}/`. This is the
Gate 3 authoring tool — Claude performs each stage; there is NO Python sidecar
(DSPy/MinerU/PaperQA2 are Gate 5 runtime infrastructure, unrelated to authoring).

## Inputs

- `subject` — one of the ids in `content/subjects.yaml` (PA, PS, POO, ALO, SO-RC).
- `source` — path to the lecture PDF or pre-extracted markdown (e.g.
  `tmp-secondbrain-scrape/_fii/_gdrive/PA_Y1/Curs/curs_2020-2021/Curs 1 PA.pdf`).
- `doc_id` — a short slug for the source (e.g. `pa-lecture-01`).

## Stages

1. **Extract source text.** Read the `source`. If it is a PDF, extract its text
   (use the pre-extracted `.md` sibling when one exists — it is cleaner). Write
   the extracted plain text verbatim to `content/{subject}/_sources/{doc_id}.md`.
   This file is what the validator checks every `quote` against — it MUST be the
   real extracted text, never paraphrased.

2. **KC discovery.** Identify the distinct knowledge concepts the lecture
   teaches. One KC = one assessable idea. For each, draft a `content/{subject}/
   kcs/{kc_id}.yaml` file conforming to the `KnowledgeConcept` schema:
   `id` (`{subject-lower}-kc-NNN`), `subject`, `name_ro`, `name_en`, `cluster`,
   `bloom_level` (remember|understand|apply|analyze|evaluate|create),
   `difficulty` (1-5), `time_minutes`, `exam_weight`, `tier`, `version: 1`.

3. **Prerequisite edges.** Decide which KCs depend on which. Write
   `content/{subject}/edges.yaml` (`EdgesFile` schema): each edge is
   `{kc, prereq, rationale}`. The graph MUST stay acyclic. Every non-tier-1 KC
   MUST be within 8 prerequisite hops of a tier-1 KC.

4. **Misconception mining.** For KCs where the lecture or common student error
   suggests one, draft `content/{subject}/misconceptions/{id}.yaml`
   (`Misconception` schema): `id`, `kc_id`, `label_ro`, `label_en`, `trigger`,
   `refutation`, `version: 1`.

5. **Attribution.** Every KC and misconception MUST carry a non-empty `source:`
   list of `{doc, quote}` entries. `doc` = the `doc_id` from stage 1. `quote` =
   a VERBATIM substring copied from `content/{subject}/_sources/{doc_id}.md` —
   copy-paste it, do not retype or summarize. This is the groundedness anchor.

6. **Exam weight.** Set each KC's `exam_weight` so the subject's KCs sum to
   1.0 (+/- 0.02). If only part of the subject is authored so far, scale the
   weights of the authored KCs to sum to 1.0 and note that re-balancing is
   needed when more lectures are added.

7. **Verbatim self-check.** Before finishing, re-open each KC/misconception and
   confirm every `quote` is a character-for-character substring of the
   `_sources` file. Fix any that are not — a non-verbatim quote is a hallucination.

8. **Validate.** Run `gradle validateContent`. It MUST exit 0. Fix every `ERROR`
   issue (warnings about missing source files are acceptable only if `_sources`
   genuinely was not produced). Re-run until green.

## Output

A set of new/modified files under `content/{subject}/` and a green
`gradle validateContent`. The human curator then reviews the YAML via `git diff`
before committing — that review is Gate 3's groundedness gate (the Gate 4
curator SPA will replace it with a swipe queue).

## Guardrails

- Never invent a concept the lecture does not cover.
- Never write a `quote` that is not in the `_sources` file.
- Keep the graph acyclic; if a prerequisite feels circular, one of the two KCs
  is mis-scoped — split or merge instead of adding the back-edge.
