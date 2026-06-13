# PA NP-Complete Course Notes Extract — Proof of HAM-PATH ∈ NP

**Origin:** `C:\Users\User\Desktop\PA\NP_Complete_Course_Notes.md` (local file, PA course notes UAIC 2022-2023 based)
**Section:** Problem Solutions — HAM-PATH is NP-complete

---

## Reductions and NP-completeness

### Karp reduction (§4)

**Karp reduction from X to Y**: polynomial-time algorithm A that:
- Takes input x of problem X.
- Produces input y = A(x) of problem Y.
- Such that answer(x) = answer(y).

Notation: **X ∝ Y**. Read "X reduces to Y". Means Y at least as hard as X.

**Direction trap**. Students flip the arrow constantly. Remember: *you reduce **from** the known-hard problem **to** the new problem*. Reduction flows hardness forward, along the arrow.

### Standard recipe for NP-completeness (§7)

**Showing new problem X is NP-complete** after Cook's theorem:
1. Show X ∈ NP.
2. Pick already-proven NP-complete problem Z.
3. Build polynomial Karp reduction Z ∝ X.
4. Prove reduction correctness: x is YES for Z ⟺ A(x) is YES for X.

Both directions (⇒) and (⇐) must be proven. Most common mistake: forget one direction.

---

## Problem Solutions

### Problem (d) — HAMILTONIAN-PATH is NP-complete

**Problem definition (decision).**
```
HAM-PATH
INPUT:  un graf G = (V, E)
OUTPUT: exista o permutare v₁, v₂, ..., vₙ a nodurilor lui V
        a.i. (vᵢ, vᵢ₊₁) ∈ E pentru 1 ≤ i < n?
```

**Proof that HAM-PATH ∈ NP.** Nondeterministic algorithm:
```
AlgHamPath(G):
  for i = 0..n-1:
    choose v[i] from V        // guess a permutation
  if v[0..n-1] is a permutation of V
     and (v[i], v[i+1]) ∈ E for all i < n-1:
    success
  else failure
```
Guess = n·⌈log n⌉ bits. Verify = O(n²). Poly. So HAM-PATH ∈ NP. ✓

**Proof that HAM-PATH is NP-hard.** Reduce **HAM-CYCLE ∝ HAM-PATH** (assume HAM-CYCLE is NP-complete — in course's §10 list).

Given G = (V, E) for HAM-CYCLE. Build G' for HAM-PATH:
1. Pick any vertex u ∈ V.
2. Add a copy u' with exactly the same neighbors as u.
3. Add two new vertices s, t. Connect s to u only, t to u' only.

G has a Hamiltonian cycle ⟺ G' has a Hamiltonian path (from s to t).
- (⇒) Cycle u → x₁ → ... → xₙ₋₁ → u becomes path s → u → x₁ → ... → xₙ₋₁ → u' → t.
- (⇐) A Ham path in G' must start at s and end at t (they have degree 1). Drop s and t; replace u' by u; you get a Ham cycle through u in G.

Reduction is O(|V| + |E|). ✓

So HAM-PATH is NP-complete. ∎
