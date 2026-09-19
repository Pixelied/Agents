# Biology data attribution and reuse boundary

## Included trajectory derivative - CC BY 4.0

This application includes transformed, selected coordinates from:

Gabriele Valentini, Nobuaki Mizumoto, Stephen C. Pratt, Theodore P. Pavlic,
and Sara I. Walker (2020), **Data and code from: Revealing the structure of
information flows discriminates similar animal social behaviors**.
Dataset: https://doi.org/10.6084/m9.figshare.9786260.v1
Companion article: eLife 9:e55395, https://doi.org/10.7554/eLife.55395
License: Creative Commons Attribution 4.0 International,
https://creativecommons.org/licenses/by/4.0/

The supplied Mega Pack classifies the selected dataset bytes as
`REDISTRIBUTABLE_AND_SHIPPABLE` under CC BY 4.0. This notice retains the authors,
source title, stable links and license link. The authors do not endorse this app.

**Changes:** The Mega Pack calibrated and subsampled the original coordinates.
This compiler selects 20 *Temnothorax rugatulus* tandem-leader tracks from the
first 900 seconds, retaining every tenth original frame and 53,940 intervals.
Speed, acceleration and turn quantities are derived from those intervals;
2,694 runtime intervals are winsorized at the selected speed p95, and 133
undefined-heading intervals receive zero angular velocity. Original track/frame
identifiers and these transformations remain in the binary/report provenance.
The packaged report is `creature-profiles/runtime-profiles.report.json`.

These are laboratory tandem-leader observations, not measurements of the target
Argentine-ant-like morphology's isolated or vertical-glass walking. Transfer is
explicitly unvalidated. Default morphology uses the selected 2.2-2.6 mm worker
range, not an average across unrelated species/contexts. Validation scenes at
2, 3 and 4 mm are deliberate tests of the renderer, not new measurements.

## Other evidence and engineering assumptions

Original numerical/qualitative syntheses cite the Mega Pack source IDs in the
runtime report. Reference-only papers, theses, source code, specimen imagery,
video and restricted media are NOT bundled as application assets. A citation
is not permission to redistribute its underlying source. No candidate secondary
species is qualified in this version.

Where numerical evidence was absent, geometry priors, antenna amplitudes/filtering,
encounter probabilities, trail decay and certain edge/cursor rules are explicitly
marked `EngineeringAssumption`, not measured biological constants. See
`MODEL_ASSUMPTIONS.md`, `BIOLOGY_PROFILE_FORMAT.md`, and the report for details.

Application code licensing does not replace any underlying dataset license.
When distributing a compiled or modified profile bundle, retain this attribution,
its provenance report and a notice of further changes. Upstream author names were
cross-checked against the eLife article during the release audit; the supplied
Mega Pack remains the implementation evidence source.
