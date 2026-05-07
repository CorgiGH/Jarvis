# ALO — real concept catalog

## Operații cu vectori și matrice
Recap of basic vector and matrix arithmetic, transposition and elementary matrix types as foundation for the course (source: alo_c01).

## Tipuri de matrice elementare
Diagonal, identity, triangular, symmetric, orthogonal, permutation matrices and their algebraic properties (source: alo_c01).

## Trafic și sisteme liniare
Motivating example — modeling traffic flow in a network as a linear system Ax = b (source: alo_c01).

## Centralitatea în rețele sociale
Network centrality computed via matrix powers / eigenvectors (PageRank-style) as motivation for linear algebra (source: alo_c01).

## Compresia imaginilor digitale și SVD
Singular Value Decomposition used to compress images by truncating small singular values (source: alo_c01).

## Produs scalar
Inner product on a vector space X — bilinear/sesquilinear function with positivity, conjugate symmetry, linearity in first argument; complex case uses y^H x (source: alo_c02).

## Identitatea (Ax, y) = (x, A^H y)
Adjoint/transpose identity — `(Ax,y) = (x, A^H y)` for complex case, `(x, A^T y)` for real (source: alo_c02).

## Norme vectoriale (1, 2, ∞)
||·||₁ = sum of absolute values, ||·||₂ = Euclidean, ||·||∞ = max absolute value; lab seminar 1 computes all three for given vectors (source: alo_c02, alo_sem1).

## Norma Euclidiană
Norm induced by standard inner product on ℝⁿ / ℂⁿ; ||x||₂ = √(x^T x) (source: alo_c02).

## Norme matriceale induse (naturale)
||A|| = max ||Ax|| / ||x||; the induced norm from a vector norm respects ||AB|| ≤ ||A||·||B|| (source: alo_c02).

## Norma Frobenius
||A||_F = (Σᵢⱼ |aᵢⱼ|²)^(1/2) — equivalent to a vector 2-norm of vectorized matrix; not induced (source: alo_sem1).

## Norma spectrală
||A||₂ = largest singular value of A — induced by Euclidean vector norm (source: alo_c02).

## Reprezentări binare ale numerelor (IEEE 754)
IEEE 754 standard for floating-point: arithmetic formats (binary16/32/64/128, decimal), interchange formats, rounding rules, operations, exception handling (source: alo_c03).

## Sign-exponent-mantisa decomposition
A binary IEEE-754 number is `(-1)^s · 2^(c-bias) · (1+f)` with sign bit, biased exponent, fractional mantissa (source: alo_c03).

## Underflow și overflow
Numbers smaller than the smallest representable value flush to zero (underflow); larger than the largest representable value cause overflow (source: alo_c03).

## Subnormale, NaN, ±0, infinit
Special IEEE-754 values: subnormal numbers fill the gap below smallest normal, NaN signals invalid operations, signed zeros, infinities (source: alo_c03).

## Precizia mașină
Smallest u = 10^-m such that 1 + u ≠ 1 in computer arithmetic; used as a base error tolerance (source: alo_t1).

## Neasociativitatea aritmeticii cu virgulă mobilă
Floating-point + and × are not associative — concrete example with x=1, y=z=u/10 demonstrates (x+y)+z ≠ x+(y+z) (source: alo_t1).

## Aproximarea funcțiilor cu serii MacLaurin
Polynomial truncation `tan(x) ≈ x + x³/3 + 2x⁵/15 + 17x⁷/315 + 62x⁹/2835`; argument-reduction via tan(π/2-x)=1/tan(x) keeps x in [-π/4, π/4] for accuracy (source: alo_t1).

## Erori în calculele numerice (surse, propagare)
Sources: input rounding, finite arithmetic, truncation; propagation: how errors compound through sums, products, and iterations (source: alo_c03).

## Condiționare și stabilitate numerică
Conditioning = sensitivity of true solution to input perturbation; stability = how much the algorithm amplifies rounding errors (source: alo_c03).

## Polinomul Wilkinson
Classic ill-conditioned polynomial: tiny perturbations in coefficients cause huge changes in roots — illustrative of ill-conditioning (source: alo_c03).

## Regula lui Cramer
Solves Ax=b via xᵢ = det(Aᵢ(b)) / det(A); correct but O(n!) and numerically unstable — only useful pedagogically (source: alo_c04).

## Algoritmul de eliminare Gauss
Reduce Ax=b to triangular Ãx=b̃ by row operations, then back-substitute; standard direct solver — `f = -aᵢᵣ/aᵣᵣ; Eᵢ = Eᵢ + f·Eᵣ` (source: alo_c04).

## Substituție directă și inversă
Direct (forward) substitution solves Lx=b with L lower triangular; inverse (backward) substitution solves Ux=b with U upper triangular (source: alo_c04).

## Eliminarea chinezească
Variant of Gauss elimination introduced as alternative numerical scheme (source: alo_c04).

## Descompunere LU
Eliminarea Gauss fără schimbare de ecuații ⇔ A = LU with L unit-lower-triangular and U upper-triangular; reuse for many right-hand sides (source: alo_c05).

## Descompunerea Cholesky
For symmetric positive-definite A: A = L·L^T with L lower triangular; L computed column by column with `lᵣᵣ = √(aᵣᵣ - Σlᵣₖ²)`, `lᵢᵣ = (aᵢᵣ - Σlᵢₖlᵣₖ)/lᵣᵣ` (source: alo_c05).

## Cost Cholesky 1/6 n³
Operation count of Cholesky factorization is `n³/6 + O(n²)` additions and multiplications — about half the cost of LU (source: alo_c05).

## Matrice pozitiv definită
A is pos. def. iff (Ax,x) > 0 for all x ≠ 0; iff symmetric with all eigenvalues > 0; iff Cholesky factorization exists (source: alo_c05).

## Descompunere QR
A = QR with Q orthogonal and R upper triangular; converts Ax=b into Rx=Q^T b which is back-substitutable (source: alo_c06).

## Algoritmul Householder
Builds QR using reflection matrices `P = I - 2vv^T` (||v||₂=1) which are symmetric and orthogonal; n-1 reflections triangularize A column by column (source: alo_c06).

## Matrice de reflexie Householder
P = I - 2vv^T with ||v||₂=1; `P^T = P`, `P² = I` — reflects across hyperplane orthogonal to v (source: alo_c06).

## Algoritmul Givens
QR via Givens rotations — 2D rotations that zero out one element at a time; useful for sparse structure (source: alo_c06).

## Algoritmul Gram-Schmidt și varianta modificată
Orthogonalize columns of A successively to obtain Q; modified Gram-Schmidt is more numerically stable than classical (source: alo_c06).

## Metode iterative pentru sisteme liniare
Build sequence x^(k) → x* without modifying A; suited for large sparse systems where direct methods are too costly (source: alo_c07).

## Memorarea matricelor rare (CSR)
Compressed Sparse Row: three vectors `valori`, `ind_col`, `inceput_linii` store non-zeros plus row-start positions; `inceput_linii(n+1) = NN+1` (source: alo_c07).

## Metoda Jacobi
Iterative solver: `xᵢ^(k+1) = (bᵢ - Σⱼ≠ᵢ aᵢⱼ xⱼ^(k)) / aᵢᵢ`; converges if A is strictly diagonally dominant (source: alo_c07).

## Metoda Gauss-Seidel
Iterative solver using newest available components: `xᵢ^(k+1) = (bᵢ - Σⱼ<ᵢ aᵢⱼ xⱼ^(k+1) - Σⱼ>ᵢ aᵢⱼ xⱼ^(k)) / aᵢᵢ`; converges for symmetric positive-definite A (source: alo_c07).

## Metodele relaxării (SOR)
Successive over-relaxation: `xᵢ^(k+1) = (1-ω)xᵢ^(k) + ω·xᵢ_GS` for ω ∈ (0,2); accelerates Gauss-Seidel for SPD matrices (source: alo_c07).

## Metoda pantei maxime / gradienților conjugați
Iterative methods for SPD matrices using gradient information; conjugate gradient converges in at most n steps in exact arithmetic (source: alo_c07 outline).

## Optimizare numerică — probleme de minimizare
`min{f(x); x∈D}`; maximization reduces to minimization via `max f = -min(-f)` (source: alo_c08).

## Tipuri de probleme de optimizare
Linear/nonlinear, quadratic, convex, with/without constraints, continuous/discrete/integer/mixed (source: alo_c08).

## Punct de minim global / local / strict local
Global if f(x*)≤f(x) for all x; local if there's a neighborhood V where f(x*)≤f(x) ∀x∈V; strict if `<` for x≠x* (source: alo_c08).

## Gradient și matrice Hessiană
∇f(x) = vector of partials; Hessian H_f(x) = matrix of mixed second partials; both used in Taylor expansion and optimality conditions (source: alo_c08).

## Dezvoltare Taylor de ordinul 1 și 2
f(x) ≈ f(a) + ∇f(a)^T (x-a) + ½(x-a)^T H_f(a)(x-a) + R(||x-a||³); basis for first/second-order optimality conditions (source: alo_c08).

## Condiții necesare de optim de ordinul 1 (Teorema 1)
If x* is a local minimum of f ∈ C¹, then ∇f(x*) = 0; such points are called stationary/critical points (source: alo_c08).

## Condiții necesare de optim de ordinul 2 (Teorema 2)
If x* is a local minimum of f ∈ C², then ∇f(x*)=0 AND H_f(x*) is positive semi-definite (source: alo_c08).

## Valori proprii și vectori proprii
λ ∈ ℂ is an eigenvalue of A iff ∃u ≠ 0 with `Au = λu`; equivalently `det(λI - A) = 0` (source: alo_c09).

## Polinomul caracteristic
`p_A(λ) = det(λI - A)` is a degree-n polynomial whose roots are the eigenvalues; an n×n matrix has exactly n eigenvalues (counted with multiplicity) (source: alo_c09).

## Diagonalizarea matricelor
If A has n distinct eigenvalues, there exists nonsingular T such that `T⁻¹AT = diag(λ₁,...,λₙ)`; T = [u₁ u₂ ... uₙ] is the eigenvector matrix (source: alo_c09).

## Matrici asemenea
A ~ B iff ∃T nonsingular with `A = TBT⁻¹`; similar matrices share characteristic polynomial and eigenvalues (source: alo_c09).

## Teorema lui Gershgorin
Every eigenvalue λ of A lies in some Gershgorin disc: there is i₀ with `|λ - aᵢ₀ᵢ₀| ≤ Σⱼ≠ᵢ₀ |aᵢ₀ⱼ|` (source: alo_c09).

## Metoda puterii și iterația inversă
Power method: x^(k+1) = Ax^(k)/||·|| converges to dominant eigenvector; inverse iteration uses (A-σI)⁻¹ to target eigenvalue near σ (source: alo_c01 plan).

## Forma Hessenberg
Almost-triangular form (zeros below first subdiagonal) used as preprocessing for QR-eigenvalue iteration (source: alo_c01 plan).

## Metoda Jacobi pentru valori proprii (matrici simetrice)
Iteratively apply Givens rotations Rₚq(θ) chosen to zero the largest off-diagonal element; A^(k+1) = Rₚq A^(k) Rₚq^T converges to diag(λᵢ) (source: alo_t5).

## Descompunerea după valori singulare (SVD)
A = UΣV^T with U, V orthogonal and Σ diagonal containing σ₁ ≥ σ₂ ≥ ... ≥ σᵣ > 0; works for any m×n matrix (source: alo_c10).

## Valorile singulare și relația cu A^T A, A A^T
σᵢ² are the strictly-positive eigenvalues of A^T A and A A^T; columns of U/V are eigenvectors of A A^T / A^T A (source: alo_c10).

## Rangul matricei via SVD
rang(A) = number of strictly-positive singular values (source: alo_c10, alo_t5).

## Numărul de condiționare k₂(A)
k₂(A) = σ_max / σ_min where σ_min is the smallest non-zero singular value; quantifies how much input error gets amplified (source: alo_t5).

## Pseudoinversa Moore-Penrose
A^I = V·Σ^I·U^T where Σ^I has 1/σᵢ on the diagonal for nonzero σᵢ; satisfies AA^I A = A and (A^I)^I = A; reduces to A⁻¹ when A is square nonsingular (source: alo_c10, alo_t5).

## Rezolvare în sensul celor mai mici pătrate
For overdetermined Ax=b (p>n) the minimum-norm least-squares solution is `x^I = V Σ^I U^T b`; equivalently `(A^T A)⁻¹ A^T b` when A^T A is invertible (source: alo_c10, alo_t5).

## Metode de rezolvare a ecuațiilor neliniare
Bisecție, tangentă (Newton), coardă, secantă; methods for finding roots of f(x)=0 with different convergence rates (source: alo_c01 plan).

## Interpolare numerică
Lagrange polynomial form, Newton divided differences, cubic splines, least-squares fit — methods to reconstruct a function from sample values (source: alo_c01 plan).

## Metode numerice de optimizare fără restricții
Gradient descent, Newton, quasi-Newton, conjugate-gradient — descend along directions derived from ∇f and H_f (source: alo_c01 plan).
