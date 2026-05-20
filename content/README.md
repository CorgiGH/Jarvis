# content/

Git-tracked knowledge-concept corpus — the source of truth for tutor content
(redesign spec §13). Authored by the `/curate-tutor` skill, validated by
`gradle validateContent`.

Layout: `content/subjects.yaml` (manifest) + `content/{subject}/` with
`kcs/*.yaml`, `edges.yaml`, `edges.mmd` (auto-generated), `misconceptions/*.yaml`,
`_sources/*.md` (extracted source text the validator checks `quote`s against).

Curator HTTP routes are read-only in Gate 3; the write path ships in Gate 4.
