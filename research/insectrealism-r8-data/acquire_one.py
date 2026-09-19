#!/usr/bin/env python3
"""Run the R8.1 corpus builder for exactly one target species."""
import argparse
import sys

import acquire_data

p = argparse.ArgumentParser()
p.add_argument("--output", required=True)
p.add_argument("--species", required=True)
args = p.parse_args()

matches = [s for s in acquire_data.SPECIES if s[0] == args.species]
if len(matches) != 1:
    raise SystemExit(f"Unknown or ambiguous species: {args.species!r}")

acquire_data.SPECIES = matches
sys.argv = ["acquire_data.py", "--output", args.output]
raise SystemExit(acquire_data.main())
