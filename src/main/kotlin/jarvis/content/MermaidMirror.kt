package jarvis.content

/**
 * Renders an [EdgesFile] as a Mermaid flowchart — the auto-generated `edges.mmd`
 * mirror of `edges.yaml` (spec §13). edges.mmd makes prerequisite-graph diffs
 * human-readable in git; it is generated, never hand-edited.
 */
object MermaidMirror {
    fun render(edges: EdgesFile): String = buildString {
        appendLine("flowchart TD")
        append("    %% AUTO-GENERATED from edges.yaml for subject ${edges.subject} — do not edit by hand")
        for (e in edges.edges.sortedWith(compareBy({ it.prereq }, { it.kc }))) {
            append("\n    ${e.prereq} --> ${e.kc}")
        }
    }
}
