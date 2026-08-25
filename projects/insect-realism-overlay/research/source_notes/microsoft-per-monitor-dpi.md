# Microsoft High DPI desktop application development / per-monitor DPI documentation

- Source ID: microsoft-per-monitor-dpi
- Stable identifier: Microsoft:Win32:High-DPI-Desktop
- Canonical source: https://learn.microsoft.com/windows/win32/hidpi/high-dpi-desktop-application-development-on-windows
- Source kind: documentation
- Retrieved: 2026-08-25
- License: Microsoft documentation terms
- License class: REFERENCE_ONLY
- Reviewed page-equivalent: 6

## Why this source matters
Official Windows evidence for per-monitor scaling and physical-size-consistent window behavior.

## Measurements / observations extracted
Per-monitor DPI awareness requires applications to respond to DPI changes as windows move across displays. DPI-aware sizing is necessary to keep apparent physical dimensions consistent across monitors.

## Units and uncertainty
DPI and scale factors; EDID/physical-size discovery is a separate issue.

## What can enter the derived database
Windows display-scale adapter requirements and multi-monitor rescale events.

## What must not be redistributed
Do not bundle Microsoft documentation text.

## Conflicts / limitations
DPI may be logical/effective rather than independently measured physical PPI; calibration fallback remains necessary.

## Follow-up sources cited by this work
Apple NSScreen; Task 11 Windows display APIs.
