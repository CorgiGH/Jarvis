# Phase 1 Card-Cull Audit — live FSRS corpus

**Date:** 2026-06-03  
**DB:** `C:\Users\User\.jarvis\tutor.db` (opened READ-ONLY via `file:...?mode=ro`; nothing was deleted or modified)  
**Table:** `fsrs_cards` — 871 rows, all `source='MANUAL'`  
**Policy:** KEEP-MOST. Cull only (a) clear non-knowledge admin/grading/syllabus/logistics/deadline/calendar cards and (b) clear duplicates. When in doubt -> KEEP.

## Result summary

- **junk = 35**
- **dupe-cull = 8**
- **total_cull = 43** (union; junk and dupe-cull overlap = 0)
- **resulting = 871 - 43 = 828**
- **>=800 OK:** 828 >= 800 -> **YES**

Note vs the master-plan estimate ("16 junk + 6 exact dupes -> 849"): there are ZERO exact `(front,back)` duplicates in the corpus, and the real admin/junk surface is larger than 16 (it is 35 cards). The locked invariant is the floor of 800, not 849; this conservative cull lands at 828, comfortably above the floor.

## Method

1. Pulled all 871 `(id, source_ref, front, back, last_reviewed_at, lapses)` rows. Cards span 6 subjects (SO 216, RC 192, ALO 176, PS 143, POO 80, PA 64), authored ~8 cards per source file.
2. **JUNK pass.** Ran a broad bilingual (RO/EN) keyword scan (grading/points/exam/deadline/week/syllabus/filename), then hand-adjudicated EVERY flagged card. Most keyword hits are false positives (math "minimum/critical point", "point-to-point", "fork point", Bernoulli "succes/esec", "pass pipe FDs", constexpr "compile-time evaluation", "Las Vegas algorithms the course classifies", "random point evaluation / error probability") - all genuine subject knowledge, KEPT. A card is JUNK only if its *testable content* is course-administration metadata (grading point-values, pass thresholds, exam scheduling, deadlines, week->topic calendar mapping, lecture/lab counts) that does not transfer beyond this specific course offering.
3. **DUPLICATE pass.** Key = normalized front (lower-case, RO diacritics folded s/t/a, punctuation stripped, whitespace collapsed), regardless of back. Keep the lowest `id` in each group. (All 871 cards share bulk-import `last_reviewed_at` timestamps - 80 distinct values, one per import batch - so review history is NOT a usable tiebreaker; lowest-id is the deterministic keep rule.) One group ("what does [] / [=] / [&] capture") was a **normalization false-positive** - the distinguishing `[]`/`[=]`/`[&]` tokens were stripped; those three C++ lambda-capture cards have different answers and are KEPT.
4. Final cull set = union(junk, dupe-cull), de-duplicated. The two sets do not overlap.
5. Sanity: resulting count >= 800 (MIN_EXPECTED_CARDS, `tools/db-backup.py`).

### Distinction applied (grading-scheme vs concept)
- JUNK: "What is the minimum score to pass the OOP exam?", "Which week covers Bash scripting?", "How is T1 computed?" - the answer is a point value / schedule slot / pass rule.
- KEPT: "How is closeness centrality calculated?", "What does the Law of Large Numbers state?", "Compare TCP and UDP" - the answer is a transferable concept even when it mentions points/weeks.
- KEPT borderline: RC cards framed "Week 9/10 covers X" whose ANSWER is real (socketpair/AF_UNIX; UDP recvfrom/sendto; AF_UNIX/AF_INET/AF_INET6; DNS/HTTP/WebSockets) - the week is incidental scaffolding, the fact transfers. KEEP-MOST.

## (a) JUNK table

| id | source_ref | front | reason |
|----|-----------|-------|--------|
| `01KS037V6PVYYSQJXHW0J7W3FH` | POO:poo_c1.md | What are the four grading components for the OOP exam and their point values? | Grading scheme: OOP exam grading components and point values (course-admin, not subject knowledge). |
| `01KS037V7TRJZS07YD77KFHB9A` | POO:poo_c1.md | What is the minimum total score required to pass the OOP exam? | Grading scheme: minimum total score to pass OOP exam (course-admin). |
| `01KS03CS4S1JCHXYR6G0BZX6CZ` | ALO:alo_c01.md | ALO course: evaluation breakdown? | Grading scheme: ALO course evaluation breakdown and pass thresholds (course-admin). |
| `01KS0Z80WVSWHA9MWXT4NXVAE2` | PA:Algorithm Design - Evaluation | How many tests make up 70 points in Algorithm Design evaluation? | Grading scheme: how 70 points split across two tests (course-admin). |
| `01KS0Z80XDXHAPD027V0RAQ4HC` | PA:Algorithm Design - Evaluation | What is the maximum score for seminar active participation? | Grading scheme: max points for seminar active participation (course-admin). |
| `01KS0Z80XX3ZDQD84JPSQ25M21` | PA:Algorithm Design - Evaluation | How are seminar attendance points awarded? | Logistics: how seminar attendance points are awarded (course-admin). |
| `01KS0Z80YC1G3TRJV2E13PCA5Y` | PA:Algorithm Design - Evaluation | What is the minimum passing score out of 100? | Grading scheme: minimum passing score out of 100 (course-admin). |
| `01KS0Z80YT2CRDX5XKZKB23Q3E` | PA:Algorithm Design - Evaluation | Name the four key competencies tested by exams. | Exam-philosophy/syllabus meta: the four competencies an exam tests (course-admin). |
| `01KS0Z80Z82NTHF1FGKQB76CDY` | PA:Algorithm Design - Evaluation | What aspects of understanding do tests verify? | Exam-philosophy/syllabus meta: what aspects of understanding tests verify (course-admin). |
| `01KS0Z80ZPC4K1B2532M2W2DFG` | PA:Algorithm Design - Evaluation | How is the overdue/makeup session structured? | Exam logistics: structure of overdue/makeup session (course-admin). |
| `01KS0Z810355G47EHKJRH0RKBC` | PA:Algorithm Design - Evaluation | How are final grades determined after minimum standards met? | Grading scheme: how final grades are determined post-threshold (course-admin). |
| `01KS03XB5R800M7PGB10APW3Z2` | PS:ps1.md | Cât de mulți cursuri, seminarii și laboratoare sunt în curs? | Syllabus meta: count of lectures/seminars/labs in the course. |
| `01KS03XB79TXAWS5F1PT69V0QP` | PS:ps1.md | Care este nota minimă pentru promovare în T1? | Grading scheme: minimum pass score for T1 (course-admin). |
| `01KS03XB7QXTCJDQGT06F0GK2C` | PS:ps1.md | Cum se calculează T1? | Grading scheme: how T1 is computed from 6 tests x 10pts (course-admin). |
| `01KS03XB847ZEP9DWB77F4DGJ0` | PS:ps1.md | Componentele T2 și punctajul fiecăreia? | Grading scheme: T2 components and their point values (course-admin). |
| `01KS03XB8J6PVPM5H25DMZS1WC` | PS:ps1.md | Când se predau temele de laborator? | Deadline: when lab homework is submitted, week 12 (course-admin). |
| `01KS03XB90RYRK7VDTZGT5G4A8` | PS:ps1.md | Ce se studiază în primele 6 săptămâni? | Syllabus/calendar meta: what is studied in first 6 weeks (schedule slot label). |
| `01KS03XB9D8C038TMZ9JZME73X` | PS:ps1.md | Ce se studiază în ultimele 7 săptămâni? | Syllabus/calendar meta: what is studied in last 7 weeks (schedule slot label). |
| `01KS03XB9V8WGGAP0W4P76BYF8` | PS:ps1.md | Există sesiune de restanțe? | Exam logistics: whether a make-up/retake session exists (course-admin). |
| `01KS104GYVXC6T4K00S9NVEV3B` | SO:Operating Systems course.md | Name the two components of the SO&RC course and when each is taught. | Course-structure meta: which half of semester teaches SO vs RC (scheduling metadata). |
| `01KS104GZMCC7WBT4HFS1F37P3` | SO:Operating Systems course.md | What is the maximum points for Linux installation practical and what does a positive score represent? | Grading scheme: max points for Linux-install practical and pass meaning (course-admin). |
| `01KS104H0A54GA50FYD06QF0GS` | SO:Operating Systems course.md | What is maximum bonus points for active lab participation weeks 1-7? | Grading scheme: max bonus points for lab participation weeks 1-7 (course-admin). |
| `01KS104H0RWE2CMAWZ4D5964VT` | SO:Operating Systems course.md | When is SO test (T.SO) administered and how many points is it worth? | Exam logistics: when T.SO test is administered and its point value (course-admin). |
| `01KS104H15F8RASK1A6KYRMN1S` | SO:Operating Systems course.md | What is passing requirement for RC exam (T.RC) and its weight in final grade? | Grading scheme: RC exam pass requirement and weight in final grade (course-admin). |
| `01KS104H1JA9DZCESQDSBQYK5A` | SO:Operating Systems course.md | Calculate formula for continuous evaluation score (Total_EvalSem). | Grading scheme: continuous-evaluation score formula (course-admin). |
| `01KS104H1XV49V3709880V9FGZ` | SO:Operating Systems course.md | What is weighting distribution between continuous evaluation and RC exam in final grade? | Grading scheme: 60/40 weighting between continuous eval and RC exam (course-admin). |
| `01KS104H2A0GH68ZJ2VF5TYR09` | SO:Operating Systems course.md | What is minimum final grade required to pass the SO course? | Grading scheme: minimum final grade to pass SO course (course-admin). |
| `01KS0ZY24C72J3YNK7BRT341BK` | SO:calendar.md | What OS management techniques are paired in Week 6? | Calendar: which OS topics are paired in Week 6 (scheduling metadata). |
| `01KS0ZY24TS9W2W2ADKYAT0G1F` | SO:calendar.md | Name the final OS topic covered before exam week (Week 8). | Calendar: final OS topic before exam week / Week 7-8 mapping (scheduling metadata). |
| `01KS0ZY25E65HTQ78642BE1FQM` | SO:calendar.md | Which week introduces sockets and transitions from local IPC to network sockets? | Calendar: which week introduces sockets, Week 9 (scheduling metadata). |
| `01KS0ZY26013RT8XD2WNMFQ4SS` | SO:calendar.md | UDP and TCP network programming are taught in which consecutive weeks? | Calendar: which weeks teach UDP/TCP, 10/11 (scheduling metadata). |
| `01KS0ZY26H0CYXQDXABWRJZX00` | SO:calendar.md | What networking architecture does Week 12's multiplexing enable? | Calendar: Week 12 multiplexing architecture mapping (scheduling metadata). |
| `01KS0ZY2718576XQB4ZXFGCYYH` | SO:calendar.md | What protocols/standards are covered immediately before HTTP (Week 14)? | Calendar: what precedes HTTP in Weeks 13-14 (scheduling metadata). |
| `01KS0ZY27EK7N887BAWPSDY737` | SO:calendar.md | What are the two IPC mechanisms taught in Week 7? | Calendar: which week teaches the two IPC mechanisms, Week 7 (scheduling metadata). |
| `01KS0ZY27SKRB4NJNZCTDTD0EQ` | SO:calendar.md | Which week covers Bash scripting in the OS portion? | Calendar: which week covers Bash scripting, Week 3 (scheduling metadata). |

Junk subtotal: **35**.

## (b) DUPLICATE table

Key = normalized front (diacritics folded, punctuation stripped, whitespace collapsed); kept = lowest id.

| group | normalized_front | kept_id (source) | culled_id(s) (source) |
|-------|------------------|------------------|------------------------|
| 1 | `what does exec () do` | `01KS03MZMZE1Y9X4WZ2PXYN3PT` (SO:week05_processes.md) | `01KS0ZZ3W391RQ439ABVY4MNYC` (SO:oslab05_processes_guide.md); `01KS1004DGPK057JBRAYZKS8WC` (SO:so-lab05.md) |
| 2 | `how do you prove clique is np complete` | `01KS02ZJQRAXKNFG119HY2ZGQS` (PA:seminar10_en.md) | `01KS030V3C862QHBP3S8879RTF` (PA:seminar10_ro.md) |
| 3 | `state the decision version of the knapsack problem` | `01KS02ZJRSSNAQ9ES3HEKB6YNA` (PA:seminar10_en.md) | `01KS030V48EBH2KHD0DFM3DAY4` (PA:seminar10_ro.md) |
| 4 | `define the partition problem` | `01KS02ZJT899W83GPVFNGV27P3` (PA:seminar10_en.md) | `01KS030V5GBFWQPEYH6APPG4GP` (PA:seminar10_ro.md) |
| 5 | `what is a zombie process` | `01KS03MZJWJSGKQW7RJP7ZQDAA` (SO:week05_processes.md) | `01KS0ZSND8QAJBZR5T5YRR3D2Y` (RC:rc_lab2.md) |
| 6 | `what is an orphan process` | `01KS03MZKDDAKTXHCKNC6G6A01` (SO:week05_processes.md) | `01KS0ZSNDRZERQ762E7PAHAG04` (RC:rc_lab2.md) |
| 7 | `what does the law of large numbers state` | `01KS0404MN780XN100GWG5RR6X` (PS:ps6.md) | `01KS0ZERWGRX5GNNHVCZYWSYMY` (PS:Tema_A.md) |

Dupe-cull subtotal: **8** (7 real groups; group 1 "exec()" has 2 culls).

Excluded as a normalization false-positive (KEPT, distinct answers): `01KS038NV0Y4DP92SPCXEE5JWJ` `[]`, `01KS038NVDZSPD5ECEWQA017XH` `[=]`, `01KS038NVTCZ7V8C0BF05761TQ` `[&]` - C++ lambda-capture cards from POO:poo_c10.md.

## (c) FINAL cull list (card IDs to DELETE)

43 ids (35 junk + 8 dupe-cull, no overlap):

```
01KS037V6PVYYSQJXHW0J7W3FH
01KS037V7TRJZS07YD77KFHB9A
01KS03CS4S1JCHXYR6G0BZX6CZ
01KS0Z80WVSWHA9MWXT4NXVAE2
01KS0Z80XDXHAPD027V0RAQ4HC
01KS0Z80XX3ZDQD84JPSQ25M21
01KS0Z80YC1G3TRJV2E13PCA5Y
01KS0Z80YT2CRDX5XKZKB23Q3E
01KS0Z80Z82NTHF1FGKQB76CDY
01KS0Z80ZPC4K1B2532M2W2DFG
01KS0Z810355G47EHKJRH0RKBC
01KS03XB5R800M7PGB10APW3Z2
01KS03XB79TXAWS5F1PT69V0QP
01KS03XB7QXTCJDQGT06F0GK2C
01KS03XB847ZEP9DWB77F4DGJ0
01KS03XB8J6PVPM5H25DMZS1WC
01KS03XB90RYRK7VDTZGT5G4A8
01KS03XB9D8C038TMZ9JZME73X
01KS03XB9V8WGGAP0W4P76BYF8
01KS104GYVXC6T4K00S9NVEV3B
01KS104GZMCC7WBT4HFS1F37P3
01KS104H0A54GA50FYD06QF0GS
01KS104H0RWE2CMAWZ4D5964VT
01KS104H15F8RASK1A6KYRMN1S
01KS104H1JA9DZCESQDSBQYK5A
01KS104H1XV49V3709880V9FGZ
01KS104H2A0GH68ZJ2VF5TYR09
01KS0ZY24C72J3YNK7BRT341BK
01KS0ZY24TS9W2W2ADKYAT0G1F
01KS0ZY25E65HTQ78642BE1FQM
01KS0ZY26013RT8XD2WNMFQ4SS
01KS0ZY26H0CYXQDXABWRJZX00
01KS0ZY2718576XQB4ZXFGCYYH
01KS0ZY27EK7N887BAWPSDY737
01KS0ZY27SKRB4NJNZCTDTD0EQ
01KS0ZZ3W391RQ439ABVY4MNYC
01KS1004DGPK057JBRAYZKS8WC
01KS030V3C862QHBP3S8879RTF
01KS030V48EBH2KHD0DFM3DAY4
01KS030V5GBFWQPEYH6APPG4GP
01KS0ZSND8QAJBZR5T5YRR3D2Y
01KS0ZSNDRZERQ762E7PAHAG04
01KS0ZERWGRX5GNNHVCZYWSYMY
```

## (d) Ready-to-use DELETE statement

Parameterized form (preferred - bind the 43 ids as parameters):

```sql
DELETE FROM fsrs_cards WHERE id IN (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);
```

Literal form:

```sql
DELETE FROM fsrs_cards WHERE id IN (
  '01KS037V6PVYYSQJXHW0J7W3FH',
  '01KS037V7TRJZS07YD77KFHB9A',
  '01KS03CS4S1JCHXYR6G0BZX6CZ',
  '01KS0Z80WVSWHA9MWXT4NXVAE2',
  '01KS0Z80XDXHAPD027V0RAQ4HC',
  '01KS0Z80XX3ZDQD84JPSQ25M21',
  '01KS0Z80YC1G3TRJV2E13PCA5Y',
  '01KS0Z80YT2CRDX5XKZKB23Q3E',
  '01KS0Z80Z82NTHF1FGKQB76CDY',
  '01KS0Z80ZPC4K1B2532M2W2DFG',
  '01KS0Z810355G47EHKJRH0RKBC',
  '01KS03XB5R800M7PGB10APW3Z2',
  '01KS03XB79TXAWS5F1PT69V0QP',
  '01KS03XB7QXTCJDQGT06F0GK2C',
  '01KS03XB847ZEP9DWB77F4DGJ0',
  '01KS03XB8J6PVPM5H25DMZS1WC',
  '01KS03XB90RYRK7VDTZGT5G4A8',
  '01KS03XB9D8C038TMZ9JZME73X',
  '01KS03XB9V8WGGAP0W4P76BYF8',
  '01KS104GYVXC6T4K00S9NVEV3B',
  '01KS104GZMCC7WBT4HFS1F37P3',
  '01KS104H0A54GA50FYD06QF0GS',
  '01KS104H0RWE2CMAWZ4D5964VT',
  '01KS104H15F8RASK1A6KYRMN1S',
  '01KS104H1JA9DZCESQDSBQYK5A',
  '01KS104H1XV49V3709880V9FGZ',
  '01KS104H2A0GH68ZJ2VF5TYR09',
  '01KS0ZY24C72J3YNK7BRT341BK',
  '01KS0ZY24TS9W2W2ADKYAT0G1F',
  '01KS0ZY25E65HTQ78642BE1FQM',
  '01KS0ZY26013RT8XD2WNMFQ4SS',
  '01KS0ZY26H0CYXQDXABWRJZX00',
  '01KS0ZY2718576XQB4ZXFGCYYH',
  '01KS0ZY27EK7N887BAWPSDY737',
  '01KS0ZY27SKRB4NJNZCTDTD0EQ',
  '01KS0ZZ3W391RQ439ABVY4MNYC',
  '01KS1004DGPK057JBRAYZKS8WC',
  '01KS030V3C862QHBP3S8879RTF',
  '01KS030V48EBH2KHD0DFM3DAY4',
  '01KS030V5GBFWQPEYH6APPG4GP',
  '01KS0ZSND8QAJBZR5T5YRR3D2Y',
  '01KS0ZSNDRZERQ762E7PAHAG04',
  '01KS0ZERWGRX5GNNHVCZYWSYMY'
);
```

## (e) Counts

- junk = **35**
- dupe-cull = **8**
- total_cull = **43**
- resulting = 871 - 43 = **828**
- **>=800 OK** - 828 >= 800 (MIN_EXPECTED_CARDS, tools/db-backup.py).
