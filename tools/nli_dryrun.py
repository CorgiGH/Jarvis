"""
B5 dry-run (council-1780600422, Path B) — measure the would-be NLI faithful-rate
over the REAL PA corpus, OFFLINE, with NO wiring/gate/DB writes.

Mirrors ContentReconcile.claimsFor:
  DEFINITION   -> one per source ref, content = ref.quote
  INVARIANT    -> one when invariant!=null, content = invariant
  GRADER_RULE  -> one per grader_rules entry, content = the rule
NLI pair (mirrors TwoFamilyDeriver.buildPrompt): premise = source.quote, hypothesis = content.

The trust-net faithful path needs the NLI/LLM family to say SUPPORTED on EVERY claim of a KC
(plus non-LLM pass + round-trip). So a KC is "NLI-faithful-eligible" iff ALL its claims -> SUPPORTED.
We report that, split by KC kind (has-span / math-invariant vs prose-only).
"""
import sys, glob, os
import yaml
import torch
from transformers import AutoTokenizer, AutoModelForSequenceClassification

MODEL = "MoritzLaurer/DeBERTa-v3-base-mnli-fever-anli"
ABSTAIN_FLOOR = 0.60
KC_DIR = "content/PA/kcs"

def verdict(probs, id2label):
    top = int(torch.argmax(probs)); lab = id2label[top].lower(); p = float(probs[top])
    if p < ABSTAIN_FLOOR: return "UNCLEAR", p
    if "entail" in lab: return "SUPPORTED", p
    if "contradict" in lab: return "REFUTED", p
    return "UNCLEAR", p

def claims_for(kc):
    """(kind, content, premise_quote) tuples, mirroring ContentReconcile.claimsFor."""
    out = []
    srcs = kc.get("source") or []
    anchor = srcs[0]["quote"] if srcs else ""
    for ref in srcs:                                   # DEFINITION: content == quote (self-pair)
        out.append(("DEFINITION", ref["quote"], ref["quote"]))
    inv = kc.get("invariant")
    if inv:                                            # INVARIANT: hypothesis = bare equation
        out.append(("INVARIANT", inv, anchor))
    for rule in (kc.get("grader_rules") or []):        # GRADER_RULE: hypothesis = rule text
        out.append(("GRADER_RULE", rule, anchor))
    return out

def main():
    files = sorted(f for f in glob.glob(os.path.join(KC_DIR, "*.yaml")) if "fixture" not in f)
    print(f"loading {MODEL}...", flush=True)
    tok = AutoTokenizer.from_pretrained(MODEL)
    model = AutoModelForSequenceClassification.from_pretrained(MODEL); model.eval()
    id2label = model.config.id2label
    print(f"corpus: {len(files)} real PA KCs\n")

    corpus = []
    for fp in files:
        kc = yaml.safe_load(open(fp, encoding="utf-8"))
        has_span = any("span" in (r or {}) for r in (kc.get("source") or []))
        is_math = kc.get("invariant") is not None
        rows = []
        for kind, content, premise in claims_for(kc):
            inputs = tok(premise, content, return_tensors="pt", truncation=True)
            with torch.no_grad():
                probs = torch.softmax(model(**inputs).logits[0], dim=-1)
            v, p = verdict(probs, id2label)
            rows.append((kind, v, p, content))
        all_supported = bool(rows) and all(v == "SUPPORTED" for _, v, _, _ in rows)
        corpus.append((kc["id"], has_span, is_math, all_supported, rows))

        tag = ("math/span" if (is_math and has_span) else "span" if has_span else "no-span")
        flag = "FAITHFUL-ELIGIBLE" if (all_supported and has_span) else "capped->uncertain"
        print(f"=== {kc['id']}  [{tag}]  NLI-leg: {flag} ===")
        for kind, v, p, content in rows:
            mark = "  " if v == "SUPPORTED" else ">>"
            print(f"  {mark} {kind:<11} {v:<10} p={p:.2f} | {content[:70].replace(chr(10),' ')}")
        print()

    elig = [c for c in corpus if c[3] and c[1]]
    math = [c for c in corpus if c[2]]
    math_elig = [c for c in math if c[3] and c[1]]
    print("================ DRY-RUN SUMMARY ================")
    print(f"real KCs:                 {len(corpus)}")
    print(f"NLI-faithful-eligible:    {len(elig)}/{len(corpus)}  ({[c[0] for c in elig]})")
    print(f"math/invariant KCs:       {len(math)}  ({[c[0] for c in math]})")
    print(f"  of those, NLI-eligible: {len(math_elig)}/{len(math)}  ({[c[0] for c in math_elig]})")
    print("note: 'eligible' = NLI says SUPPORTED on ALL the KC's claims AND the KC has a span.")
    print("      The bare-equation INVARIANT claim is the usual capper (prose quote !=> '1+1+1=3').")

if __name__ == "__main__":
    main()
