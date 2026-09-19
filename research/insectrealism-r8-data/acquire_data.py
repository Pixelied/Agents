#!/usr/bin/env python3
"""Acquire a license-audited large visual research corpus for InsectRealism R8.1.

This is REFERENCE DATA for the four NEW non-ant arthropods. It does not alter
the existing Argentine-ant caste/size/brood research. Images never override
audited physical measurements or exact-stage morphology.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import shutil
import time
from pathlib import Path
from urllib.parse import urlparse

import requests
from PIL import Image

MIB = 1024 * 1024
GIB = 1024 * MIB
GBIF = "https://api.gbif.org/v1"
UA = "InsectRealism-R8.1 (+https://github.com/Pixelied/Agents)"

SPECIES = [
    ("Liposcelis bostrychophila", "liposcelis_bostrychophila", 180, 3000, "adult booklouse secondary crawler"),
    ("Blattella germanica", "blattella_germanica", 420, 5000, "~24 h first-instar target; other stages contextual only"),
    ("Oryzaephilus surinamensis", "oryzaephilus_surinamensis", 300, 4000, "adult sawtoothed grain beetle"),
    ("Chelifer cancroides", "chelifer_cancroides", 300, 4000, "optional rare pseudoscorpion predator"),
]

LINK_ONLY = [
    ["Chelifer cancroides", "adult", "500-fps gait video",
     "https://static-movie-usa.glencoesoftware.com/source/10.1242/292/c5d450faf59d0a1a6f3c8fff7403b5c64591dee2/JEB243930.MovieS2.avi",
     "Tross et al. 2022; DOI 10.1242/jeb.243930",
     "publisher-hosted; redistribution not established",
     "direct gait validation"],
    ["Chelifer cancroides", "adult/nymph cohorts", "predation paper",
     "https://doi.org/10.1080/00218839.2025.2582286",
     "Predation behaviour of C. cancroides against Varroa with alternative psocid prey",
     "publisher rights not verified for redistribution",
     "direct predation evidence"],
    ["Blattella germanica", "study-specific", "114 MB supplementary movement video",
     "https://doi.org/10.1016/j.aspen.2025.102362",
     "repellent-response study",
     "publisher supplement; link only until rights verified",
     "contextual disturbance reference; not first-instar baseline"],
]

OPEN_DATA = [
    ["Oryzaephilus surinamensis", "10.5061/dryad.80dk1", "https://doi.org/10.5061/dryad.80dk1",
     "https://zenodo.org/records/4939311", "direct species/contextual morphology-physiology"],
    ["Blattella germanica", "10.5061/dryad.f82ns", "https://doi.org/10.5061/dryad.f82ns",
     "https://zenodo.org/records/4986109", "direct species/contextual life-history"],
    ["Chelifer cancroides", "10.1242/jeb.243930", "https://doi.org/10.1242/jeb.243930",
     "https://pubmed.ncbi.nlm.nih.gov/35438154/", "direct target-species locomotion"],
]

ALLOWED = [
    r"creativecommons\.org/publicdomain/zero/1\.0",
    r"creativecommons\.org/publicdomain/mark/1\.0",
    r"creativecommons\.org/licenses/by/(?:2\.0|2\.5|3\.0|4\.0)",
    r"creativecommons\.org/licenses/by-sa/(?:2\.0|2\.5|3\.0|4\.0)",
    r"\bcc0\b", r"\bcc by(?:-sa)?(?: |-|/)?(?:2(?:\.0)?|2\.5|3(?:\.0)?|4(?:\.0)?)\b",
    r"public domain",
]

S = requests.Session()
S.headers.update({"User-Agent": UA})


def get_json(url, params=None):
    err = None
    for n in range(4):
        try:
            r = S.get(url, params=params, timeout=(15, 60))
            r.raise_for_status()
            return r.json()
        except Exception as e:
            err = e
            time.sleep(2 ** n)
    raise RuntimeError(f"GET failed: {url}: {err}")


def license_ok(value):
    t = (value or "").strip().lower()
    if not t or "noncommercial" in t or "by-nc" in t or "all rights reserved" in t:
        return False
    return any(re.search(p, t, re.I) for p in ALLOWED)


def sha(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for b in iter(lambda: f.read(MIB), b""):
            h.update(b)
    return h.hexdigest()


def ext_for(url, content_type):
    e = Path(urlparse(url).path).suffix.lower()
    if e in {".jpg", ".jpeg", ".png", ".tif", ".tiff", ".webp"}:
        return e
    ct = (content_type or "").lower()
    return ".png" if "png" in ct else ".tif" if "tiff" in ct else ".webp" if "webp" in ct else ".jpg"


def download(url, tmp):
    with S.get(url, stream=True, timeout=(20, 120), allow_redirects=True) as r:
        r.raise_for_status()
        ct = r.headers.get("Content-Type", "")
        if "html" in ct.lower() or "text/" in ct.lower():
            raise ValueError("not image data")
        declared = int(r.headers.get("Content-Length", "0") or 0)
        if declared > 60 * MIB:
            raise ValueError("single image too large")
        total = 0
        h = hashlib.sha256()
        with tmp.open("wb") as f:
            for block in r.iter_content(MIB):
                if not block:
                    continue
                total += len(block)
                if total > 60 * MIB:
                    raise ValueError("stream exceeded limit")
                f.write(block)
                h.update(block)
    try:
        with Image.open(tmp) as im:
            w, hgt = im.size
            im.verify()
    except Exception:
        tmp.unlink(missing_ok=True)
        raise
    if max(w, hgt) < 320:
        tmp.unlink(missing_ok=True)
        raise ValueError("thumbnail/too small")
    return total, h.hexdigest(), w, hgt, ct


def stage(occ):
    for k in ("lifeStage", "sex", "occurrenceRemarks"):
        if occ.get(k):
            return str(occ[k])[:240]
    return "unlabeled; contextual reference only"


def iter_occurrences(key, maximum):
    offset = 0
    count = 0
    while count < maximum:
        d = get_json(f"{GBIF}/occurrence/search", {
            "taxon_key": key, "media_type": "StillImage", "limit": 300, "offset": offset
        })
        rows = d.get("results") or []
        if not rows:
            return
        for row in rows:
            yield row
            count += 1
            if count >= maximum:
                return
        if d.get("endOfRecords"):
            return
        offset += 300


def collect(name, slug, target_mib, max_records, role, root, writer, seen):
    m = get_json(f"{GBIF}/species/match", {"name": name, "strict": "true"})
    key = m.get("usageKey")
    if not key or m.get("rank") != "SPECIES":
        raise RuntimeError(f"Exact species match failed for {name}: {m}")
    out = root / "05_REFERENCE_MEDIA" / slug
    out.mkdir(parents=True, exist_ok=True)
    tmpdir = root / ".tmp"
    tmpdir.mkdir(exist_ok=True)
    target = target_mib * MIB
    total = files = examined = skip_lic = skip_bad = 0

    for occ in iter_occurrences(key, max_records):
        if total >= target:
            break
        accepted = (occ.get("species") or occ.get("acceptedScientificName") or "").lower()
        if name.lower() not in accepted:
            continue
        occ_lic = (occ.get("license") or "").strip()
        for media in occ.get("media") or []:
            if total >= target:
                break
            examined += 1
            lic = (media.get("license") or occ_lic).strip()
            if not license_ok(lic):
                skip_lic += 1
                continue
            url = media.get("identifier") or media.get("references")
            if not isinstance(url, str) or not url.startswith("http"):
                continue
            tmp = tmpdir / f"{slug}-{os.getpid()}-{files}.bin"
            try:
                size, digest, width, height, ct = download(url, tmp)
            except Exception:
                skip_bad += 1
                tmp.unlink(missing_ok=True)
                continue
            if digest in seen:
                tmp.unlink(missing_ok=True)
                continue
            seen.add(digest)
            final = out / f"{slug}_{files+1:05d}_{digest[:12]}{ext_for(url, ct)}"
            shutil.move(tmp, final)
            total += size
            files += 1
            writer.writerow({
                "species": name, "role": role, "life_stage_or_form": stage(occ),
                "authority_level": "exact-species visual reference; not physical-scale authority unless independently audited",
                "gbif_taxon_key": key, "gbif_occurrence_key": occ.get("key", ""),
                "dataset_key": occ.get("datasetKey", ""), "basis_of_record": occ.get("basisOfRecord", ""),
                "country": occ.get("country", ""), "event_date": occ.get("eventDate", ""),
                "creator": media.get("creator") or occ.get("recordedBy") or "",
                "license": lic, "source_reference": media.get("references") or occ.get("references") or "",
                "download_url": url, "local_path": str(final.relative_to(root)), "bytes": size,
                "width_px": width, "height_px": height, "sha256": digest,
            })
            if files % 25 == 0:
                print(f"{name}: {files} files, {total/MIB:.1f} MiB", flush=True)

    return {
        "species": name, "role": role, "gbif_taxon_key": key, "gbif_match": m,
        "target_bytes": target, "actual_bytes": total, "files": files,
        "media_examined": examined, "skipped_for_license": skip_lic,
        "skipped_download_or_validation": skip_bad, "target_reached": total >= target,
    }


def write_static(root):
    src = root / "02_SOURCES"
    src.mkdir(parents=True, exist_ok=True)
    with (src / "link_only_references.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["species", "stage", "kind", "url", "source", "rights", "use"])
        w.writerows(LINK_ONLY)
    with (src / "open_dataset_index.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["species", "doi", "landing", "mirror", "classification"])
        w.writerows(OPEN_DATA)


def write_readme(root, summaries):
    total = sum(x["actual_bytes"] for x in summaries)
    lines = [
        "# InsectRealism R8.1 acquired data",
        "",
        "This corpus is only for the NEW non-ant arthropods. The full Linepithema humile worker-size/callow/queen/male/egg/larva/pupa/brood/recruitment system remains intact.",
        "",
        "Exact-species images are bundled only under clearly redistributable licenses. Stage-unlabeled media are contextual only. Images never override audited physical scale. Unrelated genomics are not used to pad the byte count.",
        "",
        f"Total redistributable corpus: {total/GIB:.3f} GiB ({total:,} bytes)",
        "",
        "| Species | Files | Actual MiB | Target MiB | Reached |",
        "|---|---:|---:|---:|---|",
    ]
    for x in summaries:
        lines.append(f"| {x['species']} | {x['files']} | {x['actual_bytes']/MIB:.1f} | {x['target_bytes']/MIB:.0f} | {'yes' if x['target_reached'] else 'no'} |")
    lines += [
        "",
        "Chelifer cancroides is an optional rare pseudoscorpion predator, not an insect and not a replacement for ants.",
        "See 05_REFERENCE_MEDIA/MANIFEST.csv for per-file provenance/license/hash and 02_SOURCES/link_only_references.csv for important material not redistributed.",
    ]
    (root / "README.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--output", required=True, type=Path)
    root = ap.parse_args().output
    root.mkdir(parents=True, exist_ok=True)
    write_static(root)
    media = root / "05_REFERENCE_MEDIA"
    media.mkdir(parents=True, exist_ok=True)
    fields = [
        "species","role","life_stage_or_form","authority_level","gbif_taxon_key",
        "gbif_occurrence_key","dataset_key","basis_of_record","country","event_date",
        "creator","license","source_reference","download_url","local_path","bytes",
        "width_px","height_px","sha256",
    ]
    summaries = []
    seen = set()
    with (media / "MANIFEST.csv").open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        for spec in SPECIES:
            summaries.append(collect(*spec, root, writer, seen))
            f.flush()

    (root / "ACQUISITION_SUMMARY.json").write_text(json.dumps(summaries, indent=2) + "\n", encoding="utf-8")
    write_readme(root, summaries)
    shutil.rmtree(root / ".tmp", ignore_errors=True)

    sums = []
    for p in sorted(root.rglob("*")):
        if p.is_file() and p.name != "SHA256SUMS.txt":
            sums.append(f"{sha(p)}  {p.relative_to(root)}")
    (root / "SHA256SUMS.txt").write_text("\n".join(sums) + "\n", encoding="utf-8")

    total = sum(x["actual_bytes"] for x in summaries)
    print(json.dumps({"total_bytes": total, "total_gib": total/GIB, "species": summaries}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
