# Recovered source audit

- recovered Java files: **2954**
- Fabric migration Java files: **33**
- registered modules/HUD modules: **107**
- native bridge reference files: **35**
- raw GL11 files: **75**
- Javassist files: **25**
- ASM files: **18**
- LaunchClassLoader files: **18**
- LWJGL2 input files: **0**
- Fabric Mixins in validated shell: **16**

The module count is derived from classes actually constructed by `ModManager`, not filename heuristics.

These are static coupling indicators, not proof of feature parity. The current shell has separately passed Fabric 26.2 compile and `runClient` smoke validation on GitHub Actions.
