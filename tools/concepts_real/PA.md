# PA — real concept catalog

## Probleme computaționale, de decizie, de optim
A computational problem maps inputs to outputs; decision problems output a single bit (yes/no); optimization problems return min-cost or max-gain solutions, and admit equivalent decision-problem reformulations (source: lecture9_10_np).

## Echivalența problemelor de optim cu cele de decizie
For NP-completeness theory we restrict to decision problems; optimization-to-decision reduction uses binary search over k thresholds (e.g., `cost(s) <= k?`) (source: lecture9_10_np).

## Algoritmi nedeterminiști — instrucțiunea choose
A nondeterministic algorithm has the additional `choose x from {...}` instruction; an execution may succeed/fail/loop and the model picks any successful execution if one exists (source: lecture9_10_np).

## Ghicire & verificare (guess and verify)
Every polynomial-time nondeterministic algorithm decomposes into a guess phase (polynomially many guessed bits) and a deterministic verification phase (source: lecture9_10_np).

## Reduceri Karp (polynomial-time many-one)
A Karp reduction from X to Y is an algorithm A that transforms input x into y = A(x) such that f(x) = g(y); used to prove negative results — if X is hard and X reduces to Y then Y is hard (source: lecture9_10_np).

## Clasa P
P = class of decision problems with a deterministic polynomial-time algorithm in worst case; informally the "tractable" class (e.g., 2-SAT, BIPARTITE, SORT, PATH, PRIMES) (source: lecture9_10_np).

## Clasa NP
NP = decision problems with a nondeterministic polynomial-time algorithm; equivalently problems whose YES-instances admit a polynomial-time-checkable certificate (source: lecture9_10_np).

## P ⊆ NP and the P vs NP question
Every deterministic algorithm is a special-case nondeterministic one (single execution); whether NP ⊆ P remains the central open problem (source: lecture9_10_np).

## NP-completeness definition
Problem X is NP-complete iff X ∈ NP and every Y ∈ NP reduces to X in polynomial time; NP-easy = condition 2 only, NP-hard = condition 2 only (source: lecture9_10_np).

## Teorema Cook (1971)
CIRCUIT-SAT is NP-complete — historically first NP-completeness proof; every NP problem reduces to CIRCUIT-SAT via simulating a polynomial-time verifier (source: lecture9_10_np).

## CIRCUIT-SAT
Input: a combinational circuit C; output: do inputs exist that make C output 1? — canonical NP-complete problem (source: lecture9_10_np).

## SAT (boolean satisfiability)
Input: a propositional formula φ; output: is φ satisfiable? — proven NP-complete by reducing CIRCUIT-SAT to SAT using Tseitin-style fresh variables for each circuit wire (source: lecture9_10_np).

## Algoritmul lui Tseitin
Reduction from SAT to 3-SAT: associate a propositional variable with each non-atomic subformula and add equivalence clauses, producing 3-CNF (source: lecture9_10_np).

## 3-SAT
Restriction of SAT to formulas in 3-CNF (each clause has at most 3 literals); NP-complete — proven by reducing SAT to 3-SAT (source: lecture9_10_np).

## Reducere 3-SAT ∝ 3-COL
Polynomial reduction building a graph with truth-gadget (T,F,O triangle), variable-gadgets (xi, ¬xi nodes), and clause-gadgets (chained OR-simulating triangles) such that φ is satisfiable iff G is 3-colorable (source: lecture9_10_np).

## 3-COL (3-Colorability)
Input: graph G; output: can G be colored with 3 colors so adjacent vertices differ? — NP-complete (source: lecture9_10_np).

## SUBSET-SUM
Input: set S ⊆ ℕ and target t ∈ ℕ; output: does some S' ⊆ S sum to t? — NP-complete via reduction from 3-SAT using base-10 numbers with one digit per variable + clause (source: lecture9_10_np).

## PARTITION (weakly NP-hard)
Given a multiset of numbers, can it be split into two disjoint subsets of equal sum? Weakly NP-hard (poly in unary, exponential in binary) (source: seminar10_en).

## Knapsack / Discrete knapsack
Decision version: given items with weights and values, capacity W and target V, is there a subset of weight ≤ W and value ≥ V? — NP-hard (source: seminar10_en).

## Maximum Independent Set (MIS)
Given graph G, find largest set of pairwise non-adjacent vertices; decision version NP-hard via reduction from 3-SAT (source: seminar10_en).

## CLIQUE
Decision: does G contain a complete subgraph of size k? NP-complete via MIS-to-CLIQUE reduction (complement graph) (source: seminar10_en).

## Vertex Cover
Decision: is there a set of ≤ k vertices touching every edge? NP-complete via reduction from MIS (source: seminar10_en).

## Hamiltonian Path / Cycle
Existence of a path/cycle visiting each vertex exactly once; both NP-complete (source: seminar9_en).

## Graph Isomorphism
Decision: are two graphs isomorphic? In NP, but not known to be P or NP-complete (source: seminar9_en).

## 2-Colorability is in P
A graph is 2-colorable iff it is bipartite — testable in linear time with BFS/DFS (source: seminar9_en).

## 2-SAT is in P
2-SAT solvable in polynomial time via implication-graph + strongly-connected-components algorithm (source: seminar9_en).

## Halting Problem
Given algorithm A and input, does A halt? — undecidable; NP-hard (source: seminar10_en).

## Bounded Halting
Does algorithm A halt within k steps? — Ω(2^n) lower bound where n = log k (source: lecture9_10_np).

## Backtracking — partial-solution validation
Recursive enumeration that prunes branches whose partial solution cannot be extended; for n=4 queens reduces calls from 341 (exhaustive) to 17 (source: seminar 11 backtracking).

## Backtracking — solution representation contract
Designing a backtracker requires specifying: (a) solution shape, (b) what partial solutions look like, (c) direct successors of a partial solution, (d) viability test (source: seminar 11 backtracking).

## Backtracking pentru Subset Sum
Choose-include-or-exclude tree; prune when partial sum exceeds target (when all values positive) (source: seminar 11 backtracking).

## Backtracking pentru Sudoku
Place a digit cell-by-cell; viability test = no row/column/3×3-box conflict; backtrack on dead ends (source: seminar 11 backtracking).

## Branch & Bound
Backtracking enhanced with bound estimation: prune when an optimistic bound (`maxRest`) on remaining decisions cannot improve current best — requires a correct (admissible) estimator (source: seminar 11 backtracking).

## Branch & Bound pentru Maximum Independent Set
Use B&B with maxRest estimating an upper bound on independent-set size from the remaining undecided vertices (source: seminar 11 backtracking).

## Strategii când o problemă e NP-completă
When a problem is NP-complete: accept worst-case-slow algorithms (often backtracking), use approximation algorithms, exploit special-case structure, or rephrase the problem (source: lecture9_10_np).

## Lecture 11 — additional NP/approximation topics
PA lecture 11 (en/ro) covers approximation algorithms, randomized algorithms, or related advanced topics extending the NP-completeness machinery (source: lecture11_en, lecture11_ro).
