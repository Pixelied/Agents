# Research-pack licensing policy

This is the project's inclusion policy from the approved design, not a replacement
for any original license. A classification alone is not evidence of permission.
The license statement must be reviewed at the individual source or asset level.
Free-to-read, search-indexed, or downloadable does not mean redistributable.

| Class | Bundle original bytes in research pack | Ship those bytes with app | Derive permitted facts |
| --- | --- | --- | --- |
| REDISTRIBUTABLE_AND_SHIPPABLE | true | true | true |
| REDISTRIBUTABLE_RESEARCH_ONLY | true | false | true |
| REFERENCE_ONLY | false | false | true |
| DO_NOT_USE | false | false | false |

## Uncertain permissions

Unverified or ambiguous permissions are REFERENCE_ONLY until verified. The intake
CLI requires an explicit choice; it does not guess a license from a domain name,
a repository description, or the fact that a file is public. Missing license
URLs are allowed only when no original bytes may be included. Explanations are
mandatory in the license manifest. DO_NOT_USE is stricter than REFERENCE_ONLY:
do not import either original bytes or derived observations from that source.

## Attribution and conditions

Retain every required copyright notice, license text, attribution, author/creator
credit, source link, and modification statement alongside each bundled item.
Third-party terms override no part of the original license; the research pack
cannot grant rights it does not own. Record item-level exceptions separately.
A source's catalog classification and license-manifest classification must agree.
Each source has exactly one license record, and each bundled file requires its
own provenance, inclusion decision and checksum.

REDISTRIBUTABLE_RESEARCH_ONLY means the project's later application must not
consume those original bytes under the current inclusion decision. This is a
project boundary, not a claim that every license assigned to that class forbids
commercial distribution. Obligations and compatibility must be assessed for the
actual app before changing a classification. No license is silently upgraded.

## Restricted material

Do not put reference-only papers, videos, datasets, images, or source snapshots
in the distributable tree. Record stable identifiers, retrieval instructions,
limited original summaries, and permissible derived measurements instead. Do not
copy expressive text, code or imagery under the label of a derived fact.
The final whole-corpus audit must inspect actual staged files, not merely trust a
manifest's included_in_zip flags. A final archive is blocked until that audit and
all biological evidence gates pass.
