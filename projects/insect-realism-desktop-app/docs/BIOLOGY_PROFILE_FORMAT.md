# Biology profile contract

The authoritative Mega Pack is read at build time. The runtime consumes the
compact versioned `runtime-profiles.bin`, not PDFs, CSV corpora or a live web
service. `build-input.json` is the normalized reproducible compiler input;
`runtime-profiles.report.json` records provenance and content hashes.

`RuntimeProfileBundle` separates schema version, profile version and app version.
Profiles identify species/context, qualification, measured or assumption-labelled
parameters, motion tracks and limitations. Evidence references retain source
identifiers, source files and hashes, units, conditions, license/review context,
and the basis of a measurement or transfer. Invalid ranges, nonfinite values,
unsupported schema or absent required evidence fail validation.

The ant profile is not falsely represented as a complete target-species field
measurement. The preserved motion sample contains 53,940 measured intervals
from the selected Valentini dataset trajectory window; transfer from tandem
leader data to the intended small-ant presentation is explicitly annotated.
Geometry priors, unresolved numerical biology and engineering choices are
labelled in provenance and `MODEL_ASSUMPTIONS.md`.

Rebuild twice from the original supplied pack and compare bytes to establish
compiler determinism. Verify the binary with the compiler's `verify` command.
Keep the report together with the binary; a source citation is not permission
to redistribute a publisher PDF or an unaudited visual asset.

Secondary profiles are not activated simply because a species appears in a
source catalog. `SECONDARY_CREATURE_GATE.json` accounts for all five supplied
candidates and leaves the release ants-only.

## Binary envelope and evidence basis

The envelope is `ANTBIO01`, followed by an eight-byte little-endian payload length,
a 32-byte SHA-256 of the postcard payload, and the serialized payload. Package
verification validates both the internal envelope and the external report digest.
This detects corruption; it is not a cryptographic signature proving authenticity.

Parameter evidence distinguishes `Measurement`, `DerivedMeasurement`, `DonorTransfer`,
and `EngineeringAssumption`. The report retains exact source fields, file hashes,
context, review state and license class. Engineering-only parameter names begin with
`engineering:` where appropriate; no new biological constants were introduced by the
continuation's retry, checkpoint, icon or packaging fixes.

## Portable compiler revision

Profile `2026.09.17-donor-transfer.2` uses pinned `libm 0.2.16` software
transcendental arithmetic at build time. The raw input and provenance are unchanged.
The complete new binary SHA-256 is
`ce558669b3b17d5e85282c1bc94ea8c0e34b6d86311ded3a2acc2d31552c502b`.
The release gate still requires exact bytes, not a numerical tolerance.

The former host-dependent arithmetic differed between Linux, macOS and Windows
in near-zero acceleration samples. The versioned correction also fixes one
approximately 180-degree donor reversal to the software kernel's deterministic
turn-sign convention. Version .2 is not claimed to be perfectly behavior-identical
to .1; the full numerical accounting and ruling are in `../APP_EXECUTION.md`.
