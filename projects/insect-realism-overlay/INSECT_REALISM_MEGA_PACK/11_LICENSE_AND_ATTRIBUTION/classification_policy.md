# License Classification Policy

Every external source receives exactly one classification. If the license or terms are uncertain, classify the source as `REFERENCE_ONLY` until the uncertainty is resolved.

| Class | Bundle bytes in research pack | Ship bytes with app | Derive factual measurements | Intended use |
| --- | --- | --- | --- | --- |
| `REDISTRIBUTABLE_AND_SHIPPABLE` | Yes | Yes | Yes | Material whose verified terms permit redistribution and product shipping, subject to attribution/notice requirements. |
| `REDISTRIBUTABLE_RESEARCH_ONLY` | Yes | No | Yes | Material that can be redistributed in the research pack but cannot be shipped as app content under the verified terms. |
| `REFERENCE_ONLY` | No | No | Yes | Restricted or unclear bytes. Keep citation, provenance, retrieval instructions, and legally permissible derived facts only. |
| `DO_NOT_USE` | No | No | No | Material that is incompatible, prohibited, untrustworthy, or otherwise excluded. |

## Attribution and notices

Attribution, copyright notices, license text, author credit, and source links required by the verified terms must travel with any bundled bytes. An asset or source-code snapshot is not considered redistributable merely because it is publicly downloadable.

## Conservative default

No automated guess upgrades a source into a redistributable class. Missing, contradictory, or item-specific license evidence defaults to `REFERENCE_ONLY`. Item-level terms override assumptions based on a hosting site's general branding or a repository README.
