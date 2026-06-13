# PS Laboratory Source Extract — Confidence Intervals in R

**Origin:** `C:\Users\User\Desktop\PS laborator\1.R` (local file, PS laboratory work, UAIC 2025-2026)
**Topic:** Interval de incredere pentru medie populatiei cu dispersie cunoscuta (interval_z)

---

## Codul sursa (fragment relevant)

```r
#2.1
interval_z = function(n, sample_mean, sigma, alfa) {
  z = qnorm(1 - alfa/2, 0, 1)
  margine = z * sigma / sqrt(n)
  a = c(sample_mean - margine, sample_mean + margine)
  return(a)
}

#2.2
interval_z(25, 67.53, 10, 0.1)

#2.3
interval_z(50, 5, 0.5, 0.05)
```

## Descriere
Functia `interval_z` calculeaza un interval de incredere bilateral (1-alfa)100% pentru media populatiei,
cu dispersia (sigma) cunoscuta, folosind distributia normala standard z.

Formula: [x_bar - z * sigma/sqrt(n),  x_bar + z * sigma/sqrt(n)]

Unde z = qnorm(1 - alfa/2) este cuantila distributiei normale.

## Exemplu de rulare (2.2)
n=25, sample_mean=67.53, sigma=10, alfa=0.1
z = qnorm(0.95) ≈ 1.644854
margine = 1.644854 * 10 / sqrt(25) = 1.644854 * 10 / 5 = 3.289708
Interval: [67.53 - 3.289708, 67.53 + 3.289708] = [64.240292, 70.819708]
