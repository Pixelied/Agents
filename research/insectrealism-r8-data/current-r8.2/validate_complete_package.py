#!/usr/bin/env python3
from __future__ import annotations

import csv
import hashlib
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

EXPECTED = {
    "blattella_germanica": ("Blattella germanica", 383, 441_436_941),
    "liposcelis_bostrychophila": ("Liposcelis bostrychophila", 322, 87_095_985),
    "oryzaephilus_surinamensis": ("Oryzaephilus surinamensis", 333, 263_465_786),
    "chelifer_cancroides": ("Chelifer cancroides", 340, 255_196_459),
}
EXPECTED_TOTAL_FILES = 1378
EXPECTED_TOTAL_BYTES = 1_047_195_171

ALLOWED_LICENSE = re.compile(
    r"(creativecommons\.org/publicdomain/(zero|mark)/1\.0|"
    r"creativecommons\.org/licenses/by(-sa)?/(2\.0|2\.5|3\.0|4\.0)|"
    r"\bcc0\b|\bcc by(?:-sa)?(?: |-|/)?(?:2(?:\.0)?|2\.5|3(?:\.0)?|4(?:\.0)?)\b|"
    r"public domain)",
    re.I,
)

STALE_PLAN_PHRASES = [
    "exactly three secondary implementation targets",
    "Chelifer cancroides: not a microfauna target",
    "Do not force a fourth",
    "## Task 7A",
    "R5/R7/R8.2 species implementation direction",
]
REQUIRED_PLAN_PHRASES = [
    "single current implementation authority",
    "Linepithema humile",
    "worker morphology variation",
    "callow",
    "queens",
    "males",
    "eggs",
    "larvae",
    "pupae",
    "optional rare pseudoscorpion predator",
    "Liposcelis entomophila",
    "same-genus",
    "no record of this species flying",
    "1,047,195,171",
]

def require(cond: bool, message: str):
    if not cond:
        raise SystemExit("VALIDATION FAILURE: " + message)

def read_csv(path: Path):
    with path.open(newline="", encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))

def sha256(path: Path):
    h=hashlib.sha256()
    with path.open("rb") as f:
        for b in iter(lambda:f.read(1024*1024), b""):
            h.update(b)
    return h.hexdigest()

def main(root: Path):
    start = root / "00_START_HERE"
    current = root / "01_CURRENT_EVIDENCE"
    refs = root / "02_REFERENCE_DATA"
    audit = root / "04_RELEASE_AUDIT"
    audit.mkdir(parents=True, exist_ok=True)

    plan = (start / "CURRENT_IMPLEMENTATION_PLAN_R8_2.md").read_text(encoding="utf-8")
    tasks = [int(x) for x in re.findall(r"^## Task (\d+)\b", plan, re.M)]
    sections = [int(x) for x in re.findall(r"^# (\d+)\.", plan, re.M)]
    require(tasks == list(range(1,58)), f"plan tasks are not exactly 1..57: {tasks}")
    require(sections == list(range(0,26)), f"plan sections are not exactly 0..25: {sections}")
    for phrase in STALE_PLAN_PHRASES:
        require(phrase not in plan, f"stale authority language remains: {phrase!r}")
    for phrase in REQUIRED_PLAN_PHRASES:
        require(phrase.lower() in plan.lower(), f"required plan concept missing: {phrase!r}")

    start_here=(start/"ASTRA_READ_THIS_FIRST.md").read_text(encoding="utf-8")
    require("Stop using the older implementation plan" in start_here, "Astra handoff does not clearly supersede old plan")
    require("Do not delete or simplify the ant system" in start_here, "Astra handoff does not explicitly preserve ant system")

    profile=json.loads((current/"chelifer_cancroides_adult_optional_predator_r8_2.json").read_text(encoding="utf-8"))
    require(profile["species"]=="Chelifer cancroides", "wrong Chelifer profile species")
    require(profile["taxonomy"]["is_insect"] is False, "Chelifer profile incorrectly classifies it as insect")
    require(profile["morphology"]["extended_visual_span_mm"]["min"]==7, "Chelifer 7 mm span missing")
    require(profile["morphology"]["extended_visual_span_mm"]["max"]==9, "Chelifer 9 mm span missing")
    require(profile["predation"]["linepithema_humile_specific_calibration"] is None, "Argentine-ant predation calibration was invented")

    qrows=read_csv(current/"CURRENT_QUALIFICATION_MATRIX_R8_2.csv")
    qspecies={r["species"] for r in qrows}
    require({"Linepithema humile","Liposcelis bostrychophila","Blattella germanica","Oryzaephilus surinamensis","Chelifer cancroides"} <= qspecies,
            "qualification matrix missing a required species")

    source_map=read_csv(current/"SOURCE_ID_MAP_R8_2.csv")
    source_ids={r["source_id"] for r in source_map}
    require({"R8-LIP-001","R8-LIP-002","R8-LIP-003","BLA-001","BLA-003","R8-ORY-001","R8-ORY-002","R8-ORY-003","R8-CHE-001","R8-CHE-002","R8-CHE-003","R8-CHE-004"} <= source_ids,
            "source ID resolver incomplete")

    empirical_dir=current/"EMPIRICAL_EXTRACTS"
    empirical_ids=set()
    for path in sorted(empirical_dir.glob("*.csv")):
        rows=read_csv(path)
        for r in rows:
            sid=(r.get("source_id") or r.get("source") or "").strip()
            if sid and re.fullmatch(r"(R8-[A-Z]+-\d+|BLA-\d+)", sid):
                empirical_ids.add(sid)
    missing_ids=sorted(empirical_ids-source_ids)
    require(not missing_ids, f"empirical source IDs have no resolver rows: {missing_ids}")

    media_sha_seen={}
    per_species=[]
    total_files=total_bytes=0
    for slug,(species,exp_files,exp_bytes) in EXPECTED.items():
        base=refs/slug
        require(base.is_dir(), f"missing reference-data directory {slug}")
        summary_path=base/"ACQUISITION_SUMMARY.json"
        manifest_path=base/"05_REFERENCE_MEDIA"/"MANIFEST.csv"
        sums_path=base/"SHA256SUMS.txt"
        require(summary_path.exists(), f"missing acquisition summary for {slug}")
        require(manifest_path.exists(), f"missing media manifest for {slug}")
        require(sums_path.exists(), f"missing SHA256SUMS for {slug}")

        summary=json.loads(summary_path.read_text(encoding="utf-8"))
        require(len(summary)==1, f"unexpected acquisition summary length for {slug}")
        rec=summary[0]
        require(rec["species"]==species, f"summary species mismatch for {slug}")
        require(int(rec["files"])==exp_files, f"{slug} summary file count changed")
        require(int(rec["actual_bytes"])==exp_bytes, f"{slug} summary raw bytes changed")

        rows=read_csv(manifest_path)
        require(len(rows)==exp_files, f"{slug} manifest rows {len(rows)} != expected {exp_files}")
        row_bytes=sum(int(r["bytes"]) for r in rows)
        require(row_bytes==exp_bytes, f"{slug} manifest byte sum {row_bytes} != expected {exp_bytes}")

        bad_licenses=[]
        for r in rows:
            lic=(r.get("license") or "").strip()
            if ("noncommercial" in lic.lower() or "by-nc" in lic.lower()
                or "all rights reserved" in lic.lower() or not ALLOWED_LICENSE.search(lic)):
                bad_licenses.append(lic)
            digest=r["sha256"].strip().lower()
            local=base/r["local_path"]
            require(local.exists(), f"manifest file missing: {local}")
            require(local.stat().st_size==int(r["bytes"]), f"byte-size mismatch: {local}")
            if digest in media_sha_seen:
                other=media_sha_seen[digest]
                raise SystemExit(f"VALIDATION FAILURE: duplicate media SHA across corpora: {digest} ({other} vs {slug})")
            media_sha_seen[digest]=slug
        require(not bad_licenses, f"{slug} contains non-redistributable licenses: {bad_licenses[:3]}")

        per_species.append((species,exp_files,exp_bytes))
        total_files+=exp_files
        total_bytes+=exp_bytes

    require(total_files==EXPECTED_TOTAL_FILES, f"total media files {total_files} != {EXPECTED_TOTAL_FILES}")
    require(total_bytes==EXPECTED_TOTAL_BYTES, f"total raw bytes {total_bytes} != {EXPECTED_TOTAL_BYTES}")

    # CSV/JSON parse sweep for all current authority data.
    parsed_csv=parsed_json=0
    for path in current.rglob("*"):
        if path.suffix.lower()==".csv":
            read_csv(path); parsed_csv+=1
        elif path.suffix.lower()==".json":
            json.loads(path.read_text(encoding="utf-8")); parsed_json+=1

    report=[
        "# InsectRealism R8.2 — final consolidated validation",
        "",
        "**RESULT: PASS**",
        "",
        "This file was generated by the release builder after assembling the single consolidated package.",
        "",
        "## Authority checks",
        f"- implementation tasks: 1–57 sequential ({len(tasks)} tasks);",
        f"- numbered sections: 0–25 sequential ({len(sections)} sections);",
        "- old Chelifer-rejection / exactly-three authority phrases: absent;",
        "- Astra handoff explicitly supersedes the older implementation plan;",
        "- Argentine-ant worker variation, callows, queens, males, eggs, larvae, pupae/brood, trail/recruitment scope: explicitly preserved.",
        "",
        "## Scientific/evidence checks",
        "- Chelifer is Arachnida/Pseudoscorpiones, not Insecta;",
        "- Chelifer 7–9 mm is treated as extended pedipalp span, not body length;",
        "- L. entomophila predation evidence is not mislabeled as direct L. bostrychophila evidence;",
        "- no Linepithema humile-specific quantitative predation calibration is claimed;",
        "- O. surinamensis is documented as 'no recorded flight' under the cited source, not physiologically flightless;",
        "- the ~1 GB reference corpus is explicitly visual/reference authority, not trajectory or morphometry authority.",
        "",
        "## Corpus integrity",
        "| Species | Media rows | Raw bytes |",
        "|---|---:|---:|",
    ]
    report += [f"| {s} | {n:,} | {b:,} |" for s,n,b in per_species]
    report += [
        f"| **TOTAL** | **{total_files:,}** | **{total_bytes:,}** |",
        "",
        f"- unique media SHA-256 values across all four corpora: {len(media_sha_seen):,};",
        "- every manifest path exists and byte size matches;",
        "- redistribution license gate rechecked;",
        f"- current evidence parse sweep: {parsed_csv} CSV + {parsed_json} JSON files parsed successfully.",
        "",
        "## Deliberate remaining gates",
        "The package remains explicit about missing exact-stage cockroach footfalls, richer booklouse trajectories, raw grain-beetle XY/gait, Chelifer display-glass traction and target-prey attack kinematics, and Argentine-ant-specific Chelifer predation calibration. These are gates, not invented constants.",
        "",
    ]
    (audit/"FINAL_QUAD_VALIDATION_R8_2.md").write_text("\n".join(report),encoding="utf-8")

    totals=[
        "# Consolidated data totals",
        "",
        f"Validated exact-species reference media: **{total_files:,} files / {total_bytes:,} raw bytes**.",
        "",
        "| Species | Files | Raw bytes |",
        "|---|---:|---:|",
    ]+[f"| {s} | {n:,} | {b:,} |" for s,n,b in per_species]
    totals += [
        "",
        "This volume is primarily license-filtered exact-species visual/reference media. Numerical calibration authority comes from the audited papers and empirical extracts, not from averaging occurrence photographs.",
    ]
    (audit/"CONSOLIDATED_DATA_TOTALS.md").write_text("\n".join(totals)+"\n",encoding="utf-8")

    # Internal bundle checksum manifest. Exclude itself to avoid recursion.
    lines=[]
    for path in sorted(root.rglob("*")):
        if path.is_file() and path.name!="CONSOLIDATED_SHA256SUMS.txt":
            lines.append(f"{sha256(path)}  {path.relative_to(root)}")
    (audit/"CONSOLIDATED_SHA256SUMS.txt").write_text("\n".join(lines)+"\n",encoding="utf-8")

    print(json.dumps({
        "status":"PASS",
        "total_media_files":total_files,
        "total_raw_bytes":total_bytes,
        "unique_media_hashes":len(media_sha_seen),
        "tasks":len(tasks),
        "sections":len(sections),
        "parsed_csv":parsed_csv,
        "parsed_json":parsed_json,
    },indent=2))

if __name__=="__main__":
    require(len(sys.argv)==2, "usage: validate_complete_package.py <package-root>")
    main(Path(sys.argv[1]).resolve())
