# PS — real concept catalog

## Experiment aleator
A process whose outcome is unknown beforehand but whose set of possible results is known and reproducible under identical conditions (e.g., throwing a die produces one of {1..6}) (source: ps1).

## Eveniment aleator elementar
A single possible result of a random experiment; the set of all elementary events is the sample space Ω (source: ps1).

## Eveniment aleator (subset al lui Ω)
A subset A ⊆ Ω satisfying some predicate; usual notation A, B, C; for discrete Ω any subset is an event (source: ps1).

## Operații cu evenimente
Reuniune A∪B, intersecție A∩B, diferență simetrică AΔB, diferență A\B, eveniment contrar Ā = Ω\A; ∅ is the impossible event, Ω the certain event (source: ps1).

## Evenimente incompatibile (mutual disjuncte)
Two events with A ∩ B = ∅; a family is mutually disjoint if pairwise intersections are empty (source: ps1).

## Funcția de probabilitate (axiomele Kolmogorov)
P: P(Ω) → [0,1] satisfying: 0 ≤ P(A) ≤ 1, P(Ω)=1, P(∅)=0, and σ-additivity P(∪Aₖ) = Σ P(Aₖ) for mutually disjoint events (source: ps1).

## Probabilitate condiționată
P(A|B) = P(A∩B) / P(B) when P(B) > 0; the probability of A given that B has occurred (source: ps2).

## Evenimente independente
A and B are independent iff P(A∩B) = P(A)·P(B); equivalently (when P(B) > 0) P(A|B) = P(A) (source: ps2).

## Independență condiționată
P(A∩B|C) = P(A|C)·P(B|C); a notion of independence relative to a third event (source: ps2).

## Formula probabilității totale
For partition {Bᵢ} of Ω, P(A) = Σᵢ P(Bᵢ)·P(A|Bᵢ); decomposes the probability over a complete case analysis (source: ps2).

## Formula lui Bayes
P(B|A) = P(B)·P(A|B) / P(A); inverts conditional probabilities — basis of probabilistic reasoning under evidence (source: ps2).

## Versiunea condiționată a formulei probabilității totale
P(A|B) = P(C|B)·P(A|B∩C) + P(C̄|B)·P(A|B∩C̄) — total-probability formula relativized to a conditioning event B (source: ps3).

## Formula de înmulțire (chain rule)
P(A₁∩…∩Aₙ) = P(A₁)·P(A₂|A₁)·P(A₃|A₁∩A₂)·…·P(Aₙ|A₁∩…∩Aₙ₋₁) (source: ps3).

## Schema bilei neîntoarse (hipergeometrică)
Drawing without replacement from urn with two colors (n₁ white, n₂ black); probability of getting k whites in r draws follows hypergeometric (source: ps3).

## Schema lui Poisson
Independent trials with possibly different success probabilities; computes probability of exactly k successes via product-and-sum scheme (source: ps3).

## Schema binomială
n independent identical Bernoulli(p) trials; P(k successes) = C(n,k)·p^k·(1-p)^(n-k) (source: ps3).

## Schema geometrică
Independent Bernoulli(p) trials repeated until first success; P(k failures before success) = (1-p)^k · p (source: ps3).

## Variabilă aleatoare reală
A function X: Ω → ℝ such that for any interval J ⊆ ℝ, X⁻¹(J) is an event (source: ps4).

## Variabilă aleatoare discretă
A random variable whose image |X(Ω)| ≤ ℵ₀ (at most countable); fully described by table of (xᵢ, pᵢ) (source: ps4).

## Funcția de masă de probabilitate
fₓ(xᵢ) = pᵢ = P{X = xᵢ}; complete description of a discrete RV with Σpᵢ = 1, 0 < pᵢ ≤ 1 (source: ps4).

## Media (E[X]) unei variabile discrete
E[X] = Σᵢ pᵢ·xᵢ — finite sum or convergent series; if divergent, X has no expectation (source: ps4).

## Dispersia (varianța) unei variabile discrete
Var[X] = E[(X-μ)²] = Σpᵢ(xᵢ-μ)² — measures spread around the mean (source: ps4).

## Repartiția uniformă Uₙ
Discrete: P(X=k) = 1/n for k ∈ {1..n}; mean (n+1)/2, variance (n²-1)/12 (source: ps4).

## Repartiția Bernoulli și binomială B(n,p)
Bernoulli: single trial with P(X=1)=p; Binomial B(n,p): sum of n Bernoullis, mean np, variance np(1-p) (source: ps4).

## Repartiția geometrică Geometric(p)
P(X=k) = (1-p)^(k-1)·p for k ≥ 1; mean 1/p, variance (1-p)/p² (source: ps4).

## Repartiția Poisson(λ)
P(X=k) = e^(-λ)·λ^k/k!; mean = variance = λ; limit of Binomial when n→∞, p→0 with np→λ (source: ps4).

## Repartiția binomială negativă NB(r,p)
Counts failures before the r-th success in independent Bernoulli(p) trials; mean r(1-p)/p, variance r(1-p)/p² (source: ps5).

## Repartiția hipergeometrică
Without-replacement variant: from urn of n₁+n₂ balls, draws k, X = number of white balls; mean kn₁/n, variance k·(n-k)/(n-1)·(n₁/n)·(n₂/n) (source: ps5).

## Repartiția Zipf
Empirical heavy-tailed distribution: P(X=k) = 1/(Hₙ·k) where Hₙ is the n-th harmonic number; appears in linguistics/word frequencies (source: ps5).

## Repartiții comune (joint) ale variabilelor aleatoare discrete
Given X with values xᵢ and Y with yⱼ, the joint is the table rᵢⱼ = P{X=xᵢ ∩ Y=yⱼ}; marginals are obtained by row/column sums (source: ps5).

## Covarianța a două variabile aleatoare
Cov(X,Y) = E[XY] - E[X]E[Y]; measures linear association (source: ps5).

## Variabile aleatoare independente
X and Y independent iff joint factorizes: rᵢⱼ = pᵢ·qⱼ for all i,j; equivalently P(X∈A, Y∈B) = P(X∈A)·P(Y∈B) (source: ps5).

## Inegalitatea lui Markov
For X ≥ 0 with E[X] = μ, P{X ≥ t} ≤ μ/t for all t > 0; tail bound from mean alone (source: ps6).

## Inegalitatea lui Cebâșev
For X with mean μ and variance σ², P{|X-μ| ≥ t} ≤ σ²/t²; corollary: P{|X-μ| ≥ kσ} ≤ 1/k² (source: ps6).

## Inegalitatea lui Chernoff
For X = ΣXᵢ, sum of independent Bernoulli(pᵢ): exponential upper-tail bound P{X > (1+δ)μ} < [e^δ/(1+δ)^(1+δ)]^μ (source: ps6).

## Inegalitatea lui Hoeffding
Sharper concentration bound for sums of bounded independent variables, parallel to Chernoff (source: ps6).

## Variabile aleatoare continue
Variable whose image is uncountable; characterized by a probability density function f and CDF F(x) = ∫f instead of a probability mass function (source: ps6).

## Distribuții continue remarcabile
Uniformă U[a,b], exponențială Exp(λ), normală N(μ,σ²), Student t, Gamma, etc. — see ps6 outline (source: ps6).

## Teorema lui Cebâșev (legea numerelor mari)
The empirical mean of i.i.d. observations converges (in probability) to the true mean as n → ∞ (source: ps6).

## Teorema limită centrală (Lindeberg-Lévy)
For i.i.d. (Xₙ) with mean μ and variance σ², (Σ Xᵢ - nμ)/(σ√n) → N(0,1) in distribution (source: ps7).

## Aproximarea normală a distribuției binomiale (Moivre-Laplace)
For B(n,p) with np(1-p) ≥ 10, Y = (X-np)/√(np(1-p)) is approximately N(0,1) (source: ps7).

## Corecția continuă
Adjustment when approximating discrete distribution with continuous: P(X=10) ≈ P(9.5 ≤ X ≤ 10.5), P(X<13) ≈ P(X ≤ 12.5), etc. (source: ps7).

## Generarea de numere aleatoare uniforme
Pseudo-random number generators (PRNG) — most languages use Mersenne Twister; basis for simulating any distribution via inverse-CDF method (source: ps7).

## Comenzi R pentru distribuții (p/q/d/r)
R distribution functions: pXXX = CDF, qXXX = inverse-CDF (quantile), dXXX = PDF/PMF, rXXX = sampler — for binom, geom, pois, unif, exp, norm, t, gamma (source: ps7).

## Metode Monte Carlo (simulare)
Solve problems by generating large numbers of random values and observing the fraction with a property; used when analytic solution is intractable (source: ps8).

## Estimarea mediei prin Monte Carlo
Generate N i.i.d. samples X₁..Xₙ and use X̄ = (X₁+…+Xₙ)/N as unbiased estimator of E[X]; Var(X̄) = σ²/N (source: ps8).

## Integrare Monte Carlo
Estimate ∫f using random sampling — area/volume estimation by generating uniform samples and counting fraction in region (source: ps8).

## Estimarea probabilităților prin Monte Carlo
Probability of event = expected indicator value; estimate by generating samples and counting (source: ps8).

## Algoritmi aleatori — clasificare
Random algorithm = an algorithm that makes probabilistic choices during execution; classified into Las Vegas (always correct, randomness affects only runtime) and Monte Carlo (may err with bounded probability) (source: ps9).

## Algoritmi Las Vegas
Always return correct output; runtime is the random variable. Definition: P(A(x)=F(x)) ≥ 1/2 and P(A(x)="?") ≤ 1/2 (with retry boost) (source: ps9).

## Evaluarea unui game-tree (algoritm aleator)
Random child selection during AND/OR-tree evaluation gives expected work O(N^log₄ 3) ≈ N^0.793 — beats deterministic worst case (source: ps9).

## RandomizedQuickSort
Pick pivot uniformly at random; expected runtime O(n log n) avoiding the worst-case sorted-input scenario of deterministic QuickSort (source: ps9).

## Algoritmi Monte Carlo
Algorithms with positive probability of error; trade correctness for speed (source: ps9).

## Verificarea înmulțirii matricilor (Freivalds)
Monte Carlo: pick random vector r, check A(Br) = (Cr); if AB ≠ C the test detects with prob ≥ 1/2 in O(n²) instead of O(n³) (source: ps9).

## Algoritm aleator pentru min-cut
Karger's randomized contraction algorithm finds a graph min-cut with probability ≥ 1/C(n,2) per run; repeat to amplify (source: ps9).

## Metoda probabilistică (Erdős)
Prove existence of a combinatorial object by showing a random object has nonzero probability of having the property; applied to satisfiability (source: ps9).

## Statistică — populație, eșantion
Population = complete set of objects of interest (often infinite); sample (eșantion) = subset selected from population; simple random sample = each individual has equal selection probability (source: ps10).

## Variabile / atribute / date
Atribut = caracteristică măsurată; date = valorile colectate; data can be real numbers, integers, words, letters etc. (source: ps10).

## Parametru vs statistică
Parametru = numerical value computed over the entire population; statistică = same value computed on a sample (source: ps10).

## Statistica descriptivă vs inferențială
Descriptivă: collects, presents and describes data (often graphically); Inferențială: makes decisions about the population from sample data (source: ps10).

## Tipuri de variabile (calitative vs cantitative)
Calitative: nominale (no order — eye color) or ordinale (ordered — satisfaction level); Cantitative: discrete or continue (source: ps10).

## Variabile discrete vs continue
Discrete count (number of credits, pages); Continue measure (height, speed, temperature) — typically can take any value in a real interval (source: ps10).

## Reprezentări grafice
Calitative: pie charts, bar graphs (frequency-based); Cantitative: histogram, stem-and-leaf — reveal distribution shape (source: ps10).

## Frecvența și frecvența relativă
Frecvența = number of times observation appears in sample; frecvența relativă = freq / sample size; distribuția frecvențelor groups all (observation, freq) pairs (source: ps10).

## Histograma și gruparea în clase (bins)
Group continuous data into k = 1 + log n / log 2 classes of equal width; sort observations by class; bar height = class frequency (source: ps10).

## Măsuri ale tendinței centrale
Media (mean), mediana (middle when sorted), modul (most frequent value), cvartile Q1/Q2/Q3 — different summaries of "typical" value (source: ps10).

## Măsuri ale variabilității
Domeniul (range = max-min), dispersia eșantionului, deviația standard a eșantionului, mediana și domeniul intercvartilic (IQR), valori aberante (outliers) (source: ps10).

## HW Tema D / Tema A,B,C — exerciții aplicative
Homework problem sets covering applied probability and statistics computations (e.g., Bayes problems, binomial computations, Monte Carlo simulations) (source: Tema_D, Tema_A, Tema_B, Tema_C).

## Lab stat 1..4
Lab sheets covering descriptive statistics, distribution fitting, sampling, hypothesis testing in R (source: lab_stat1..4).
