# Jolt Multi-POV Lab

**An unofficial, local-first companion to [JoltCracker](https://splosh.icu/jolt) v2.4 by Splosh.**

Jolt reads Minecraft's game tick counter from hunger and heart HUD animations and
identifies places where a game's clock and the recorded video clock diverge.
Jolt already recognizes more than one player's screen **within one video**. This
project extends the *investigation workflow*, without modifying Jolt itself:

- Import **multiple independent Jolt raw JSON results** from different recordings.
- Show separate timelines per Jolt-detected game/POV, including named players,
  Jolt-reported cuts, POV switches, and undecodable intervals.
- Align recordings using **human-identified shared events** and exact video times.
- Show discrepancies between matched anchor intervals (possible editing, retiming,
  inaccurate event markers, recording issues, etc.).
- For a cut, suggest **other aligned recordings that contain Jolt-observed video
  near the edit boundary**. These are review leads, *not* a reconstruction of
  missing footage.
- Attach two local videos for side-by-side seeking and approximate synchronized playback.
- Export the case, a portable HTML summary, or a CSV of Jolt-reported transitions.
- No accounts, uploads, npm install, web services, or external JavaScript libraries.

## Run on your Mac

1. Extract the downloaded project ZIP. Keep `index.html`, `core.js`, and `app.js`
   **together in the same folder**.
2. Double-click `index.html` to open it in Safari, Chrome, or Firefox.
3. In **Jolt v2.4**, enable **Raw result file** under More Settings and scan
   each recording, or use the terminal:

   ```bash
   /Applications/Jolt.app/Contents/MacOS/jolt "/path/to/video.mp4" --json "/path/to/video.jolt.json"
   ```

   Jolt on macOS needs `ffmpeg` available; Splosh's instructions recommend
   `brew install ffmpeg` if necessary.
4. Click **Add Jolt JSON files** and select all `.json` results.
5. Choose the reference recording. Add event anchors using a matching moment
   visible in two recordings, e.g. "Nether portal breaks" at `12:04.50` in A
   and `12:10.00` in B. Two or more shared events let the app flag elapsed-time
   disagreements.
6. Click a reported cut and **Review** an alternative POV near the boundary.
   To see actual video, choose each local original in the two video players.
7. **Save case** for later, or export CSV/HTML for an investigation write-up.

If your browser blocks locally loaded scripts, run this from the extracted folder
(using macOS' Python if installed):

```bash
python3 -m http.server 8765 --bind 127.0.0.1
```

Then open `http://127.0.0.1:8765/` in your browser. The server only serves local
files to your computer. Do not expose the server to the public internet.

## Try the included example

The two JSON files under `examples/` are **fabricated test data**, not evidence
about any real video. See [`examples/EXAMPLE.md`](examples/EXAMPLE.md) for a
reproducible two-recording case demonstrating a 5-second Jolt-reported jump and
a second video with an overlapping observation window.

## How to interpret results

**Important limitations:**

- Each Minecraft client's tick counter is independent. Two clients showing the
  same tick number are *not* evidence that their recordings depict the same
  moment. Automatic cross-player tick alignment is intentionally **not** used.
- A single shared event only determines an offset. With multiple events, the
  alignment is linearly interpolated between them and assumes the events really
  occurred simultaneously. It is *not* a physics-based synchronizer.
- A discrepancy between successive anchor intervals is not automatically a
  proven cut or speed change. Misidentification, latency, poor time estimates,
  pauses, and source-video edits can all contribute.
- An observed run spans its first/last matched jolt. It is **not** proof every
  intervening frame is genuine. A candidate second view near a reported cut
  does **not** necessarily cover time removed from the first video.
- Jolt's own event labels and missing-time estimates are presented as-is. This
  tool does not re-decode HUD pixels or independently validate Jolt's detections.
- The attached Jolt.app is a compiled macOS binary, with no complete editable
  source tree. Consequently this is a standalone companion, **not a modified
  or repackaged Jolt.app**.

## Development and tests

`core.js` exposes pure functions in browsers and in Node.js without dependencies.
Use Node 18 or newer:

```bash
node --test tests/*.test.js
```

The fixtures use the v2.4 JSON structure inspected from the Jolt macOS
executable: `games`, `runs`, `points` (`[video_second, tick, jolt_index]`),
`breaks`, `jolts`, `unread`, `screens`, `fps`, and `span`.

Tests cover import validation, browser control smoke tests, multi-POV separation, observation coverage,
single-anchor offset alignment, piecewise anchor alignment, contradictory
anchors, cross-recording candidate windows, timestamp formatting, and CSV
escaping. This repository does **not** contain Jolt's proprietary executable.

### Planned further work

- Compare independently recorded audio to suggest candidate shared-event anchors.
- Support separate video segments around major cuts, rather than a single
  piecewise interpolation across all timeline discontinuities.
- Add optional review-oriented frame similarity and repeated-footage analysis.
- Request developer source access for a true native integration with Jolt's GUI.

## Credits

Original JoltCracker and tick-cracking algorithm: **Splosh**, [splosh.icu](https://splosh.icu/jolt).
All files in this project are new companion code and examples; no executable
code has been copied from Jolt.app.
