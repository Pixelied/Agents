#!/usr/bin/env python3
"""Prune R8.2 exact-species reference media to a high-value ~50% R8.3 set.

Selection is NOT random. It scores target-stage relevance, image resolution,
sharpness/edge detail, provenance completeness, and then performs a
source-diversity-aware round-robin. Near-duplicate dHash images are suppressed
where possible before the quota is filled.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import re
from collections import defaultdict, deque
from pathlib import Path

from PIL import Image, ImageFilter, ImageStat

SPEC = {
    "blattella_germanica": {
        "species": "Blattella germanica",
        "target_terms": ["nymph", "instar", "juvenile", "first instar", "1st instar"],
        "penalty_terms": ["adult"],
    },
    "liposcelis_bostrychophila": {
        "species": "Liposcelis bostrychophila",
        "target_terms": ["adult", "female"],
        "penalty_terms": ["egg", "nymph"],
    },
    "oryzaephilus_surinamensis": {
        "species": "Oryzaephilus surinamensis",
        "target_terms": ["adult"],
        "penalty_terms": ["larva", "pupa", "egg"],
    },
    "chelifer_cancroides": {
        "species": "Chelifer cancroides",
        "target_terms": ["adult", "male", "female"],
        "penalty_terms": ["nymph", "protonymph", "deutonymph", "tritonymph"],
    },
}


def sha256(path: Path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for b in iter(lambda: f.read(1024 * 1024), b""):
            h.update(b)
    return h.hexdigest()


def dhash_and_sharpness(path: Path):
    with Image.open(path) as im:
        im = im.convert("L")
        # dHash for near-duplicate detection.
        small = im.resize((9, 8))
        px = list(small.getdata())
        bits = 0
        for y in range(8):
            for x in range(8):
                bits = (bits << 1) | (1 if px[y*9+x] > px[y*9+x+1] else 0)
        # Edge variance as a cheap sharpness/detail proxy.
        sample = im.copy()
        sample.thumbnail((384, 384))
        edges = sample.filter(ImageFilter.FIND_EDGES)
        var = ImageStat.Stat(edges).var[0]
        return bits, float(var)


def hamming(a: int, b: int) -> int:
    return (a ^ b).bit_count()


def normalize(vals, v):
    if not vals:
        return 0.0
    lo, hi = min(vals), max(vals)
    if hi <= lo:
        return 0.5
    return (v - lo) / (hi - lo)


def metadata_score(row):
    keys = ["creator","license","source_reference","download_url","dataset_key",
            "country","event_date","basis_of_record"]
    return sum(1 for k in keys if (row.get(k) or "").strip()) / len(keys)


def stage_score(row, cfg):
    text = " ".join([
        row.get("life_stage_or_form",""),
        row.get("role",""),
        row.get("authority_level",""),
    ]).lower()
    score = 0.0
    if any(t in text for t in cfg["target_terms"]):
        score += 1.0
    elif "unlabeled" in text or not row.get("life_stage_or_form","").strip():
        score += 0.25
    if any(t in text for t in cfg["penalty_terms"]):
        score -= 0.5
    return score


def diversity_key(row):
    ds = (row.get("dataset_key") or "").strip()
    creator = (row.get("creator") or "").strip()
    source = (row.get("source_reference") or "").strip()
    return ds or creator or source or "unknown-source"


def choose(rows, target, cfg):
    res_vals = [math.log1p(max(1,int(r.get("width_px") or 0))*max(1,int(r.get("height_px") or 0))) for r in rows]
    sharp_vals = [r["_sharpness"] for r in rows]
    byte_vals = [math.log1p(int(r.get("bytes") or 0)) for r in rows]

    for r in rows:
        res = math.log1p(max(1,int(r.get("width_px") or 0))*max(1,int(r.get("height_px") or 0)))
        b = math.log1p(int(r.get("bytes") or 0))
        r["_score"] = (
            4.0 * stage_score(r,cfg)
            + 2.5 * normalize(res_vals,res)
            + 2.0 * normalize(sharp_vals,r["_sharpness"])
            + 0.7 * normalize(byte_vals,b)
            + 1.4 * metadata_score(r)
        )

    groups = defaultdict(list)
    for r in rows:
        groups[diversity_key(r)].append(r)
    for g in groups.values():
        g.sort(key=lambda x: x["_score"], reverse=True)

    # Start with source-diverse round robin.
    group_order = sorted(groups, key=lambda k: groups[k][0]["_score"], reverse=True)
    queues = {k: deque(groups[k]) for k in group_order}
    selected = []
    selected_dh = []

    def add_if_diverse(r):
        # Suppress very close perceptual duplicates while there are alternatives.
        dh = r["_dhash"]
        if any(hamming(dh, old) <= 4 for old in selected_dh):
            return False
        selected.append(r)
        selected_dh.append(dh)
        return True

    progress = True
    while len(selected) < target and progress:
        progress = False
        for k in group_order:
            q = queues[k]
            while q and len(selected) < target:
                cand = q.popleft()
                progress = True
                if add_if_diverse(cand):
                    break

    # Fill quota with best remaining even if perceptually similar.
    if len(selected) < target:
        selected_ids = {id(r) for r in selected}
        remaining = sorted((r for r in rows if id(r) not in selected_ids),
                           key=lambda x:x["_score"], reverse=True)
        selected.extend(remaining[:target-len(selected)])
    return selected[:target]


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--reference-root",required=True,type=Path,
                    help="R8.2 02_REFERENCE_DATA directory")
    ap.add_argument("--keep-ratio",type=float,default=0.50)
    ap.add_argument("--report-dir",required=True,type=Path)
    args=ap.parse_args()
    if not 0.40 <= args.keep_ratio <= 0.60:
        raise SystemExit("R8.3 keep ratio must stay within user's 40-60% requested band.")
    args.report_dir.mkdir(parents=True,exist_ok=True)

    report=[]
    summary=[]
    total_before_files=total_after_files=0
    total_before_bytes=total_after_bytes=0

    for slug,cfg in SPEC.items():
        base=args.reference_root/slug
        manifest=base/"05_REFERENCE_MEDIA"/"MANIFEST.csv"
        if not manifest.exists():
            raise SystemExit(f"Missing R8.2 manifest: {manifest}")
        with manifest.open(newline="",encoding="utf-8-sig") as f:
            rows=list(csv.DictReader(f))
        if not rows:
            raise SystemExit(f"Empty manifest: {manifest}")

        for i,r in enumerate(rows):
            path=base/r["local_path"]
            if not path.exists():
                raise SystemExit(f"Referenced image missing: {path}")
            digest=sha256(path)
            if digest.lower()!=r["sha256"].strip().lower():
                raise SystemExit(f"SHA mismatch before pruning: {path}")
            try:
                dh,sharp=dhash_and_sharpness(path)
            except Exception as e:
                raise SystemExit(f"Image decode/quality scan failed: {path}: {e}")
            r["_path"]=path
            r["_dhash"]=dh
            r["_sharpness"]=sharp
            r["_original_index"]=i

        target=max(1,round(len(rows)*args.keep_ratio))
        selected=choose(rows,target,cfg)
        selected_sha={r["sha256"] for r in selected}

        before_bytes=sum(int(r["bytes"]) for r in rows)
        after_bytes=sum(int(r["bytes"]) for r in selected)
        total_before_files+=len(rows); total_after_files+=len(selected)
        total_before_bytes+=before_bytes; total_after_bytes+=after_bytes

        original_manifest=manifest.with_name("MANIFEST_R8_2_FULL.csv")
        manifest.rename(original_manifest)
        old_sums=base/"SHA256SUMS.txt"
        if old_sums.exists():
            old_sums.rename(base/"SHA256SUMS_R8_2_FULL.txt")
        old_summary=base/"ACQUISITION_SUMMARY.json"
        if old_summary.exists():
            old_summary.rename(base/"ACQUISITION_SUMMARY_R8_2_FULL.json")

        fieldnames=[k for k in rows[0].keys() if not k.startswith("_")]
        with manifest.open("w",newline="",encoding="utf-8") as f:
            w=csv.DictWriter(f,fieldnames=fieldnames)
            w.writeheader()
            for r in sorted(selected,key=lambda x:x["_original_index"]):
                w.writerow({k:r.get(k,"") for k in fieldnames})

        # Delete only reference media that did not make R8.3 selection.
        for r in rows:
            kept=r["sha256"] in selected_sha
            report.append({
                "species":cfg["species"],
                "local_path":r["local_path"],
                "sha256":r["sha256"],
                "life_stage_or_form":r.get("life_stage_or_form",""),
                "dataset_key":r.get("dataset_key",""),
                "width_px":r.get("width_px",""),
                "height_px":r.get("height_px",""),
                "bytes":r.get("bytes",""),
                "sharpness_edge_variance":f"{r['_sharpness']:.4f}",
                "selection_score":f"{r['_score']:.5f}",
                "r8_3_status":"KEEP" if kept else "PRUNED",
                "reason":"top quality/stage/diversity selection" if kept else "lower-value visual redundancy after R8.3 rebalance"
            })
            if not kept:
                r["_path"].unlink()

        # Rebuild component checksum file for the pruned corpus.
        checksum_lines=[]
        for p in sorted(base.rglob("*")):
            if p.is_file() and p.name not in {"SHA256SUMS.txt","SHA256SUMS_R8_3.txt"}:
                checksum_lines.append(f"{sha256(p)}  {p.relative_to(base)}")
        (base/"SHA256SUMS_R8_3.txt").write_text("\n".join(checksum_lines)+"\n",encoding="utf-8")

        r8_3_summary={
            "species":cfg["species"],
            "selection_policy":"R8.3 quality/stage/provenance/diversity prune",
            "requested_keep_ratio":args.keep_ratio,
            "r8_2_files":len(rows),
            "r8_3_files":len(selected),
            "retained_count_ratio":len(selected)/len(rows),
            "r8_2_bytes":before_bytes,
            "r8_3_bytes":after_bytes,
            "retained_byte_ratio":after_bytes/before_bytes if before_bytes else 0,
        }
        (base/"ACQUISITION_SUMMARY_R8_3.json").write_text(
            json.dumps(r8_3_summary,indent=2)+"\n",encoding="utf-8")
        summary.append(r8_3_summary)

    with (args.report_dir/"VISUAL_PRUNING_REPORT.csv").open("w",newline="",encoding="utf-8") as f:
        fields=["species","local_path","sha256","life_stage_or_form","dataset_key",
                "width_px","height_px","bytes","sharpness_edge_variance","selection_score",
                "r8_3_status","reason"]
        w=csv.DictWriter(f,fieldnames=fields); w.writeheader(); w.writerows(report)

    totals={
        "keep_ratio_request":args.keep_ratio,
        "files_before":total_before_files,
        "files_after":total_after_files,
        "actual_file_keep_ratio":total_after_files/total_before_files,
        "bytes_before":total_before_bytes,
        "bytes_after":total_after_bytes,
        "actual_byte_keep_ratio":total_after_bytes/total_before_bytes,
        "per_species":summary,
    }
    (args.report_dir/"VISUAL_REBALANCE_SUMMARY.json").write_text(
        json.dumps(totals,indent=2)+"\n",encoding="utf-8")
    (args.report_dir/"REFERENCE_MEDIA_SELECTION_METHOD.md").write_text(
        """# R8.3 reference-media selection method

R8.3 deliberately retains about half of the R8.2 visual corpus rather than
deleting almost all reference imagery.

Selection is deterministic and evidence-oriented, not random:

1. Target-stage/form labels are preferred where source metadata contains them.
2. Higher-resolution images receive additional score.
3. Edge-detail variance is used as a modest sharpness/detail proxy.
4. Complete creator/license/source/date/dataset metadata is rewarded.
5. Source/dataset diversity is preserved using a round-robin selection.
6. Perceptual dHash suppresses close visual duplicates while alternatives exist.
7. The quota is then filled by the highest remaining scores.

This selection does **not** promote a photograph to morphology or scale
authority. Exact measurements, calibrated specimen sources, published tables,
and raw study datasets retain higher scientific authority.
""",encoding="utf-8")
    print(json.dumps(totals,indent=2))


if __name__=="__main__":
    main()
