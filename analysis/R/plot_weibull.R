# Weibull dispersal-distance draws (female juveniles) vs. the theoretical density.
# Data is produced by SampleWeibullTest -> analysis/data/weibull_samples.csv
#
# Run from the PROJECT ROOT:
#   Rscript analysis/R/plot_weibull.R

shape_b <- 1.22
scale_a <- 5.49
in_csv  <- "analysis/data/weibull_samples.csv"
out_png <- "analysis/figures/weibull.png"

df   <- read.csv(in_csv)
vals <- df$x

dir.create(dirname(out_png), showWarnings = FALSE, recursive = TRUE)

# Crop the top 1% so the bulk of the distribution is readable.
xmax <- as.numeric(quantile(vals, 0.99))
vals <- vals[vals <= xmax]

png(out_png, width = 900, height = 600)
hist(vals, breaks = 100, freq = FALSE, col = "grey85", border = "white",
     main = sprintf("Weibull draws vs theoretical (shape=%.2f, scale=%.2f, 99%% shown)",
                    shape_b, scale_a),
     xlab = "dispersal distance")
curve(dweibull(x, shape = shape_b, scale = scale_a),
      add = TRUE, lwd = 2, col = "firebrick")
legend("topright", legend = c("sampled", "theoretical"),
       col = c("grey70", "firebrick"), lwd = c(8, 2), bty = "n")
dev.off()

cat("wrote", out_png, "\n")
