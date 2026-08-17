from __future__ import annotations
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
DP = ROOT / "datapacks/fallen_knight"
RP = ROOT / "resourcepacks/fallen_knight"

errors: list[str] = []

for root in (DP, RP):
    for path in root.rglob("*.json"):
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except Exception as exc:
            errors.append(f"invalid json: {path.relative_to(ROOT)}: {exc}")

function_root = DP / "data/fallen_knight/function"
known = {
    "fallen_knight:" + str(path.relative_to(function_root).with_suffix("")).replace("\\", "/")
    for path in function_root.rglob("*.mcfunction")
}
call_re = re.compile(r"(?:^|\s)function\s+(fallen_knight:[a-z0-9_./-]+)")
for path in function_root.rglob("*.mcfunction"):
    for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if line.startswith("$"):
            continue
        match = call_re.search(line)
        if match and match.group(1) not in known:
            errors.append(f"missing function {match.group(1)} referenced by {path}:{line_no}")

if errors:
    print("\n".join(errors), file=sys.stderr)
    raise SystemExit(1)
print("fallen_knight static validation passed")
