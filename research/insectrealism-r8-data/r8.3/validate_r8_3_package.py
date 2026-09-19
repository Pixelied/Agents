#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import sys
import gzip
import zipfile
from pathlib import Path

TARGET_SPECIES = {
    "Liposcelis bostrychophila",
    "Blattella germanica",
    "Oryzaephilus surinamensis",
    "Chelifer cancroides",
}
EXPECTED_R82_VISUAL_COUNT = 1378
EXPECTED_R82_VISUAL_BYTES = 1_047_195_171

def die(msg: str):
    raise SystemExit("R8.3 VALIDATION FAILURE: " + msg)

def require(cond: bool, msg: str):
    if not cond:
        die(msg)

def read_csv(path: Path):
    with path.open(newline="", encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))

def sha256(path: Path):
    h=hashlib.sha256()
    with path.open("rb") as f:
        for b in iter(lambda:f.read(1024*1024),b""):
            h.update(b)
    return h.hexdigest()

def main(root: Path):
    start=root/"00_START_HERE"
    study=root/"02_STUDY_DATA"
    refs=root/"03_REFERENCE_MEDIA"
    audit=root/"04_RELEASE_AUDIT"

    plan=(start/"CURRENT_IMPLEMENTATION_PLAN_R8_3.md").read_text(encoding="utf-8")
    tasks=[int(x) for x in re.findall(r"^## Task (\d+)\b",plan,re.M)]
    sections=[int(x) for x in re.findall(r"^# (\d+)\.",plan,re.M)]
    require(tasks==list(range(1,58)),f"implementation tasks not sequential 1..57: {tasks}")
    require(sections==list(range(0,26)),f"plan sections not sequential 0..25: {sections}")
    require("RAW_OPEN_DATA" in plan and "PUBLISHED_DIRECT_NUMERIC" in plan,
            "R8.3 plan lacks empirical-data authority hierarchy")
    require("Linepithema humile" in plan and "callow" in plan.lower()
            and "queens" in plan.lower() and "pupae" in plan.lower(),
            "R8.3 plan does not explicitly preserve full ant scope")
    require("single current implementation authority" in plan,
            "R8.3 plan is not explicitly the single authority")
    require("1,378" in plan and "R8.2 package was too image-heavy" in plan,
            "R8.3 plan does not document why the data rebalance exists")

    # Visual rebalance.
    vis_summary=json.loads((audit/"VISUAL_REBALANCE_SUMMARY.json").read_text(encoding="utf-8"))
    before=int(vis_summary["files_before"])
    after=int(vis_summary["files_after"])
    ratio=float(vis_summary["actual_file_keep_ratio"])
    require(before==EXPECTED_R82_VISUAL_COUNT, f"visual pre-prune count changed: {before}")
    require(0.40 <= ratio <= 0.60, f"visual retention ratio {ratio:.3f} outside requested 40-60%")
    require(after < before, "visual pruning did not reduce image count")
    require(after >= 550, "visual set was over-pruned; user explicitly did not want almost all images deleted")

    visual_manifest_rows=0
    visual_bytes=0
    visual_hashes=set()
    for slug_dir in sorted(p for p in refs.iterdir() if p.is_dir()):
        manifest=slug_dir/"05_REFERENCE_MEDIA"/"MANIFEST.csv"
        if not manifest.exists():
            continue
        rows=read_csv(manifest)
        visual_manifest_rows += len(rows)
        for r in rows:
            p=slug_dir/r["local_path"]
            require(p.exists(),f"retained visual missing: {p}")
            require(p.stat().st_size==int(r["bytes"]),f"retained visual byte mismatch: {p}")
            digest=sha256(p)
            require(digest==r["sha256"].strip().lower(),f"retained visual SHA mismatch: {p}")
            require(digest not in visual_hashes,f"duplicate retained visual SHA: {digest}")
            visual_hashes.add(digest)
            visual_bytes += int(r["bytes"])
    require(visual_manifest_rows==after,
            f"retained visual manifest rows {visual_manifest_rows} != rebalance summary {after}")

    # Empirical raw/open/source-data package.
    manifest_path=study/"STUDY_DATA_MANIFEST.csv"
    summary_path=study/"STUDY_DATA_SUMMARY.json"
    require(manifest_path.exists(),"missing STUDY_DATA_MANIFEST.csv")
    require(summary_path.exists(),"missing STUDY_DATA_SUMMARY.json")
    rows=read_csv(manifest_path)
    summary=json.loads(summary_path.read_text(encoding="utf-8"))
    require(len(rows)==int(summary["files_acquired"]),"study manifest row count != acquisition summary")
    study_bytes=sum(int(r["bytes"]) for r in rows)
    require(study_bytes==int(summary["bytes_acquired"]),"study-data byte total mismatch")
    require(len(rows)>=10,f"too few empirical source files acquired: {len(rows)}")
    require(study_bytes>=100_000_000,
            f"too little actual study data acquired ({study_bytes:,} bytes); build refuses an image-heavy handoff")

    species={r["species"] for r in rows}
    raw_species={r["species"] for r in rows if r["evidence_class"]=="raw_open_study_data"}
    require(len(raw_species)>=2,
            f"raw/open study data did not cover enough target taxa: {sorted(raw_species)}")

    classes={r["evidence_class"] for r in rows}
    require("raw_open_study_data" in classes,"no raw/open study files acquired")
    # Experimental video is valuable, but the best target locomotion movies are
    # publisher-copyrighted. Do not fail or pirate files just to satisfy a byte/class quota.
    # The remote-only registry + direct published locomotion tables below are mandatory.

    study_hashes=set()
    validated_containers=0
    for r in rows:
        p=study/r["local_path"]
        require(p.exists(),f"study data file missing: {p}")
        require(p.stat().st_size==int(r["bytes"]),f"study data byte mismatch: {p}")
        d=sha256(p)
        require(d==r["sha256"].strip().lower(),f"study data SHA mismatch: {p}")
        require(d not in study_hashes,f"duplicate empirical file SHA: {d}")
        study_hashes.add(d)
        require((r.get("license") or "").strip(),f"study file lacks license metadata: {p}")
        require((r.get("intended_use") or "").strip(),f"study file lacks intended use: {p}")
        require((r.get("baseline_calibration_permission") or "").strip(),
                f"study file lacks calibration permission: {p}")

        # Validate common research-data containers without assuming UTF-8.
        # One proven Edmond CSV uses a micro-symbol byte outside UTF-8; that is
        # legitimate tabular data, not a reason to discard the study.
        ext=p.suffix.lower()
        if ext in {".xlsx",".zip"}:
            try:
                with zipfile.ZipFile(p) as zf:
                    bad=zf.testzip()
                    require(bad is None,f"corrupt ZIP/XLSX member {bad}: {p}")
                    require(len(zf.namelist())>0,f"empty ZIP/XLSX container: {p}")
                validated_containers+=1
            except zipfile.BadZipFile:
                die(f"invalid ZIP/XLSX container: {p}")
        elif ext==".gz":
            try:
                with gzip.open(p,"rb") as gf:
                    require(bool(gf.read(64)),f"empty gzip data file: {p}")
                validated_containers+=1
            except OSError:
                die(f"invalid gzip data file: {p}")
        elif ext==".xls":
            head=p.read_bytes()[:8]
            require(head==bytes.fromhex("d0cf11e0a1b11ae1"),f"invalid legacy XLS OLE header: {p}")
            validated_containers+=1
        elif ext in {".csv",".tsv",".txt"}:
            raw=p.read_bytes()[:65536]
            require(len(raw)>0,f"empty text/tabular data file: {p}")
            decoded=False
            for enc in ("utf-8-sig","cp1252","latin-1"):
                try:
                    raw.decode(enc)
                    decoded=True
                    break
                except UnicodeDecodeError:
                    pass
            require(decoded,f"tabular/text data not decodable under audited encodings: {p}")
            validated_containers+=1

    # Published numerical movement/biology data: small but high authority.
    num=study/"PUBLISHED_DIRECT_NUMERIC"
    required_numeric=[
        "blattella_2003_first_instar_bounded_model.csv",
        "chelifer_2022_morphology.csv",
        "chelifer_2022_locomotion.csv",
        "chelifer_2022_kinematic_regressions.csv",
        "oryzaephilus_direct_behavior_aggregates.csv",
        "liposcelis_behavior_and_development_aggregates.csv",
    ]
    numeric_rows=0
    for name in required_numeric:
        p=num/name
        require(p.exists(),f"missing published direct numerical dataset: {name}")
        r=read_csv(p)
        require(r,f"empty published numerical dataset: {name}")
        numeric_rows += len(r)
        # Every row must identify source/evidence class.
        for row in r:
            require((row.get("source") or "").strip(),f"numeric row lacks source: {name}")
            require((row.get("evidence_class") or "").strip(),f"numeric row lacks evidence class: {name}")
    require(numeric_rows>=50,f"published direct numerical layer unexpectedly small: {numeric_rows} rows")

    # Remote-only registry is mandatory because some best movement recordings are copyrighted.
    remote=study/"REMOTE_ONLY_HIGH_VALUE_DATA.csv"
    require(remote.exists(),"missing remote-only high-value data registry")
    remote_rows=read_csv(remote)
    require(any(r["species"]=="Chelifer cancroides" and "500-fps" in (r["role"] or "")
                for r in remote_rows),
            "Chelifer direct high-speed locomotion recording not indexed remotely")
    require(any(r["species"]=="Blattella germanica" and
                ("locomotion" in (r["role"] or "").lower() or "movement" in (r["role"] or "").lower())
                for r in remote_rows),
            "German cockroach high-value movement/locomotion source not indexed remotely")
    # Every target taxon must still have direct published numerical evidence even
    # if its original raw track files were never publicly archived.
    numeric_species_from_files=set()
    for name in required_numeric:
        lower=name.lower()
        if "blattella" in lower: numeric_species_from_files.add("Blattella germanica")
        if "chelifer" in lower: numeric_species_from_files.add("Chelifer cancroides")
        if "oryzaephilus" in lower: numeric_species_from_files.add("Oryzaephilus surinamensis")
        if "liposcelis" in lower: numeric_species_from_files.add("Liposcelis bostrychophila")
    require(TARGET_SPECIES <= numeric_species_from_files,
            f"published direct numerical layer does not cover all target taxa: {sorted(numeric_species_from_files)}")

    # Acquisition failures remain visible, never silently omitted.
    errors=study/"ACQUISITION_ERRORS_AND_BLOCKS.csv"
    require(errors.exists(),"missing acquisition errors/blocks report")
    error_rows=read_csv(errors)

    report=[
        "# InsectRealism R8.3 — empirical rebalance validation",
        "",
        "**RESULT: PASS**",
        "",
        "R8.3 corrects the R8.2 image-heavy research package while preserving the same species/ant scope.",
        "",
        "## Visual-reference rebalance",
        f"- R8.2 reference files: {before:,}",
        f"- R8.3 retained reference files: {after:,}",
        f"- retained by count: {ratio*100:.1f}%",
        f"- retained visual bytes: {visual_bytes:,}",
        f"- unique retained visual SHA-256s: {len(visual_hashes):,}",
        "",
        "## Actual study data",
        f"- acquired reusable study/source files: {len(rows):,}",
        f"- acquired reusable study/source bytes: {study_bytes:,}",
        f"- empirical raw/context species represented: {', '.join(sorted(species))}",
        f"- taxa with raw/open files: {', '.join(sorted(raw_species))}",
        f"- evidence classes: {', '.join(sorted(classes))}",
        "- redistributable experimental video is preferred when available but is not required when publisher rights block redistribution;",
        f"- direct published numerical rows: {numeric_rows:,}",
        f"- remote-only high-value records: {len(remote_rows):,}",
        f"- acquisition failures/blocks recorded transparently: {len(error_rows):,}",
        f"- empirical containers/signatures validated: {validated_containers:,}",
        "",
        "The validator intentionally does not count the visual-reference corpus as movement data.",
        "Contextual genomics/transcriptomics also cannot silently calibrate movement.",
        "Exact-stage/context restrictions from manifests and the R8.3 plan remain binding.",
        "",
    ]
    (audit/"FINAL_R8_3_EMPIRICAL_VALIDATION.md").write_text("\n".join(report),encoding="utf-8")

    print(json.dumps({
        "status":"PASS",
        "visual_files_before":before,
        "visual_files_after":after,
        "visual_keep_ratio":ratio,
        "visual_bytes_after":visual_bytes,
        "study_files":len(rows),
        "study_bytes":study_bytes,
        "study_species":sorted(species),
        "raw_open_species":sorted(raw_species),
        "study_evidence_classes":sorted(classes),
        "published_numeric_rows":numeric_rows,
        "remote_only_records":len(remote_rows),
        "acquisition_errors_or_blocks":len(error_rows),
        "validated_empirical_containers":validated_containers,
        "plan_tasks":len(tasks),
        "plan_sections":len(sections),
    },indent=2))

if __name__=="__main__":
    ap=argparse.ArgumentParser()
    ap.add_argument("root",type=Path)
    args=ap.parse_args()
    main(args.root.resolve())
