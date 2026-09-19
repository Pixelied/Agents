# Evidence baselines and hard gaps

## Drosophila melanogaster — adult

Direct evidence supports immediate walking-model work:

- 2024 wild-type Berlin body length: **2.04 ± 0.10 mm**.
- Straight freely walking observations: **7.2–44.7 mm/s**, with **28 mm/s** most represented.
- Speed-binned gait occupancy from Mendes 2013:
  - slow ≤19.9 mm/s: tripod **31.37%**, tetrapod **25.45%**, pentapod **30.15%**;
  - medium 20–33.9: tripod **51.63%**, tetrapod **15.90%**, pentapod **14.37%**;
  - fast ≥34: tripod **64.98%**, tetrapod **7.26%**, pentapod **13.33%**.
- Spontaneous locomotion has a stopped peak and moving epochs around **5–20 mm/s**; high-speed turns include a brief speed drop.
- Grooming dataset: repeated leg sweep/rub mechanics **5–7 Hz**, larger head-clean/leg-rub transition rhythm **0.3–0.6 Hz**.
- Free-flight control measurements include mean **0.71 m/s**, maximum around **1.60 m/s**, measured acceleration/deceleration and turn radii/rates.

Primary/open-data anchors:

- Current Biology 2024 locomotion + Dryad: https://doi.org/10.5061/dryad.mpg4f4r73
- eLife gait data: https://doi.org/10.7554/eLife.00231
- eLife tripod mechanics: https://doi.org/10.7554/eLife.65878
- Drosophilid XY trajectories: https://doi.org/10.5061/dryad.z8w9ghxfc
- Grooming data: https://doi.org/10.25349/D9QW4J

## Blattella germanica — first-instar nymph

The exact ~24-hour life stage has an unusually implementation-ready movement model.

Direct Jeanson et al. 2003 values:

- body ~**3 mm** long x **2 mm** wide; antennae ~**3 mm**;
- central moving speed **11.0 mm/s**;
- peripheral/wall speed **10.6 mm/s**;
- central stop hazard **0.03 s^-1**;
- peripheral stop hazard **0.08 s^-1**;
- peripheral exit hazard **0.12 s^-1**;
- transport mean free path **23.2 mm**;
- wall-departure angle: log-normal geometric mean **36.6°**, geometric SD **2.14**;
- stop mixture: short-state probability **0.93**, short mean **5.87 s**, long mean **700 s**;
- about half of moving time and ~80% of stopping time occurred in the peripheral zone.

Primary source: https://doi.org/10.1016/S0022-5193(03)00277-7

Hard gap: fine first-instar leg-phase/footfall kinematics. Adult B. germanica turning mechanics may be a qualitative hypothesis, not numeric nymph calibration.

## Cimex lectularius — adult

Direct adult morphology:

- male length roughly **4.36–4.63 mm**;
- female length roughly **4.63–4.98 mm**;
- thorax width roughly **1.30–1.47 mm** across the cited treatments.

No-host host-search controls:

- female mean speed **1.33 mm/s**, walking **25.5%**;
- male mean speed **0.99 mm/s**, walking **17.2%**;
- characteristic frequent direction changes and short stops.

In 2D 10-minute dispersal, about **24–26%** moved less than 4 cm. The observed diffusion-rate range in that study was **0.00006–0.416 cm²/s**.

Primary sources:
- https://doi.org/10.3390/insects2010022
- https://doi.org/10.3390/insects6040792
- 2025 odor/state data: https://doi.org/10.5061/dryad.nzs7h4529

Hard gap: direct adult footfall/gait timing. Host/heat experiments may validate latent search states but cannot be wired to screen content.

## Tribolium castaneum — adult

Strong direct adult whole-body evidence exists for:

- path length;
- sinuosity;
- edge affinity (including a published within-10-mm edge metric);
- movement speed/stops/reentry around obstacles;
- locomotor individual variation;
- death-feigning / anti-predator immobility;
- walking-vs-flight context.

Open data:
- Mendeley movement traits: https://doi.org/10.17632/zcb97xf8xt.1
- walking/flight: https://doi.org/10.5061/dryad.w9ghx3g46
- death-feigning: https://doi.org/10.5061/dryad.ksn02v78j
- synchronized anti-predator behavior: https://doi.org/10.5061/dryad.sf7m0cght

**Hard gate:** no qualifying direct adult leg-phase gait dataset was located. Detailed larval Tribolium gait is explicitly non-transferable to adult.

## Dataset acquisition priorities

P0:
1. Drosophila Dryad `wt_berlin_freely_walking_dataset.csv` (34.43 MB).
2. Drosophila `figure_S1_data.zip` (~192.56 KB).
3. Tribolium Mendeley 10.17632/zcb97xf8xt.1.
4. Tribolium obstacle supplement `JEZ-343-809-s001.xlsx` (~39.4 KB).
5. Direct adult gait evidence for Tribolium and Cimex, or keep those release gates closed.

Large remote raw datasets were indexed but are not falsely represented as mirrored in the research artifact.
