"""
B5 spike — prove a LOCAL NLI model runs on this box and gives a sane
3-band verdict (SUPPORTED / REFUTED / UNCLEAR) on a real PA KC.

Throwaway. NOT the production leg. Goal: kill the "does the PC-side NLI
topology hold at all" unknown before building the B5 contract/leg/gate.

Model: MoritzLaurer/DeBERTa-v3-base-mnli-fever-anli  (~184M, 3-class NLI,
CPU-feasible, ~0.5GB). DeBERTa-v3 family = the locked D6 pick. MiniCheck
(binary support-prob) is the B5-proper alternative to bench against a gold set.

Verdict contract (spike form):
  argmax == entailment    -> SUPPORTED
  argmax == contradiction -> REFUTED
  argmax == neutral  OR  top_prob < ABSTAIN_FLOOR  -> UNCLEAR  (load-bearing)
"""
import sys
import torch
from transformers import AutoTokenizer, AutoModelForSequenceClassification

MODEL = "MoritzLaurer/DeBERTa-v3-base-mnli-fever-anli"
ABSTAIN_FLOOR = 0.60  # top class must clear this or we abstain to UNCLEAR

# premise = the real lecture span from content/PA/_sources/pa-lecture-01.md:82-83
PREMISE = (
    "An algorithm is a well-ordered collection of unambiguous and effectively "
    "computable operations that when executed produces a result and halts in a "
    "finite amount of time."
)

CASES = [
    ("SUPPORTED", "An algorithm halts in a finite amount of time."),
    ("REFUTED",   "An algorithm may run forever and never halt."),
    ("UNCLEAR",   "An algorithm is always written in the Python programming language."),
]


def verdict(probs, id2label):
    top_id = int(torch.argmax(probs))
    top_label = id2label[top_id].lower()
    top_prob = float(probs[top_id])
    if top_prob < ABSTAIN_FLOOR:
        return "UNCLEAR", top_label, top_prob
    if "entail" in top_label:
        return "SUPPORTED", top_label, top_prob
    if "contradict" in top_label:
        return "REFUTED", top_label, top_prob
    return "UNCLEAR", top_label, top_prob  # neutral


def stdin_mode():
    """Read premise line + hypothesis line from stdin, print one tab line:
    `SUPPORTED|REFUTED|UNCLEAR\\t<prob>`. Mirrors the SymPy seam's stdin protocol
    so a JVM ProcessBuilder can drive it. ran-fail prints `ERR\\t<msg>`."""
    import io
    try:
        tok = AutoTokenizer.from_pretrained(MODEL)
        model = AutoModelForSequenceClassification.from_pretrained(MODEL)
        model.eval()
        id2label = model.config.id2label
        premise = sys.stdin.readline().rstrip("\n")
        hypothesis = sys.stdin.readline().rstrip("\n")
        inputs = tok(premise, hypothesis, return_tensors="pt", truncation=True)
        with torch.no_grad():
            probs = torch.softmax(model(**inputs).logits[0], dim=-1)
        got, _raw, p = verdict(probs, id2label)
        sys.stdout.write(f"{got}\t{p:.4f}\n")
    except Exception as e:
        sys.stdout.write("ERR\t" + str(e).replace("\t", " ").replace("\n", " ") + "\n")


def main():
    if "--stdin" in sys.argv:
        stdin_mode()
        return
    print(f"loading {MODEL} (first run downloads ~0.5GB to HF cache)...", flush=True)
    tok = AutoTokenizer.from_pretrained(MODEL)
    model = AutoModelForSequenceClassification.from_pretrained(MODEL)
    model.eval()
    id2label = model.config.id2label
    print(f"loaded. labels={id2label}\n")
    print(f"PREMISE: {PREMISE}\n")

    ok = True
    for expected, hypothesis in CASES:
        inputs = tok(PREMISE, hypothesis, return_tensors="pt", truncation=True)
        with torch.no_grad():
            logits = model(**inputs).logits[0]
        probs = torch.softmax(logits, dim=-1)
        got, raw, p = verdict(probs, id2label)
        mark = "OK " if got == expected else "XX "
        if got != expected:
            ok = False
        print(f"{mark}expected={expected:<10} got={got:<10} "
              f"(raw={raw}, p={p:.2f})  | {hypothesis}")

    print("\nSPIKE", "GREEN — 3-band verdict works" if ok else "RED — verdicts off, inspect")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
