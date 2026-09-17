# External datasets

No external dataset has been accepted yet. This is an in-progress research workspace, not the completed Mega Pack.

Add a source and an item-level license review before retrieval. Every download entry records its source ID, exact HTTP(S) URL, safe root-relative destination, expected byte size (0 means unknown), SHA-256 where known, and whether the bytes are staged for inclusion. A bundled entry requires a digest; that flag alone never establishes permission.

Large or reference-only resources remain externally represented: retain a DOI/accession, retrieval instructions, size/checksum if verifiable, calibration notes, and source-linked observations. Do not copy restricted source bytes into this tree.

Use `insect_research.download.download_with_resume` only after permission review. Transfers use identity encoding, a 30-second socket timeout, cooperative per-destination locks, and a `.part` file. Interrupted data resumes only when metadata binds the same source/expectations and a strong ETag or expected digest supports validation. Unbound/unsafe-to-resume partials restart. Range offsets and lengths are checked; a server returning 200 replaces rather than appends. Hash-invalid complete partials are discarded. Existing final files are not replaced until retrieval checks succeed.

Without an expected digest the downloader can detect protocol/size errors but cannot establish cryptographic integrity; record the resulting digest and retain independent provenance. Never mistake transport success for permission, calibration, or scientific review.
