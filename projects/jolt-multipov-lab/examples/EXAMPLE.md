# Example reports

`speedrunner.json` and `hunter.json` are **fabricated fixtures**, not real gameplay analysis.
They follow the Jolt v2.4 JSON fields recovered from the downloadable executable.

To explore multi-video comparison:

1. Open `../index.html` and import both JSON files.
2. Keep `speedrunner.json` as the reference.
3. Create shared event **Portal**: speedrunner `0:20`, hunter `0:22`.
4. Create shared event **Finish**: speedrunner `0:40`, hunter `0:47`.
5. Open the `5 s` cut in the speedrunner recording. The second recording should
   appear as an alternative viewpoint near the transition.
6. An alignment warning of `5.00 s` should appear because those anchors imply
   inconsistent elapsed time. This is **not** proof of any specific cause.

Do not use sample data as evidence about a real Minecraft video.
