# Inverse Gaussian (Wald) dispersal-distance draws (female adults) vs. the
# theoretical density.
# Data is produced by SampleInverseGaussianTest -> analysis/data/inverse_gaussian_samples.csv
#
# Run from the PROJECT ROOT:
#   Rscript analysis/R/plot_inverse_gaussian.R

mu     <- 2.66
lambda <- 0.33
in_csv  <- "analysis/data/inverse_gaussian_samples.csv"
out_png <- "analysis/figures/inverse_gaussian.png"

# Inverse Gaussian / Wald density (no base-R function, so define it):
#   f(x) = sqrt(lambda / (2*pi*x^3)) * exp(-lambda*(x-mu)^2 / (2*mu^2*x)),  x > 0
dInvGauss <- function(x, mu, lambda) {
  out <- numeric(length(x))
  pos <- x > 0                       # evaluate only where the density is defined
  out[pos] <- sqrt(lambda / (2 * pi * x[pos]^3)) *
    exp(-lambda * (x[pos] - mu)^2 / (2 * mu^2 * x[pos]))
  out
}

df   <- read.csv(in_csv)
vals <- df$x

dir.create(dirname(out_png), showWarnings = FALSE, recursive = TRUE)

xmax <- as.numeric(quantile(vals, 0.99))
vals <- vals[vals <= xmax]

png(out_png, width = 900, height = 600)
hist(vals, breaks = 100, freq = FALSE, col = "grey85", border = "white",
     main = sprintf("Inverse Gaussian draws vs theoretical (mu=%.2f, lambda=%.2f, 99%% shown)",
                    mu, lambda),
     xlab = "dispersal distance")
curve(dInvGauss(x, mu, lambda), add = TRUE, lwd = 2, col = "firebrick")
legend("topright", legend = c("sampled", "theoretical"),
       col = c("grey70", "firebrick"), lwd = c(8, 2), bty = "n")
dev.off()

cat("wrote", out_png, "\n")
