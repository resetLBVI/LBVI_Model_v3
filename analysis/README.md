# Dispersal-kernel sampling checks

Visual validation of the `DispersalKernal` samplers: each Java test draws 100,000
samples (seed `MersenneTwisterFast(42)`, so runs are reproducible), writes them to
a CSV under `data/`, and an R script overlays the sampled histogram on the
theoretical density and saves a PNG under `figures/`.

## Layout

```
analysis/
├── R/        plotting scripts (tracked in git)
├── data/     CSVs written by the Java tests   (git-ignored, regenerate anytime)
└── figures/  PNGs written by the R scripts     (git-ignored)
```

## 1. Generate the data (Java, run from the project root)

```bash
mvn -Dtest='Sample*Test' test        # all four samplers at once
# or individually:
mvn -Dtest=SampleInverseGaussianTest test
mvn -Dtest=SampleWeibullTest test
mvn -Dtest=SampleBivariateTTest test
mvn -Dtest=logisticFunForCaptureTest test   # cowbird capture-probability curve
mvn -Dtest=testArrivalDate test             # breeding-season arrival date
```

| CSV | Distribution | Sex / age | Params |
|-----|--------------|-----------|--------|
| `inverse_gaussian_samples.csv` | Inverse Gaussian (Wald) | female adult | mu=2.66, lambda=0.33 |
| `weibull_samples.csv` | Weibull | female juvenile | shape=1.22, scale=5.49 |
| `2Dt_male_adult.csv` | 2Dt (bivariate t) | male adult | a=0.078, b=1.47 |
| `2Dt_male_juvenile.csv` | 2Dt (bivariate t) | male juvenile | a=3.33, b=2.41 |
| `capture_prob_curve.csv` | Logistic capture prob vs distance | n/a (trap) | P90 at 0.9 km, P10 at 3.0 km |
| `arrivalDate_samples.csv` | Triangular lambda -> Poisson (compound) | n/a (all agents) | a=95, c=113, b=144 |

## 2. Plot (R, run from the project root)

```bash
Rscript analysis/R/plot_inverse_gaussian.R
Rscript analysis/R/plot_weibull.R
Rscript analysis/R/plot_bivariate_t.R      # writes both male-adult and male-juvenile
Rscript analysis/R/plot_capture_prob.R     # cowbird capture-probability curve
Rscript analysis/R/plot_arrival_date.R     # arrival date vs theoretical Triangular-Poisson PMF
```

Output PNGs land in `figures/`. Each plot crops the top 1% of the tail so the
bulk of the distribution stays readable — relevant for the heavy-tailed 2Dt
kernels (the male-adult 2Dt has an infinite mean).

Scripts use base R only (no packages to install).
