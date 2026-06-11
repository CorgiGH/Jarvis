# Verified grade models + exam schedule — live course sites, 2026-06-11

Live-site verification sweep over the five IA Year-1 Sem-2 subjects. Every fact below carries its source URL. Cells the live sites do not answer are marked **UNKNOWN** — do not fill them from memory or from the taxonomy doc. Errata in §3 MUST be folded into the design doc's grade-model section (corrects `2026-06-11-artifact-type-taxonomy.md`).

---

## 1. Grade-model registry (VERIFIED)

### 1.1 ALO — Algebra Liniară și Optimizare <a id="alo"></a>

Source: https://edu.info.uaic.ro/algebra-liniara/ (formula quoted verbatim, extracted twice by independent fetches)

**Formula (verbatim):** `Punctaj final = punctaj laborator + 10*nota test seminar + 40*nota test scris`

| Component | Max points | Detail | Source |
|---|---|---|---|
| Laborator (Teme 1–5) | 300 | T1=50 (wk 5), T2=60 (wk 12), T3=60 (wk 12), T4=60 (wk 14), T5=70 (wk 14); +up to 5 pts/tema if submitted ≥1 week early; eval = 40% implementation + 60% presentation/questioning; late = −50%; fraud 2nd offense = −600 pts; teams ≤2, consistent | course page + tema PDFs |
| Test seminar | 100 (10 × nota) | Week 7, 30 min written, covers seminar weeks 2–7 | course page |
| Test scris (exam) | 400 (40 × nota) | 1h, 3–4 exercises, first 30 min printed-docs-only, scale 1–10 | course page |
| **Max total** | **800** | (NOT 1000 — see erratum E1) | reconstruction from formula |

- **Pass condition (both simultaneously):** exam ≥ 3/10 AND total ≥ 360 pts.
- **Weights of 800:** lab 37.5% · seminar test 12.5% · exam 50%.
- **Curve:** Gaussian over PASSING students only — top 10% → 10, next 20% → 9, next 25% → 8, next 25% → 7, next 15% → 6, bottom 5% → 5.
- **Re-exam policy:** **UNKNOWN** (not stated on site).
- **Per-component minimum gates:** only the exam ≥ 3/10 gate; no lab gate stated.

### 1.2 PS — Probabilități și Statistică <a id="ps"></a>

Sources: fisa disciplinei IA PDF §10 (https://edu.info.uaic.ro/probabilitati-si-statistica/files/IA_sem_2_Fisa%20disciplinei_Probabilitati%20si%20statistica.pdf, pdftotext-confirmed) + live page https://edu.info.uaic.ro/probabilitati-si-statistica/ + PS_ro.html

- **Evaluation type: Verificare (V)** — no sit-down exam, no arrears/improvement session (fisa §2.6 + live page; confirmed by zero IA12 schedule rows, §2).

Fisa §10 component table ("Verificare mixtă", total 100 pts, Curs row = 0):

| Component (fisa name) | Points | Re-exam? |
|---|---|---|
| Test teoretic | 50 | Nu |
| Temă de laborator | 17 | Nu |
| Test | 17 | Nu |
| Verificare practică | 16 | Nu |

Live-page bucketing of the same 100 pts (user-facing contract):

| Bucket | Points | Detail |
|---|---|---|
| Seminars | 60 | 6 mini-tests, one per seminar, 15 min each, 10 pts each |
| Labs | 60 | Temas A–D (20) + lab class exercises (20) + final-week statistics test (20: written, 20 min, phone calculator only, F-test excluded) |

- **Minimum gates:** seminar sub-score ≥ 30/60 AND lab sub-score ≥ 30/60 (live page).
- **Re-exam:** "cu reexaminare: Nu" for all four fisa components — failing a gate = course failed, no recovery.
- **Curve:** **UNKNOWN**.
- **Unreconciled:** fisa 4-component split (50+17+17+16) vs live-page 60+60 bucketing — both sum to the same contract but the mapping is not published anywhere (see gap G4).

### 1.3 POO — Programare Orientată pe Obiecte (CS1201) <a id="poo"></a>

Sources:
- **Official course site (user-provided):** https://gdt050579.github.io/poo_course_fii/home.html — administrative page fetched 2026-06-11
- https://edu.info.uaic.ro/fise-discipline/2024-2025/BSc.pdf confirms: type **E (Examen)**, 6 credits, 2h curs + 2h lab/week, Year 1 Sem 2.

**Grade model (verbatim from https://gdt050579.github.io/poo_course_fii/administrative.html):**

| Component | Points | % | Detail |
|---|---|---|---|
| First lab evaluation (week 8) | 30 | 30% | T1 — in-lab, C++ coding |
| Second lab evaluation (week 14 or 15) | 30 | 30% | T2 — in-lab, C++ coding |
| Final evaluation (examination period) | 30 | 30% | Written/practical exam |
| Lab activity | up to 10 | 10% | "up to 1 point for each laboratory and no more than 10 points in total" |
| **Total** | **100** | **100%** | |

- **Pass condition (verbatim):** "Passing is conditioned on gaining a minimum of `45` points" — **total ≥ 45 out of 100**. No per-component minimum gate stated anywhere on the site.
- **Competency requirements for passing (verbatim):** "the capability to write C++ programs based on specifications", "the capability to correctly apply OOP principles (inheritance, polymorphism, etc)", "the ability to understand OO principles/programming-techniques written in C++", "the ability to detect simple errors in a C++ program and understand them."
- **Grade scale (verbatim):** "They are established according to the distribution of the scores of those promoted: the sum of the scores obtained at the laboratories and the ones from the tests." ECTS percentages (top 10% → 10, next 25% → 9, next 30% → 8, next 25% → 7, lowest 10% → 6) "adjusted accordingly" if distribution is non-normal.
- **Re-exam policy (verbatim):** "Previous results obtained on past OOP courses/labs will NOT be equated. You will need to take both the lab and course tests to pass the OOP exam." (No other re-exam detail stated.)
- **Attendance gate:** **UNKNOWN** — not stated on the site.
- **Final exam format** (MCQ? written? coding? Google Form? item count? duration?): **UNKNOWN** — not stated on the site. The schedule (§2) has this as "Examen scris" 09.06.2026 (2h, rooms C2/C210/C309/C412/C413) and "Test practic" 16.06.2026 (4h, 10 rooms) — suggesting two distinct components, consistent with T1/T2/Final shape.
- **Lab test format** (T1/T2 — coding problem count, duration, open-book?): **UNKNOWN** — not stated on the site.
- **Fisa disciplinei PDFs:** Both `FisaDisciplineiPOO2023-2024_Info.pdf` and `FisaDisciplineiPOO2023-2024_En_Info.pdf` linked from the course site return HTTP 404. No fisa for 2024-2025 or 2025-2026 on edu.info.uaic.ro (E13/E14 unchanged on that point).
- Low-confidence ~2022-era web-search variant (3 projects + week-14 practical + 18-question written, ≥5 each): **CONTRADICTED** by the official site's 4-component 100-pt model — discard.

### 1.4 PA — Proiectarea Algoritmilor (CS1205) <a id="pa"></a>

Sources: fisa 2024-2025 RO (https://edu.info.uaic.ro/fise-discipline/2024-2025/Proiectarea-algoritmilor-Licenta-Informatica.pdf) + fisa 2025-2026 EN (https://edu.info.uaic.ro/fise-discipline/2025--2026/2025-Licen%C8%9B%C4%83-Informatic%C4%83(%C3%AEn%20limba%20englez%C4%83)/InfoEng_sem_2_Fisa%20disciplinei_Proiectarea%20Algoritmilor.pdf), both read via PyPDF2. Type **E (Examen)**, 5 credits.

Fisa 2024-2025 RO §10:

| Component | Weight | Source detail |
|---|---|---|
| Teste scrise (curs) | 70% | §10.4 verbatim "Teste scrise 70%" |
| Seminar | 30% | §10.5 "10% prezența, 20% activitate" |

Fisa 2025-2026 EN §10 (different structure — see erratum E10):

| Component | Points (of 100) | Re-exam? |
|---|---|---|
| Course continuous: Practical Test 50% + Theoretical Test 50% of block | 65 × 50% = 32.5 | Yes (both written tests) |
| Seminar continuous (oral, 100% of block) | 65 × 50% = 32.5 | No |
| Final written assessment | 35 | — |

- **Pass condition (both fise):** ≥ 45 points total of 100. No per-component gate; "failure to pass continuous assessment results in failure to pass final assessment: No" for both sub-blocks (2025-26 EN fisa).
- **Partial exam slot:** week 8 = "Evaluare parțială — Test scris" (fisa 2024-25 §8.1); 2025-26 partial window 06.04–12.04.2026 (https://edu.info.uaic.ro/orar-examene/partiale/).
- **NOT in any fisa:** "35 pts per test", per-test duration, topic split (Greedy/DP/NP), Alk-mandatory, retake format. Those facts are corpus-only (Alex's 2015-2018 past papers) — keep them, but tag them corpus-evidence, not official (errata E10–E12).

### 1.5 SORC — Sisteme de Operare și Rețele de Calculatoare (ONE subject, SO + RC halves) <a id="sorc"></a>

Source: https://profs.info.uaic.ro/georgiana.calancea/so-rc-evaluation.html (verbatim)

**Combined formula:** `NF = 0.1*SO1 + 0.3*SO2 + 0.1*SO3 + 0.1*RC1 + 0.4*RC2` → SO half 0.5, RC half 0.5.

**Pass conditions:** RC2 ≥ 5 AND NF ≥ 5. RC2 retakeable in the re-examination session.

| Component | Weight in NF | What it is | Gate | Source |
|---|---|---|---|---|
| SO1 | 0.1 | **UNKNOWN** — SO sub-site https://edu.info.uaic.ro/operating-systems-and-computer-networks/SO/index.html returned HTTP 401 | none stated | so-rc-evaluation.html |
| SO2 | 0.3 | **UNKNOWN** (same 401) | none stated | so-rc-evaluation.html |
| SO3 | 0.1 | **UNKNOWN** (same 401) | none stated | so-rc-evaluation.html |
| RC1 | 0.1 | practical lab marks incl. code-review sessions (weeks 11–14) + other lab activities | none stated | so-rc-evaluation.html |
| RC2 | 0.4 | written exam, Computer Networks module, weeks 9–14 content | ≥ 5, retakeable | so-rc-evaluation.html |

- Main page (https://edu.info.uaic.ro/operating-systems-and-computer-networks/): "Only the final exam component can be retaken during makeup and grade improvement sessions" — consistent with RC2-only retake. SO-component re-exam policy: **UNKNOWN**.
- Older standalone-RC formula on https://profs.info.uaic.ro/georgiana.calancea/evaluation.html: `N = 0.5*P + 0.4*L + 1` (P = practical project ≥ 5; L = lab mark incl. week-5/week-11 evaluations). Does NOT match NF — treat so-rc-evaluation.html as the authoritative merged-course contract.
- Corpus-only T.SO claims (week 8, 30 pts, 12-pt gate, practical C/bash + 18-item MCQ Google Form): **UNVERIFIABLE this sweep** (401) — neither confirmed nor contradicted (erratum E9).

---

## 2. IA12 exam schedule (official, verified) <a id="ia12-schedule"></a>

Source: https://edu.info.uaic.ro/orar-examene/sesiune/participanti/orar_IA12.html — "Orar Inteligență artificială, Anul 1, Grupa 2", session 01.06.2026–28.06.2026, generated by eOra 3.1.9. Past/upcoming relative to 2026-06-11. **12 dated rows.**

| # | Subject | Raw name | Type | Date | Day | Time | Room(s) | Status |
|---|---|---|---|---|---|---|---|---|
| 1 | ALO | Algebră liniară și optimizare | Examen | 03.06.2026 | Wed | 16–18 | C2 | past |
| 2 | SORC | Sisteme de operare și rețele de calculatoare | Examen | 06.06.2026 | Sat | 11–13 | C2 | past |
| 3 | POO | Programare orientată-obiect | Examen (scris) | 09.06.2026 | Tue | 08–10 | C2, C210, C309, C412, C413 | past |
| 4 | — | Educație fizică | Examen | 09.06.2026 | Tue | 16–18 | Teren Sport | past |
| 5 | PA | Proiectarea algoritmilor | Examen | 10.06.2026 | Wed | 12–14 | C112, C2, C308, C309 | past (yesterday) |
| 6 | POO | Programare orientată-obiect | Test practic | 16.06.2026 | Tue | 08–12 | C210, C308, C309, C401, C403, C405, C409, C411, C412, C413 | upcoming (+5d) |
| 7 | — | Pedagogie 1 | Examen (facultativ) | 18.06.2026 | Thu | 09–11 | C2 | upcoming (+7d) |
| 8 | SORC | Sisteme de operare și rețele de calculatoare | Restanță | 20.06.2026 | Sat | 10–12 | C2 | upcoming (+9d) |
| 9 | ALO | Algebră liniară și optimizare | Restanță | 22.06.2026 | Mon | 12–14 | C2 | upcoming (+11d) |
| 10 | (see note) | Reverse Engineering | Restanță | 22.06.2026 | Mon | 19–20 | C309 | upcoming (+11d) |
| 11 | PA | Proiectarea algoritmilor | Restanță | 23.06.2026 | Tue | 10–12 | C112, C2, C308, C309 | upcoming (+12d) |
| 12 | POO | Programare orientată-obiect | Restanță | 25.06.2026 | Thu | 18–20 | C2, C308, C309 | upcoming (+14d) |

Absent from the schedule:
- **PS** — zero rows, consistent with Verificare (no sit-down exam).
- **Mărire/second-session dates** — not on this page.

Row-10 caution: the sweep mapped "Reverse Engineering" to "RC"; but "Reverse Engineering" is not "Rețele de Calculatoare" — RE is plausibly an unrelated optional discipline. There is no standalone RC exam in this curriculum anyway (RC2 sits inside the SORC slot, rows 2/8). Do NOT register row 10 as the RC exam without confirming what "Reverse Engineering" is for IA12 (gap G13).

---

## 3. Taxonomy errata (fold into design doc grade-model section)

Each entry: old claim → verdict → evidence. Confirmed-as-is taxonomy claims (ALO 300-pt temas, early-bonus, exam format, Gaussian-over-passers, week-7 seminar test, 360-pt pass condition, zero ALO past papers, website-only deadlines; PA seminar 30%, min-45 floor; PS Verificare/no-re-exam) are NOT listed — they need no edit.

| # | Old claim (taxonomy) | Verdict | Evidence |
|---|---|---|---|
| E1 | ALO "total = 1000 pts" | **CONTRADICTED** — max = 300 + 10×10 + 40×10 = **800** | formula verbatim at https://edu.info.uaic.ro/algebra-liniara/ |
| E2 | ALO "exercitii/ folder = exam-problem source" | **MISLEADING** — files live at `seminar/exercitii N.pdf` (no `exercitii/` folder) and are weekly SEMINAR problem sets (headed "Seminar 1, 2025-2026"), not past exam questions; they are merely what the week-7 seminar test draws from | https://edu.info.uaic.ro/algebra-liniara/seminar/exercitii%201.pdf |
| E3 | PS "Test teoretic (50 pts, single biggest item), FORMAT entirely unknown → ask Alex to upload a past paper" | **WRONG FRAMING** — the live page never uses "Test teoretic"; the activity is **6 × 15-minute in-seminar mini-tests** (6×10=60 pts), not one sitting; there is no single past paper to upload; format is now KNOWN — remove the ask-user row | fisa §10 names "Test teoretic 50"; live page https://edu.info.uaic.ro/probabilitati-si-statistica/ describes the 6 mini-tests |
| E4 | SORC "RC weights UNKNOWN" | **RESOLVED** — RC1 = 0.1 of NF, RC2 = 0.4 of NF, gate RC2 ≥ 5 (retakeable) | https://profs.info.uaic.ro/georgiana.calancea/so-rc-evaluation.html |
| E5 | SORC "RC has no grading doc at all — full unknown" | **CONTRADICTED** — grading docs exist | so-rc-evaluation.html + https://profs.info.uaic.ro/georgiana.calancea/evaluation.html |
| E6 | SORC "how the two halves combine — UNKNOWN" | **RESOLVED** — `NF = 0.1*SO1 + 0.3*SO2 + 0.1*SO3 + 0.1*RC1 + 0.4*RC2`, 50/50 split | so-rc-evaluation.html (verbatim) |
| E7 | SORC "RC labs 5+8 missing" | **CLARIFIED** — Lab 5 exists (week-5 homework-evaluation session, no PDF expected); Lab 8 is a numbering skip (list jumps 7→9), genuinely absent; new so-rc numbering maps old "Lab 8" slot to so-rc lab 2 (UDP) | https://profs.info.uaic.ro/georgiana.calancea/laboratories.html + so-rc-laboratories.html |
| E8 | SORC "RC exam format — everything unknown" | **PARTIALLY RESOLVED** — RC2 = written exam covering weeks 9–14; duration/question types/open-book still UNKNOWN | so-rc-evaluation.html |
| E9 | SORC "T.SO week 8, 30 pts, 12-pt gate, practical+MCQ" | **DOWNGRADE to corpus-evidence** — live site neither confirms nor denies (SO sub-site HTTP 401); only the 0.1/0.3/0.1 NF weights are official; no per-SO-component gate stated anywhere accessible | so-rc-evaluation.html + 401 on …/SO/index.html |
| E10 | PA "2 × 35-pt written tests" (as the whole model) | **PARTIAL + STRUCTURE GAP** — "35 pt" appears in no fisa; 2024-25 RO says "Teste scrise 70%"; 2025-26 EN restructures to **65% continuous (Practical Test + Theoretical Test + seminar oral) + 35% final written assessment** — a three-component shape the taxonomy lacks; unresolved whether this is a real 2025-26 restructuring (RO 2025-26 fisa not found to cross-check) | both PA fise §10 |
| E11 | PA "Alk pseudocode mandatory" | **DOWNGRADE to corpus-evidence** — not in any fisa; comes from past-paper inspection only | both PA fise (absence) |
| E12 | PA "retake = single 70-pt full-syllabus paper" | **DOWNGRADE to corpus-evidence** — fisa says reexamination: Yes for written tests, but no format/points | PA fisa 2025-26 EN §10 |
| E13 | POO "course MCQ 30 + lab T1/T2 30+30, ≥20 combined gate" | **PARTIALLY CONFIRMED + PARTIALLY CONTRADICTED** — The official course site (https://gdt050579.github.io/poo_course_fii/administrative.html) confirms T1 (week 8, 30 pts) + T2 (week 14/15, 30 pts) + Final exam (30 pts) + lab activity (10 pts), total 100. Points-per-component CONFIRMED (T1=30, T2=30). BUT: (a) "≥20 combined gate" for T1+T2 — NOT STATED on site; the only gate is total ≥ 45; (b) "course MCQ Google Form" — format NOT STATED; (c) the ~2022-era 3-project variant is CONTRADICTED by the official model. | https://gdt050579.github.io/poo_course_fii/administrative.html (fetched 2026-06-11; user-provided URL) |
| E14 | POO "attendance ≥ 10 gate" | **NOT STATED** — the official course site does not mention any attendance threshold; cannot confirm or contradict from official sources | https://gdt050579.github.io/poo_course_fii/administrative.html (fetched 2026-06-11) |
| E15 | (schedule-derived) RC treated as having its own regular exam | **CORRECTED** — IA12 schedule has NO regular-session RC row; RC2 is examined inside the single SORC slot (06.06); the lone "Reverse Engineering" restanță row (22.06) is of ambiguous identity and must not be assumed to be Rețele de Calculatoare | orar_IA12.html |

**Errata count: 15.**

---

## 4. Resources discovered (for the digestion pipeline)

All URLs confirmed live this sweep (POO site added 2026-06-11). **Total: 140 items** (108 prior-sweep + 32 POO course site).

### ALO — 25
- Lectures (13): `https://edu.info.uaic.ro/algebra-liniara/curs/ALO%20curs%2001.pdf` … `ALO%20curs%2013.pdf` (curs 01 header: "Anca Ignat & Corina Forascu")
- Seminar sets (7): `https://edu.info.uaic.ro/algebra-liniara/seminar/exercitii%201.pdf` … `exercitii%207.pdf` (pen-and-paper, no solutions — the week-7 test pool)
- Lab temas (5): `https://edu.info.uaic.ro/algebra-liniara/lab/Tema%201.pdf` … `Tema%205.pdf` (machine precision / secant minimization / LU / Householder QR / Jacobi+SVD) — **closes prior gap: tema PDFs + point values + week deadlines were not in the corpus**

### PS — 59
- Lectures (26): `ps1.pdf`–`ps13.pdf` + `ps1_en.pdf`–`ps13_en.pdf` at `https://edu.info.uaic.ro/probabilitati-si-statistica/files/`
- Statistics lab sheets (12): `lab_stat1.pdf`–`lab_stat6.pdf` + `_en` variants
- Homework specs (8): `Tema_A.pdf`–`Tema_D.pdf` + `_en` variants
- Lab data (8): lab4 — `unemployment.csv`, `sample1.txt`, `sample2.txt`, `life_expect.csv`, `unemploy2012.csv` at `…/files/data/lab4/`; lab5 — `…/files/data/lab5/history.txt`; lab6 — `…/files/data/lab6/history.txt`, `program.txt`
- Fise disciplinei (3): IA / InfoRo / InfoEng PDFs at `…/files/` — **closes gap: authoritative §10 component table**
- Grades (2): live spreadsheet `https://docs.google.com/spreadsheets/d/e/2PACX-1vRjgsXv2H3J3rpg1CsS9MI8uNPxmlkuFgd-h2Zxii6QzBnypfme0jRWY4DgeI3TPyBKrfWm0GcFKqkL/pubhtml` + `…/files/PS_note.pdf`

### SORC — 18
- RC lectures (6): `Curs1_De_la_procese_la_retele_RO.pptx.pdf`, `Curs2_Programare_in_retea_1.pptx.pdf`, `Curs3_…_2.pptx.pdf`, `Curs4_…_3.pptx.pdf`, `Curs5_DNS_NivelulAplicatie.pptx.pdf`, `Curs6_HTTP_WebSockets_MCP.pptx.pdf` at `https://profs.info.uaic.ro/georgiana.calancea/`
- RC lab PDFs (10): `Laboratorul_1/2/3/4/6/7/9/10/12/13.pdf` (same base URL; labs 5/11/14 are eval/presentation sessions with no PDF) — **closes the "labs 5/8 missing" question: everything that exists is now enumerated**
- New-format lab page (1): `https://adriancert.github.io/operating-systems-and-computer-networks/networking/lab1/`
- Fisa (1): `https://edu.info.uaic.ro/operating-systems-and-computer-networks/Fisa-SO+RC_ro.pdf` (242 KB; needs local pdftotext — WebFetch couldn't parse it)

### PA — 4
- PA fisa 2024-25 RO: `https://edu.info.uaic.ro/fise-discipline/2024-2025/Proiectarea-algoritmilor-Licenta-Informatica.pdf`
- PA fisa 2025-26 EN: `https://edu.info.uaic.ro/fise-discipline/2025--2026/2025-Licen%C8%9B%C4%83-Informatic%C4%83(%C3%AEn%20limba%20englez%C4%83)/InfoEng_sem_2_Fisa%20disciplinei_Proiectarea%20Algoritmilor.pdf`
- BSc plan: `https://edu.info.uaic.ro/fise-discipline/2024-2025/BSc.pdf` (course codes/credits/types for CS1201 + CS1205)
- PA course site (exercise sheets + materials hub): `https://sites.google.com/view/fii-pa`
- Alk interpreter: `https://github.com/alk-language/java-semantics`

### POO — 32 (official course site, user-provided 2026-06-11)
Source: https://gdt050579.github.io/poo_course_fii/ — base URL `https://gdt050579.github.io/poo_course_fii/`

- Course PDFs (13): `courses/Course-1.pdf` … `courses/Course-12.pdf` (Introduction / C++ Language specifiers / Creating an object / Operators / Inheritance / Templates / STL (1) / STL (2) / Advances beyond C++98 / Lambda expressions / Exceptions / Design Patterns) + `courses/cpp_to_rust.pdf` (extra: From C++ to Rust)
- Lab HTML pages (19): `labs/lab01.html` … `labs/lab12.html` (12 numbered) + `labs/lab01_extra.html`, `labs/lab02_extra.html`, `labs/lab03_extra.html`, `labs/lab05_extra.html`, `labs/lab06_extra.html`, `labs/lab07_extra.html` (6 "extra" labs, no lab04/lab08–12 extras)
- Fisa disciplinei PDFs: **both 404** — `FisaDisciplineiPOO2023-2024_Info.pdf` + `FisaDisciplineiPOO2023-2024_En_Info.pdf` listed on home page but return HTTP 404
- Past/sample exam papers: **ZERO** on this site

### Schedule — 1
- `https://edu.info.uaic.ro/orar-examene/sesiune/participanti/orar_IA12.html` — **closes gap: first absolute exam-date table in the corpus**

Past/sample exam papers: **ZERO found on any official site for any subject** (confirmed, not just not-found).

**Total resources: 140** (108 prior + 32 POO course site items).

---

## 5. Remaining genuine gaps (the live sites don't have these)

| # | Gap |
|---|---|
| G1 | ALO re-exam (restanță) grading policy — not stated on the course page |
| G2 | ALO past exam papers — none exist on the official site |
| G3 | PS probability-half seminar sheets + probability-half lab sheets (labs 1–6) — not linked anywhere on the page |
| G4 | PS fisa 4-component split (50/17/17/16) vs live-page 60/60 bucketing — exact mapping unpublished |
| G5 | PS per-seminar-test topics and materials/open-book policy — not stated; zero sample tests |
| G6 | PS grade curve — not stated |
| G7 | SORC SO1/SO2/SO3 definitions — SO sub-site behind HTTP 401; T.SO week/points/gate unverifiable officially |
| G8 | SORC RC2 detailed format (duration, MCQ vs open, materials) — only "written exam, weeks 9–14" is published |
| G9 | SORC SO-component re-exam policy — unknown (401) |
| G10 | PA per-test point split, duration, and topic coverage — fisa gives only aggregate percentages; retake format unstated |
| G11 | PA 2025-26 RO fisa missing — cannot cross-check the EN fisa's 65/35 three-component restructuring |
| G12 | POO official grade model — **PARTIALLY RESOLVED**: official course site (user-provided 2026-06-11) confirms 4-component 100-pt structure (T1 30 + T2 30 + Final 30 + Lab activity 10, pass ≥ 45 total). Still open: final exam format (MCQ? written? coding? duration?), T1/T2 in-lab format, attendance gate, fisa disciplinei (both PDFs 404). |
| G13 | "Reverse Engineering" restanță row (22.06) — identity ambiguous; no regular-session RC row for IA12 to compare against |
| G14 | Mărire (grade-improvement) session dates — not on the IA12 schedule page |
| G15 | Past/sample papers for every subject — none published officially anywhere |
