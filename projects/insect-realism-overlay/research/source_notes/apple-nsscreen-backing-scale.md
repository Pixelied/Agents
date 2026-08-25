# Apple NSScreen backingScaleFactor documentation

- Source ID: apple-nsscreen-backing-scale
- Stable identifier: Apple:NSScreen.backingScaleFactor
- Canonical source: https://developer.apple.com/documentation/appkit/nsscreen/backingscalefactor
- Source kind: documentation
- Retrieved: 2026-08-25
- License: Apple documentation terms
- License class: REFERENCE_ONLY
- Reviewed page-equivalent: 2

## Why this source matters
Official macOS evidence that logical screen units and backing pixels are distinct.

## Measurements / observations extracted
backingScaleFactor supplies the backing-store pixel scale associated with an NSScreen. Realistic physical sizing must not confuse logical points with physical pixels.

## Units and uncertainty
Scale factor (dimensionless); physical millimeters still need monitor-size/PPI information or calibration.

## What can enter the derived database
Display-scale adapter requirement for macOS.

## What must not be redistributed
Do not redistribute Apple documentation text.

## Conflicts / limitations
Backing scale alone does not provide physical display dimensions.

## Follow-up sources cited by this work
Microsoft DPI docs; Task 11 native display research.
