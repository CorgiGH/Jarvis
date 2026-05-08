#!/usr/bin/env python3
"""Seed /opt/jarvis/data/archival/_extras/{subject}/course-info.md with
canonical course metadata scraped from edu.info.uaic.ro and external
course pages on 2026-05-08. Idempotent: overwrites the file each run.

Subjects covered: ALO, PS, POO. SO/RC/PA stubs flagged for follow-up
(public pages only had partial info; rest behind login).

Why _extras/: ConceptCatalog.scan() skips path components starting with
`_`, so these files don't pollute the concept catalog or [[stats]]
counts. They DO surface via [[search]]/[[recall]]/[[read]] tool calls,
giving the bot grounded context when the user asks about deadlines,
grading formulas, or what's coming up.

Pipe:
  ssh root@46.247.109.91 "python3 -" < tools/seed-course-info.py
"""

import os

ROOT = "/opt/jarvis/data/archival/_extras"

PAGES = {
    "ALO": """# ALO — Algebra Liniara si Optimizare 2025-2026

Sources:
- https://edu.info.uaic.ro/algebra-liniara/ (fetched 2026-05-08)
- BScIA-2025-2028.pdf curriculum (UAIC FII, AI program, Anul I Sem 2)

## Curriculum metadata (from BScIA-2025-2028.pdf)

- Course code: **AI1203**
- Type: DF (Discipline fundamentale)
- ECTS credits: **5**
- Form of verification: **E (Examen)** — has June final exam
- Hours/week: 2 Curs + 1 Seminar + 1 Laborator (4h total)
- Online %: C 22, S 22, L 22

## Grading formula

`Punctaj final = punctaj laborator + 10*nota test seminar + 40*nota test scris`

Pass requirements:
- Written exam grade ≥ 3
- Final score ≥ 360

Final-grade Gaussian distribution:
- Top 10% → 10
- Next 20% → 9
- Next 25% → 8
- Next 25% → 7
- Next 15% → 6
- Remaining 5% → 5

## Lab assignments (Teme)

| # | Topic | Points | Deadline (week) |
|---|-------|--------|-----------------|
| 1 | Machine precision & function approximation | 50 | Week 5 |
| 2 | Function minimization | 60 | Week 12 |
| 3 | Linear systems — LU decomposition | 60 | Week 12 |
| 4 | Linear systems — QR decomposition | 60 | Week 14 |
| 5 | Eigenvalues / eigenvectors & SVD | 70 | Week 14 |

Bonus: up to 5 pts for submission ≥ 1 week early.
Late penalty: 50% point deduction after deadline.

Approximate calendar (semester started ~Feb 16, week-1 = Feb 16-22):
- Week 5 ≈ 2026-03-22 (Tema 1 — likely past)
- Week 12 ≈ 2026-05-10 (Tema 2 + 3)
- Week 14 ≈ 2026-05-24 (Tema 4 + 5)

## Seminar test (week 7 ≈ 2026-04-05)

`test scris de jumătate de oră ... din exercițiile făcute la seminariile precedente`

## Written exam (final exam session)

`teză scrisă cu 3 sau 4 exerciții din materia predată — 1 oră`

First 30 min: printed materials only (no electronics). Grading 1-10 scale.

## Lecture materials

13 PDFs (Curs 01-13) at https://edu.info.uaic.ro/algebra-liniara/curs/

## Lab materials

Tema 1-5 PDFs at https://edu.info.uaic.ro/algebra-liniara/lab/

## Lab evaluation policy

- 40% implementation, 60% presentation + Q&A.
- Team size: max 2 students; composition fixed for semester.
- LLM disclosure: source must cite sources + indicate % LLM-generated code.
- Fraud: first offense = point deduction; second offense = lab score -600
  (no recovery).
- Group changes: one-time only, within first 2 weeks; needs instructor approval.
- Final scores provisional until week 13 copy-paste verification.
""",
    "PS": """# PS — Probabilitati si Statistica Spring/Summer 2026

Sources:
- https://edu.info.uaic.ro/probabilitati-si-statistica/ (fetched 2026-05-08)
- BScIA-2025-2028.pdf curriculum (UAIC FII, AI program, Anul I Sem 2)

## Curriculum metadata (from BScIA-2025-2028.pdf)

- Course code: **AI1204**
- Type: DF (Discipline fundamentale)
- ECTS credits: **5**
- Form of verification: **V (Verificare)** — NOT a formal exam.
  This matches the public page note: "There is no arrears and
  enhancements session exam." PS uses continuous evaluation
  (seminars + labs + HW) only.
- Hours/week: 2 Curs + 1 Seminar + 1 Laborator (4h total)
- Online %: C 22, S 22, L 22

## ⚠️ Schedule.json placeholder note

The placeholder PS exam at 2026-06-15 is INCORRECT — PS does not
have a June exam. The 2026-05-21 HW deadline IS the final
evaluation point. Remove or relabel the placeholder.

## Instructors

- Olariu Emanuel Florentin — C212, C building. emanuel.olariu@info.uaic.ro
- Zalinescu Adrian — C307, C building. adrian.zalinescu@info.uaic.ro

## Lecture schedule

Discrete probability:
- L1: 2026-02-16 — Introduction & probability function
- L2: 2026-02-23 — Conditional probability & independence
- L3: 2026-03-02 — Probabilistic formulas & schemata
- L4: 2026-03-09 — Discrete random variables & distributions
- L5: 2026-03-16 — Joint distributions & covariance
- L6: 2026-03-23 — Inequalities & continuous variables

Statistics + labs:
- L7 / Lab 1: 2026-03-30 — CLT & simulation
- L8 / Lab 2: 2026-04-20 — Monte Carlo methods
- L9 / Lab 3: 2026-04-27 — Randomized algorithms
- L10 / Lab 4: 2026-05-04 — Descriptive statistics

## Homework deadline

**2026-05-21** — `you can delete and/or upload the homeworks until 21 of May`

Tema (paired with Lab numbers):
- Tema A = Lab 1 work (CLT simulation)
- Tema B = Lab 2 work (Monte Carlo methods)
- Tema C = Lab 3 work (Randomized algorithms)
- Tema D = Lab 4 work (Descriptive statistics with datasets)

## NO arrears / enhancements exam

Page literally states: `There is no arrears and enhancements session exam.`

So PS does NOT have a June final exam. The 2026-05-21 HW deadline is the
last evaluation point. The placeholder PS June exam in schedule.json is
WRONG and should be removed.

## Grading formula

Seminars (60 pts total):
- Six 15-min tests, 10 pts each.
- Min 30 pts to pass: `Those who fail to receive at least 30 points cannot pass the course.`

Laboratories (60 pts total):
- Class exercises: 20 pts.
- Homework: 20 pts.
- Final-week test: 20 pts.
- Min 30 pts to pass.

## Syllabus

Discrete probability: random events, conditional probability, total
probability + Bayes, discrete random variables, expectations/variance,
distributions (uniform, Bernoulli, binomial, geometric, Poisson,
negative binomial, hypergeometric, Zipf), joint distributions,
covariance, Markov / Chebyshev / Chernoff / Hoeffding inequalities,
continuous distributions, LLN, CLT.

Statistics: Monte Carlo methods, randomized algorithms (Las Vegas,
Monte Carlo type), probabilistic methods, descriptive statistics
(central tendency, mean, median, mode, quartiles), variability
(variance, std, IQR, outliers).

## Lecture slides

RO + EN versions for all 10 lectures at
https://edu.info.uaic.ro/probabilitati-si-statistica/files/

## Lab data files

unemployment.csv, sample1.txt, sample2.txt, life_expect.csv, unemploy2012.csv
""",
    "POO": """# POO — Programare Orientata pe Obiecte 2026

Sources:
- https://gdt050579.github.io/poo_course_fii/administrative.html (fetched 2026-05-08)
- BScIA-2025-2028.pdf curriculum (UAIC FII, AI program, Anul I Sem 2)

## Curriculum metadata (from BScIA-2025-2028.pdf)

- Course code: **AI1201**
- Type: DF (Discipline fundamentale)
- ECTS credits: **5**
- Form of verification: **E (Examen)** — has June final exam
- Hours/week: 2 Curs + 0 Seminar + 2 Laborator + 2 Lucrari practice (6h total)
- Online %: C 22, L 22

## Grading formula (4 components)

| Component | Points | Notes |
|-----------|--------|-------|
| First lab evaluation | 30 | Week 8 |
| Second lab evaluation | 30 | Week 14 or 15 |
| Final exam | 30 | Examination period |
| Lab activity | 10 | Up to 1 pt per lab, max 10 |

Pass threshold: **45 / 100** total.

Approximate calendar (semester ~Feb 16):
- Week 8 ≈ 2026-04-06 (Lab eval 1 — likely past)
- Week 14 ≈ 2026-05-18 (Lab eval 2 candidate)
- Week 15 ≈ 2026-05-25 (Lab eval 2 alternate)

## Schedule

Classes on-site in rooms C112 and C2 across Mon/Tue/Wed; some sessions
online via Zoom. Special C++ → Rust session: 2026-05-06.

## Lecture material

10 lectures (`poo_c1.pdf` … `poo_c10.pdf`) — already on disk in
`Desktop/Second brain/Courses/POO/`.

## Lab material

10 labs + extras (`poo_lab01.html` … `poo_lab10.html`) at
https://gdt050579.github.io/poo_course_fii/labs/labs.html
""",
    "SO": """# SO+RC — Sisteme de Operare si Retele de Calculatoare (combined course)

Sources:
- https://edu.info.uaic.ro/sisteme-de-operare/ (fetched 2026-05-08)
- https://edu.info.uaic.ro/computer-networks/notare.php (fetched 2026-05-08)
- BScIA-2025-2028.pdf curriculum

## Curriculum metadata (IMPORTANT — combined course)

- Course code: **AI1202**
- Course title: "Sisteme de operare si retele de calculatoare"
- Type: DF (Discipline fundamentale)
- ECTS credits: **5** (single 5-credit course covering BOTH halves)
- Form of verification: **E (Examen)** — has June final exam
- Hours/week: 2 Curs + 0 Seminar + 2 Laborator + 2 Lucrari practice (6h total)

The bot's catalog has this as subject `SO&RC`. Memory's "5 active
subjects" counts it as ONE.

## Internal split (per memory user_uaic_finals_2026.md)

The combined course has two halves with separate evaluation tracks
that aggregate into the single AI1202 final mark:

**SO half — continuous evaluation 80/20 split:**
- 80% continuous evaluation.
- 20% final exam.
- User's continuous-eval score is locked at 24.5/50 — needs final
  exam to compute final SO mark.

**RC half — formula `N = 0.5 * P + 0.4 * L + 1`:**
- P = practical project. Pass: P ≥ 5.
- L = lab/seminar evaluation in weeks 4 and 11.
- Pass: N ≥ 5 AND P ≥ 5.
- Memory user note "RC half = lab + T.RC June exam" — `T.RC` is
  likely the project P or its presentation; the published notare.php
  formula does NOT mention a separate June T.RC exam, so memory may
  be slightly off OR there's restricted-access info we haven't
  fetched.

## Materials

Operating Systems half:
- `Operating Systems course.pdf` already on disk in
  `Desktop/Second brain/Operating Systems course.pdf`.
- Lab files: `Desktop/Second brain/Labs/SO/` (if present).

Computer Networks half:
- Instructors: Alboaie Lenuta, Panu Andrei.
- Topics: network types, TCP/IP protocols, network architecture
  models, client/server paradigm, BSD socket interface, wireless
  networks, security.
- Materials: cursullaboratorul.php / resurse.php / notare.php on
  edu.info.uaic.ro/computer-networks/ (some restricted).

## Stubs / fetch incomplete

- Concrete lecture / lab / exam dates not on public pages.
- Final exam date in 2026-06-01 → 2026-06-21 window — not yet
  published.

Action: when user gets the official AI1202 exam slot, edit
`/opt/jarvis/data/schedule.json` and update the SO+RC exam row.
""",
    "_curriculum": """# UAIC FII AI 2025-2028 — Anul I Sem 2 (current semester)

Source: BScIA-2025-2028.pdf (full 3-year curriculum). Fetched 2026-05-08.

## User's 5 active subjects this semester

| Code | Course (RO) | ECTS | Form | Hours/wk (C/S/L/LP) |
|------|-------------|------|------|---------------------|
| AI1201 | Programare orientata-obiect (POO) | 5 | E | 2/0/2/2 |
| AI1202 | Sisteme de operare si retele de calculatoare (SO+RC) | 5 | E | 2/0/2/2 |
| AI1203 | Algebra liniara si optimizare (ALO) | 5 | E | 2/1/1/0 |
| AI1204 | Probabilitati si statistica (PS) | 5 | **V** | 2/1/1/0 |
| AI1205 | Proiectarea algoritmilor (PA) | 5 | E | 2/2/0/0 |
| AI1206 | Limba Engleza II | 5 | V | 0/2/0/0 |
| AI1207 | Educatie fizica | 1 | C | 0/1/0/0 |

Legend: DF=Discipline fundamentale, DC=Discipline complementare,
DS=Discipline de specialitate. C=Curs, S=Seminar, L=Laborator,
LP=Lucrari practice. Fv: E=Examen, C=Colocviu, V=Verificare.

## Key takeaways for the bot

- 4 of 5 active subjects (POO/SO+RC/ALO/PA) are E (formal exam in
  June 1-21 window).
- **PS is V (Verificare) — NO June exam.** PS HW deadline 2026-05-21
  is the LAST evaluation point for PS.
- SO+RC is ONE 5-ECTS combined course (AI1202), but internally split
  into "SO half" + "RC half" with different evaluation tracks
  aggregated into a single final mark. User's catalog uses "SO&RC"
  as the subject string.
- All 5 subjects = 25 ECTS + English II 5 + PE 1 = 31 ECTS for the
  semester. Standard 30-ECTS load.

## Other semester subjects (referenced for context)

This curriculum lists all 6 semesters of the AI program. Year-1
Sem-1 finished (data structures, computer arch+OS, logic, calculus,
intro programming). Year-2 covers databases, graph algos, ML, intro
AI, neural networks, software engineering, data mining, NLP, advanced
programming. Year-3 covers statistical AI, evolutionary, computer
vision, mixed reality, generative AI, robotics, knowledge-based
systems, electives, diploma project.

## Grading split

- Continuous evaluation: 80% of final mark.
- Final exam: 20% of final mark.

Memory note (user_uaic_finals_2026.md): user's continuous-eval half is
locked at 24.5/50 — needs final exam score to compute final grade.

## Materials

- `Operating Systems course.pdf` already on disk in
  `Desktop/Second brain/Operating Systems course.pdf` (full course doc).

## Stubs / fetch incomplete

- Concrete schedule dates not on public page.
- Detailed continuous-eval breakdown behind login.
- Final exam date unknown — falls in 2026-06-01 → 2026-06-21 window.

Action: when user gets the official exam date, edit
`/opt/jarvis/data/schedule.json` and change the relevant placeholder
exam row.
""",
    "_RC_FOLDED": """# RC is folded into AI1202 SO+RC. See _extras/SO/course-info.md.

## Instructors

Alboaie Lenuta, Panu Andrei.

## Grading formula

`N = 0.5 * P + 0.4 * L + 1`

- P = practical project.
- L = lab/seminar evaluation in weeks 4 and 11.
- Pass thresholds: P ≥ 5 AND N ≥ 5.

Lab points (L) awarded for:
- Activity during the laboratories.
- Contributions to Linux kernel, components, libraries, distributions.
- Administration of a public computer network.
- Other: documentation translations, scientific paper contributions.

## Memory note

User memory says `RC half = lab + T.RC June exam`. The notare.php page
does NOT explicitly mention a separate June T.RC exam — it lists P
(project) + L (lab eval) only. Either memory is partial / stale OR the
T.RC is the project P submission, OR there's exam info on a subpage
(restricted access). Verify when user gets official exam slot.

## Stubs

- Schedule with concrete dates not on public page.
- Materials index links exist (cursullaboratorul.php, resurse.php) —
  not yet fetched.
""",
    "PA": """# PA — Proiectarea Algoritmilor (Algorithm Design)

Sources:
- https://edu.info.uaic.ro/proiectarea-algoritmilor/ (fetched 2026-05-08)
- BScIA-2025-2028.pdf curriculum

## Curriculum metadata (from BScIA-2025-2028.pdf)

- Course code: **AI1205**
- Type: DF (Discipline fundamentale)
- ECTS credits: **5**
- Form of verification: **E (Examen)** — has June final exam
- Hours/week: 2 Curs + 2 Seminar (4h total — NO lab, NO LP)
- Online %: C 22, S 22

## Stubs / fetch incomplete

The umbrella site lists `/proiectarea-algoritmilor/` as a registered
discipline page, but the public landing page yielded little structured
content via WebFetch. Probably:
- Detailed info on subpages we haven't crawled.
- Or behind login / restricted access.

## What we know from disk + memory

- Lecture material on disk: `Desktop/Second brain/Courses/PA/` with
  `lecture9_10_np.pdf`, `lecture11_en.pdf`, `lecture11_ro.pdf`.
- Seminar/lab material: `Desktop/Second brain/Labs/PA/seminar9_en.pdf`
  through `seminar10_ro.pdf` + `lecture11_en/ro.pdf`.
- Council `concepts_real/PA.md` contains 36 hand-curated PA concepts
  (Complexity through NP-completeness through Approximation).

## Action

Crawl https://edu.info.uaic.ro/proiectarea-algoritmilor/ subpages on
next session OR ask user to paste current grading formula from their
own copy of the syllabus.
""",
}


def main():
    written = 0
    for subject, body in PAGES.items():
        d = os.path.join(ROOT, subject)
        os.makedirs(d, exist_ok=True)
        path = os.path.join(d, "course-info.md")
        with open(path, "w", encoding="utf-8") as f:
            f.write(body)
        written += 1
    print(f"wrote {written} course-info.md files under {ROOT}/")


if __name__ == "__main__":
    main()
