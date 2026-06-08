/**
 * InvariantRule — surface 0e.
 * 4px accent blockquote for the invariant statement.
 * Renders nothing when body is null/empty/blank.
 *
 * testids: invariant-rule · invariant-rule-body
 */

interface InvariantRuleProps {
  /** The invariant statement body (RO). Null/empty → render nothing. */
  body: string | null | undefined;
}

export function InvariantRule({ body }: InvariantRuleProps) {
  if (!body || !body.trim()) return null;

  return (
    <div
      data-testid="invariant-rule"
      style={{
        borderLeft: "4px solid var(--color-accent, #e5c04a)",
        paddingLeft: "16px",
        margin: "12px 0",
      }}
    >
      <p
        data-testid="invariant-rule-body"
        className="font-mono text-xs text-page-fg/90 tracking-wide leading-relaxed"
      >
        {body}
      </p>
    </div>
  );
}
