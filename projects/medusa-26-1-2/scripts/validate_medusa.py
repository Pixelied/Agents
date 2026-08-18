from __future__ import annotations

import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
DP = ROOT / "datapacks/medusa"
RP = ROOT / "resourcepacks/medusa"

errors: list[str] = []

for root in (DP, RP):
    if not root.exists():
        continue
    for path in root.rglob("*.json"):
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except Exception as exc:
            errors.append(f"invalid json: {path.relative_to(ROOT)}: {exc}")

function_root = DP / "data/medusa/function"
known = {
    "medusa:" + str(path.relative_to(function_root).with_suffix("")).replace("\\", "/")
    for path in function_root.rglob("*.mcfunction")
}
call_re = re.compile(r"(?:^|\s)function\s+(medusa:[a-z0-9_./-]+)")
for path in function_root.rglob("*.mcfunction"):
    for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        stripped = line.lstrip()
        if stripped.startswith("$"):
            continue
        for match in call_re.finditer(line):
            if match.group(1) not in known:
                errors.append(
                    f"missing function {match.group(1)} referenced by "
                    f"{path.relative_to(ROOT)}:{line_no}"
                )

generator = ROOT / "scripts/generate_temple.py"
if generator.exists():
    result = subprocess.run(
        [sys.executable, str(generator), "--check"],
        cwd=ROOT,
        text=True,
        capture_output=True,
    )
    if result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip() or "generator check failed"
        errors.append(detail)

if errors:
    print("\n".join(errors), file=sys.stderr)
    raise SystemExit(1)

print("medusa static validation passed")
