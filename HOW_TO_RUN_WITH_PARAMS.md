# Running the RESET LBVI model from a parameter file

You can run the model from a **parameter CSV** instead of typing every value into the
Model tab by hand. This guide shows how to create one, edit it, and run with it.

The parameter file is a simple two-column CSV — `parameter,value`, one row per
parameter. The model already *writes* one of these on every run (`paramList.csv`), so you
never have to type it from scratch: **run once to get a template, edit it, point the model
back at it.**

---

## Step 1 — Get a template (`paramList.csv`)

Every run writes `paramList.csv`, a snapshot of **every parameter at the value that run
used**. That file is your master template. Where it is written depends on how the model
was launched:

| Launched as…                                             | `paramList.csv` appears in…                    |
| -------------------------------------------------------- | ---------------------------------------------- |
| Shaded JAR (`java -jar target/RESET_LBVI-1.0-SNAPSHOT.jar`) | `target/` — next to the jar                   |
| IDE / `target/classes`                                   | one level **above** the project folder         |

It looks like this (≈65 rows):

```
parameter,value
mpInputSimulationDurationYears,30
mpVireoLongDistanceDispersalFAd,0.0196
mpVireoLongDistanceDispersalMJu,0.0491
lowerArrivalDate,91
...
```

## Step 2 — Make your own copy and edit it

1. Copy `paramList.csv` and rename it something meaningful, e.g. `run_scenarioA.csv`.
   **Put it somewhere stable** — *not* inside `target/`, which is deleted by `mvn clean`.
   A `scenarios/` folder is a good home.
2. Open it in Excel, Numbers, or any text editor.
3. **Change only the right-hand `value` column.** Leave the parameter names and the
   header row (`parameter,value`) unchanged.

Two useful things to know:

- **Partial files are fine.** You do not need all the rows. A file with only the
  parameters you want to change works — every parameter you leave out keeps its built-in
  default. So a whole scenario can be three lines:

  ```
  parameter,value
  mpInputSimulationDurationYears,5
  mpVireoLongDistanceDispersalMJu,0.06
  ```

- **Survival probabilities are not in the template.** The four survival probabilities are
  drawn randomly each run, so they are not listed. To change survival, edit the
  *hyperparameters* that **are** in the file — e.g. `mpSurvivalProbAdultF_betaAlpha`,
  `mpSurvivalProbAdultF_betaBeta`, and the juvenile gamma shape/rate parameters.

- **Input data-file paths are in the template too.** You can point the model at your own
  copies of the five input files straight from the parameter CSV — no need to retype them in
  the Model tab each run. The rows are:

  | Parameter row                  | Input file                                      |
  | ------------------------------ | ----------------------------------------------- |
  | `shpFilePath`                  | background shapefile (`.shp`, display only)     |
  | `vegAttributePath`             | territories CSV (`inTerritories2020.csv`)       |
  | `groundwaterFilePath`          | groundwater CSV                                 |
  | `trapPlanFilePath`             | cowbird trap plan CSV                           |
  | `initVireoPopulationFilePath`  | initial Vireo population CSV                    |

  Each value can be an **absolute path** (e.g. `/Users/you/data/myTerritories.csv`, used
  exactly as written — point anywhere on your machine) **or** a path relative to the run base
  directory (the default values, like `RESET_LBVI_inputData/inTerritories2020.csv`, are
  relative). Leave a row out to keep the built-in default. Example — run with your own
  territories and population files but everything else default:

  ```
  parameter,value
  vegAttributePath,/Users/you/data/myTerritories.csv
  initVireoPopulationFilePath,/Users/you/data/myPopulation.csv
  ```

## Step 3 — Run the model with your file

### Option A — GUI (no terminal)

1. Launch the model as usual (the MASON window opens).
2. In the **Model tab**, find the **`paramFilePath`** field.
3. Paste the **full absolute path** to your file, for example:
   `/Users/you/.../4_QuickShare/scenarios/run_scenarioA.csv`
4. Press ▶ (play / run). The console prints a confirmation line:
   `Parameters loaded from …run_scenarioA.csv: 12 applied, 0 skipped.`

Leave `paramFilePath` **blank** to run with the default / Model-tab values, exactly as
before.

### Option B — Command line (headless, no window)

```
java -cp target/RESET_LBVI-1.0-SNAPSHOT.jar lbvi.LBVIEnvironment \
     /Users/you/.../4_QuickShare/scenarios/run_scenarioA.csv 42
```

- First argument: the parameter CSV (optional — omit to use defaults).
- Second argument: a random seed (optional — omit for a time-based seed).
- The `-cp … lbvi.LBVIEnvironment` form runs the headless class instead of the GUI the
  jar launches by default. "Headless" just means it runs with no window and writes the
  output CSVs directly, so you can launch it from a terminal or a script.

To build the jar first (if you have not already): `mvn package` (produces
`target/RESET_LBVI-1.0-SNAPSHOT.jar`).

## Step 4 — Verify what actually ran

After the run, open the **newly written `paramList.csv`** (the output snapshot). It records
the values the run *actually used*, so you can confirm your edits took effect. The console's
`N applied, M skipped` line is the quick check:

- **applied** = rows that were set.
- **skipped** = rows that were *not* set, each with a warning. A skip usually means a
  misspelled parameter name or a value that could not be parsed — check the console
  messages and fix the file.

---

## Tips & gotchas

- **Always use an absolute path** for `paramFilePath`. The model writes `paramList.csv`
  using one location rule, but reads `paramFilePath` relative to wherever the program was
  launched from — an absolute path avoids any confusion and always works.
- **Keep the header row** (`parameter,value`). It is fine to leave it in; the loader skips
  it automatically.
- **Row order does not matter**, and blank lines are ignored.
- **Values must match the parameter's type** — a whole number for counts/dates (e.g.
  `mpInputSimulationDurationYears,5`), a decimal for probabilities (e.g.
  `mpVireoLongDistanceDispersalMJu,0.06`), and `true`/`false` for on/off switches.
- **To run many scenarios**, make one CSV per scenario and run the model once per file
  (e.g. in a shell loop over the headless command). This loader handles one configuration
  per run; it is not an automatic parameter sweep.
