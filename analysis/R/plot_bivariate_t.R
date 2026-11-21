# 2Dt (bivariate Student's t) dispersal-distance draws vs. the theoretical
# density, for both parameter sets (male adult, male juvenile).
# Data is produced by SampleBivariateTTest -> analysis/data/2Dt_*.csv
#
# Run from the PROJECT ROOT:
#   Rscript analysis/R/plot_bivariate_t.R

# --- Theoretical density ------------------------------------------------------
# 2Dt distance-kernel density (derived from the inverse-CDF sampler):
#   f(x) = 2*(b-1)*x/a^2 * (1 + (x/a)^2)^(-b),  x >= 0
# Defined as a function of x with the shape/scale params a, b so we can overlay
# it with curve() below. `x` here is a vector; the whole body is vectorised.
f2dt <- function(x, a, b) 2 * (b - 1) * x / a^2 * (1 + (x / a)^2)^(-b)

# --- The two cases to plot ----------------------------------------------------
# A list of lists: one entry per parameter set. Each entry bundles a label,
# its (a, b) params, and the CSV to read. Looping over this keeps the plotting
# code written once instead of copy-pasted per case.
cases <- list(
  list(name = "male_adult",    a = 0.078, b = 1.47,
       csv = "analysis/data/2Dt_male_adult.csv"),
  list(name = "male_juvenile", a = 3.33,  b = 2.41,
       csv = "analysis/data/2Dt_male_juvenile.csv")
)

# --- Plot each case -----------------------------------------------------------
for (cs in cases) {                       # cs = one case (a named list) per pass
  df   <- read.csv(cs$csv)                # read the CSV into a data frame
  vals <- df$x                            # pull out the single column named "x"

  out_png <- sprintf("analysis/figures/2Dt_%s.png", cs$name)  # build output path
  # Make sure the output folder exists (quiet, creates parents if needed):
  #   dirname()      -> "analysis/figures"  (folder part of out_png)
  #   recursive=TRUE -> like `mkdir -p`; also no error if it already exists
  #   showWarnings=FALSE -> don't warn on re-runs when the folder is there
  dir.create(dirname(out_png), showWarnings = FALSE, recursive = TRUE)

  # These kernels are heavy-tailed (male adult has infinite mean), so crop the
  # top 1% to keep the plot readable. quantile(vals, 0.99) is the value below
  # which 99% of the samples fall; as.numeric() drops its "99%" name attribute.
  xmax <- as.numeric(quantile(vals, 0.99))
  vals <- vals[vals <= xmax]              # keep only samples within the 99% range

  png(out_png, width = 900, height = 600) # open a PNG "device"; plotting writes to it
  # Histogram of the samples as a DENSITY (freq = FALSE), so the bar areas sum
  # to 1 and are directly comparable to the theoretical density curve.
  hist(vals, breaks = 100, freq = FALSE, col = "grey85", border = "white",
       main = sprintf("2Dt %s draws vs theoretical (a=%.3f, b=%.2f, 99%% shown)",
                      cs$name, cs$a, cs$b),   # %% prints a literal percent sign
       xlab = "dispersal distance")
  # Overlay the theoretical density on top of the histogram. Inside curve(), `x`
  # is the plotting variable curve() sweeps across the current x-range; add=TRUE
  # draws onto the existing histogram instead of starting a new plot.
  curve(f2dt(x, cs$a, cs$b), add = TRUE, lwd = 2, col = "firebrick")
  # Manual legend: a thick grey line stands in for the histogram bars, a thin
  # red line for the curve. bty = "n" removes the box around the legend.
  legend("topright", legend = c("sampled", "theoretical"),
         col = c("grey70", "firebrick"), lwd = c(8, 2), bty = "n")
  dev.off()                               # close the device -> finalise/save the PNG

  cat("wrote", out_png, "\n")             # progress message to the console
}
