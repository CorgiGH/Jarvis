# ALO Study Guide Extract — Gaussian Elimination Example

**Origin:** `C:\Users\User\Downloads\ALO\ALO_Study_Guide.md` (local file, generated from ALO course materials, UAIC 2025-2026)
**Section:** §7 Eliminarea Gauss — Exemplu pas cu pas

---

## 7. Eliminarea Gauss

### Ideea
Transforma Ax = b intr-un sistem echivalent Ux = b', unde U este superior triunghiulara, apoi aplica substitutia inversa.

### Algoritmul (Pasul r, r = 1, 2, ..., n-1)
La pasul r, presupunem a_{rr}^{(r-1)} != 0 (pivot). Pentru fiecare linie i = r+1, ..., n:

```
f = -a_{ir}^{(r-1)} / a_{rr}^{(r-1)}    (multiplicatorul)

Pentru j = r+1, ..., n:
    a_{ij} = a_{ij} + f * a_{rj}

a_{ir} = 0
b_i = b_i + f * b_r
```

### Exemplu pas cu pas
```
Sistem:  x1 - x2 + 3x3 = 2
        3x1 - 3x2 + x3 = -1
         x1 + x2       = 3

Matricea extinsa:
( 1  -1   3 | 2)
( 3  -3   1 |-1)
( 1   1   0 | 3)

Pas 1: Eliminam x1 din E2 si E3
f21 = -3/1 = -3:  E2 = E2 + (-3)*E1 => ( 0  0  -8 | -7)
f31 = -1/1 = -1:  E3 = E3 + (-1)*E1 => ( 0  2  -3 |  1)

( 1  -1   3 | 2)
( 0   0  -8 |-7)
( 0   2  -3 | 1)

Pas 2: Pivotul a22 = 0! => pivotare: interschimbam E2 cu E3

( 1  -1   3 | 2)
( 0   2  -3 | 1)
( 0   0  -8 |-7)

Sistem superior triunghiular! Substitutie inversa:
x3 = -7 / -8 = 7/8
x2 = (1 - (-3)*7/8) / 2 = (1 + 21/8) / 2 = (29/8) / 2 = 29/16
x1 = (2 - (-1)*29/16 - 3*7/8) / 1 = (2 + 29/16 - 21/8) / 1
   = (32/16 + 29/16 - 42/16) / 1 = 19/16
```
