# GPU visual validation
Run `cargo test -p rendering --release` with a Vulkan/Metal/D3D12 device. GPU tests
are deliberately not silently skipped when the device is unavailable.
Run `cargo run -p rendering --example validation --release -- --scene all --output target/validation`.

The PNGs are actual wgpu render-target readbacks, not substitute drawings. JSON
identifies backend, versions, source/image hashes, PPI, dimensions, body overrides,
coverage, instanced draw counts, uploads, and optional timestamp-query durations.
Live rendering never reads the desktop; CPU background compositing is used only
by this developer fixture generator.

Tests cover transparent clearing, 96-byte instance ABI, physical versus logical
scale, heading interpolation, LOD hysteresis, invalid-value rejection, eight
separate appendages at magnification, elbowed antennae, and limb-only rotation.
The declared software-backend reconstruction tolerances are <1.32 total rotational
coverage ratio and <1.5 limb-only ratio. These are not biological measurements.

Physical 1:1 viewing, native compositor alpha, and high-refresh temporal stability
remain real-hardware checks. Magnified diagnostics are not evidence for those gates.
