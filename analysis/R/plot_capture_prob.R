# analysis/R/plot_capture_prob.R
# Distance-based cowbird capture probability curve (logistic).
# Data is produced by logisticFunForCaptureTest -> analysis/data/capture_prob_curve.csv
# This reproduces the red "Intermediate alternative" curve in
# LogisticFunctionForCowbirdTrapEffectiveness.xlsx (P=0.90 at 0.9 km, P=0.10 at 3.0 km).
#
# Run from the PROJECT ROOT:
#   Rscript analysis/R/plot_capture_prob.R

p90_dist <- 0.9   # km at which capture prob = 0.90 (mpCowbirdCaptureP90DistKM)
p10_dist <- 3.0   # km at which capture prob = 0.10 (mpCowbirdCaptureP10DistKM)

in_csv  <- "analysis/data/capture_prob_curve.csv"
out_png <- "analysis/figures/capture_prob.png"

df <- read.csv(in_csv)

# Independent check: rebuild the logistic curve from the two anchors and overlay it.
# P(d) = 1 / (1 + exp(-(a + b*d))), solved from the 0.90 / 0.10 anchor points.
logitHigh <- log(0.9 / 0.1)
logitLow  <- log(0.1 / 0.9)
b <- (logitLow - logitHigh) / (p10_dist - p90_dist)
a <- logitHigh - b * p90_dist
captureProb <- function(d) 1 / (1 + exp(-(a + b * d)))

dir.create(dirname(out_png), showWarnings = FALSE, recursive = TRUE)

png(out_png, width = 900, height = 600)
plot(df$x, df$prob, type = "n", ylim = c(0, 1),
     main = "Cowbird capture probability vs distance to trap (intermediate scenario)",
     xlab = "distance from trap (km)", ylab = "capture probability")
grid(col = "grey90")

# theoretical logistic (from the formula) -- should sit exactly under the sampled line
curve(captureProb(x), from = min(df$x), to = max(df$x), add = TRUE,
      col = "grey60", lwd = 6)
# the curve computed by the Java function (the "red line")
lines(df$x, df$prob, col = "firebrick", lwd = 2)

# mark the two defining anchor points
anchors_x <- c(p90_dist, p10_dist)
anchors_y <- c(0.90, 0.10)
points(anchors_x, anchors_y, pch = 19, col = "black")
segments(anchors_x, 0, anchors_x, anchors_y, lty = 3, col = "grey40")
segments(0, anchors_y, anchors_x, anchors_y, lty = 3, col = "grey40")
text(anchors_x, anchors_y,
     labels = sprintf("(%.1f km, %.2f)", anchors_x, anchors_y),
     pos = 4, cex = 0.9)

legend("topright",
       legend = c("Java computeCaptureProb", "logistic formula"),
       col = c("firebrick", "grey60"), lwd = c(2, 6), bty = "n")
dev.off()

cat("wrote", out_png, "\n")