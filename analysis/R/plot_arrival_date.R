# Arrival-date draws (Triangular lambda -> Poisson) vs the theoretical compound PMF.
# Data produced by testArrivalDate::writeSamplesForR -> analysis/data/arrivalDate_samples.csv
#
# Run from the PROJECT ROOT:
#   Rscript analysis/R/plot_arrival_date.R

a <- 95; c <- 113; b <- 144
in_csv  <- "analysis/data/arrivalDate_samples.csv"
out_png <- "analysis/figures/arrival_date.png"

# Triangular density on [a, b] with mode c (no base-R function, so define it):
dtri <- function(x, a, c, b) {
  out <- numeric(length(x))
  L <- x >= a & x <= c
  R <- x >  c & x <= b
  out[L] <- 2 * (x[L] - a) / ((b - a) * (c - a))
  out[R] <- 2 * (b - x[R]) / ((b - a) * (b - c))
  out
}

# Marginal PMF of the arrival date: Poisson mean-mixed over the triangular lambda.
#   P(N = k) = integral over lambda of  Triangular(lambda) * Poisson(k; lambda)
pmfArrival <- function(k, a, c, b) {
  sapply(k, function(kk)
    integrate(function(l) dtri(l, a, c, b) * dpois(kk, l), a, b)$value)
}

df   <- read.csv(in_csv)
vals <- df$x

dir.create(dirname(out_png), showWarnings = FALSE, recursive = TRUE)

ks  <- seq(min(vals), max(vals))
pmf <- pmfArrival(ks, a, c, b)

png(out_png, width = 900, height = 600)
hist(vals, breaks = seq(min(vals) - 0.5, max(vals) + 0.5, by = 1),
     freq = FALSE, col = "grey85", border = "white",
     main = sprintf("Arrival-date draws vs theoretical Triangular-Poisson (a=%d, c=%d, b=%d)",
                    a, c, b),
     xlab = "arrival date (day of year)")
lines(ks, pmf, lwd = 2, col = "firebrick", type = "b", pch = 16, cex = 0.5)
legend("topright", legend = c("sampled", "theoretical (compound)"),
       col = c("grey70", "firebrick"), lwd = c(8, 2), bty = "n")
dev.off()

cat("wrote", out_png, "\n")