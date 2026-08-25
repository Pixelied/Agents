# Ant anatomy and physical display-scale synthesis

## Primary worker profile

The first realism profile is **Linepithema humile** (Argentine ant), not a generic rescaled ant. Two independent identification sources place workers at approximately **2.2–2.6 mm total body length** [msstate-linepithema-humile; ufifas-argentine-ant]. Wild's primary taxonomic treatment supplies worker component ranges across **n=81** workers: head length **0.62–0.78 mm**, head width **0.53–0.72 mm**, and scape length **0.62–0.80 mm** [wild-2004-linepithema-taxonomy]. These component ranges are retained without inventing means or standard deviations that were not extracted from the primary measurement table.

The silhouette should therefore be built around an actually tiny, slender worker. The target should not inherit the heavy head, long legs, extreme speed, or body proportions of large Cataglyphis or Camponotus simply because those species have richer gait footage. Cross-species locomotion evidence may constrain *relationships* such as tripod phasing or curvature response, but absolute geometry stays tied to Linepithema wherever direct data exist.

## Antenna and visible morphology

Independent Argentine-ant identification descriptions agree on elbowed **12-segmented antennae without a terminal club** [msstate-linepithema-humile; ufifas-argentine-ant]. At normal screen size, individual funicular segments will often be subpixel and should not be rendered as twelve visibly separated beads. What remains important is the long scape, the elbow, the overall paired-antenna silhouette and biologically active motion.

AntWeb's standardized head/dorsal/profile image system is the preferred next-stage source for checking body proportions and silhouette consistency across many worker specimens [antweb-2026-api]. Individual images are not automatically bundled merely because the site is broadly CC Attribution; photographer/specimen/source attribution and item-level exceptions remain mandatory [antweb-2026-about-license].

## Physical millimeters versus pixels

The renderer must keep biological size in millimeters. The canonical conversion is:

`pixels = millimeters / 25.4 * physical_PPI`

`rendering_scale.csv` enumerates all combinations of **2.0, 2.5, 3.0, 3.5, 4.0 mm** and **96, 110, 144, 163, 218, 254 PPI** without prematurely rounding fractional pixels. For example, a 3 mm ant spans about 12.99 physical pixels at 110 PPI and about 30 physical pixels at 254 PPI. Fractional coverage matters because snapping body length to whole pixels would produce visible size jumps between displays.

OS backing/DPI scale is a separate coordinate transform. macOS backing scale and Windows per-monitor DPI behavior describe logical-to-device pixel mapping, not a trustworthy biological millimeter measurement by themselves [apple-nsscreen-backing-scale; microsoft-per-monitor-dpi]. When true monitor dimensions/PPI cannot be recovered reliably, the app needs a physical calibration fallback rather than pretending logical DPI equals physical PPI.

## Rendering implications at 2–4 mm

At the low end of common desktop density, an entire 2–4 mm ant can occupy only roughly 8–15 pixels. At high-density displays it can occupy roughly 20–40 pixels. This makes silhouette, body-segment spacing, leg-contact flicker, antenna motion, orientation, and temporal anti-aliasing much more perceptually important than microscopic setae, eye facets, or adhesive-pad geometry. Those microstructures still inform biomechanics but should not receive per-ant geometry cost at ordinary viewing scale.

## Provenance rules for later geometry work

1. Never convert a taxonomic component range into a whole-body length unless the source explicitly provides a defensible relation.
2. Never infer a mean or SD from a min/max range.
3. Never copy absolute gait values from a larger species without recording the donor species and transfer rationale.
4. Preserve biological millimeters until the display adapter performs the final physical-PPI conversion.
5. Prefer multiple specimen views/individuals over a single beautiful reference image so model proportions do not accidentally reproduce one atypical specimen.
