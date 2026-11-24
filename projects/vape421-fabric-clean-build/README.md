# Clean Fabric packaging CI

Builds from the exact original source commit provided by the user; no historical ports. The overlay contains the small Fabric bootstrap, input adapter and in-process native bridge, and `prepare.py` checks the original Java file hashes. Builds are isolated, read-only CI with no account credentials. Tests cover packaging and standalone Java 25 native loading, not real Minecraft gameplay. The bridge is part of the mod archive; there is no external injector.
