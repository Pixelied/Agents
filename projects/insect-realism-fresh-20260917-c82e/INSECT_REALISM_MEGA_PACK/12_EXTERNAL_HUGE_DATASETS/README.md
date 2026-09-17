# External datasets

The first candidate is indexed as `dataset-clifton-2020-ant-tracks`. Its metadata has been reviewed, but no payload has been downloaded, deserialized, normalized or bundled. `clifton-2020-status.json` records the unresolved retrieval state. The actual download manifest stays empty rather than treating a landing-page URL as a data-file URL.

Before adding a download entry, resolve the exact payload endpoint, applicable permissions, byte size and independently verifiable integrity metadata where available. The source catalogue and licence manifest must agree. Keep large data external unless inclusion is justified; keep restricted bytes out of staging.

The resumable-download helper is tested infrastructure, not proof that a particular scientific host is reachable. It cannot make an untrusted binary format safe to execute. Inspect format risks and establish a controlled conversion process before importing scientific objects into the analysis environment.

This directory does not satisfy the plan's real normalized-trajectory requirement at this checkpoint.
