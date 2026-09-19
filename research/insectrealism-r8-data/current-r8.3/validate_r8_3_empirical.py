#!/usr/bin/env python3
from __future__ import annotations

import csv, hashlib, json, os, re, sys, zipfile
from collections import Counter, defaultdict
from pathlib import Path

EXPECTED_ORIGINAL = {
    "blattella_germanica": ("Blattella germanica", 383),
    "liposcelis_bostrychophila": ("Liposcelis bostrychophila", 322),
    "oryzaephilus_surinamensis": ("Oryzaephilus surinamensis", 333),
    "chelifer_cancroides": ("Chelifer cancroides", 340),
}
REQUIRED_CLASSES = {
    "raw_repository_data",
    "raw_publisher_supplement",
    "published_aggregate_extract",
}
REQUIRED_SPECIES = {
    "Blattella germanica",
    "Liposcelis bostrychophila",
    "Oryzaephilus surinamensis",
    "Chelifer cancroides",
}
CRITICAL_SIGNALS = {
    "Blattella germanica": [
        "10.5061/dryad",
        "PMC9385682",
        "PMC6393502",
    ],
    "Liposcelis bostrychophila": [
        "10.17632/5zdf96kr72.1",
        "PMC12028104",
    ],
    "Oryzaephilus surinamensis": [
        "4939311",
        "PMC4641671",
        "PMC8113238",
    ],
    "Chelifer cancroides": [
        "PMC8778599",
        "10.1242/jeb.243930",
    ],
}

def fail(msg):
    raise SystemExit("R8.3 VALIDATION FAILURE: "+msg)

def require(cond,msg):
    if not cond: fail(msg)

def sha256(p:Path):
    h=hashlib.sha256()
    with p.open("rb") as f:
        for b in iter(lambda:f.read(1024*1024),b""): h.update(b)
    return h.hexdigest()

def read_csv(p:Path):
    with p.open(encoding="utf-8-sig",newline="") as f:
        return list(csv.DictReader(f))

def main(root:Path):
    empirical=root/"02_EMPIRICAL_DATA"
    visual=root/"03_REFERENCE_MEDIA"
    start=root/"00_START_HERE"
    evidence=root/"01_CURRENT_EVIDENCE"
    require(empirical.is_dir(),"02_EMPIRICAL_DATA missing")
    require(visual.is_dir(),"03_REFERENCE_MEDIA missing")
    require(start.is_dir(),"00_START_HERE missing")
    require(evidence.is_dir(),"01_CURRENT_EVIDENCE missing")

    # Single-authority plan / ant-scope audit.
    plan_path=start/"CURRENT_IMPLEMENTATION_PLAN_R8_3.md"
    require(plan_path.exists(),"R8.3 implementation plan missing")
    plan=plan_path.read_text(encoding="utf-8")
    tasks=[int(x) for x in re.findall(r"^## Task (\d+)\b",plan,re.M)]
    sections=[int(x) for x in re.findall(r"^# (\d+)\.",plan,re.M)]
    require(tasks==list(range(1,58)),f"R8.3 plan tasks are not exactly 1..57: {tasks}")
    require(sections==list(range(0,26)),f"R8.3 numbered plan sections are not exactly 0..25: {sections}")
    for phrase in [
        "single current implementation authority",
        "Linepithema humile",
        "worker morphology",
        "callow",
        "queens",
        "males",
        "eggs",
        "larvae",
        "pupae",
        "02_EMPIRICAL_DATA",
        "03_REFERENCE_MEDIA",
        "raw_repository_data",
        "optional rare pseudoscorpion predator",
    ]:
        require(phrase.lower() in plan.lower(),f"current plan missing required concept: {phrase}")
    for stale in [
        "exactly three secondary implementation targets",
        "Chelifer cancroides: not a microfauna target",
        "Do not force a fourth",
        "## Task 7A",
    ]:
        require(stale not in plan,f"stale plan authority remains: {stale}")
    handoff=(start/"ASTRA_READ_THIS_FIRST_R8_3.md").read_text(encoding="utf-8")
    require("previous R8.2 plan is superseded" in handoff,"Astra handoff does not supersede R8.2 clearly")
    require("Do not delete, reduce or replace" in handoff,"Astra handoff does not explicitly preserve ant system")
    require(not list(start.glob("*R8_2*")),"current START_HERE contains stale R8.2 authority filenames")

    # Visual curation must really remove ~40-60%, not silently keep the old image dump.
    cur=json.loads((visual/"CURATION_SUMMARY.json").read_text())
    byslug={re.sub(r"[^a-z0-9]+","_",x["species"].lower()).strip("_"):x for x in cur}
    total_orig=total_kept=0
    for slug,(species,orig) in EXPECTED_ORIGINAL.items():
        row=next((x for x in cur if x["species"]==species),None)
        require(row is not None,f"curation summary missing {species}")
        require(int(row["original_files"])==orig,f"{species} original visual count changed")
        frac=float(row["keep_fraction"])
        require(0.50 <= frac <= 0.60,f"{species} keep fraction {frac:.3f} outside 50-60%")
        byte_frac=float(row.get("byte_keep_fraction",1.0))
        require(byte_frac <= 0.65,f"{species} retained visual bytes {byte_frac:.3f} exceeds 65% cap")
        total_orig += int(row["original_files"]); total_kept += int(row["kept_files"])
    require(total_orig==1378,f"unexpected original visual total {total_orig}")
    require(0.50 <= total_kept/total_orig <= 0.60,"overall visual keep fraction outside 50-60%")

    kept=read_csv(visual/"KEPT_REFERENCE_MEDIA.csv")
    dropped=read_csv(visual/"DROPPED_REFERENCE_MEDIA.csv")
    require(len(kept)==total_kept,"kept visual manifest row count mismatch")
    require(len(kept)+len(dropped)==1378,"kept+dropped visual rows != original 1378")
    hashes=set()
    for r in kept:
        p=root/r["local_path"]
        require(p.exists(),f"kept visual file missing: {p}")
        require(p.stat().st_size==int(r["bytes"]),f"visual byte mismatch: {p}")
        require(sha256(p)==r["sha256"],f"visual SHA mismatch: {p}")
        require(r["sha256"] not in hashes,f"duplicate retained visual hash: {r['sha256']}")
        hashes.add(r["sha256"])
    for r in dropped:
        require(r.get("download_url") or r.get("source_reference"),"dropped visual row lost source URL")
        require(r.get("sha256"),"dropped visual row lost SHA")

    # Empirical manifest integrity.
    rows=read_csv(empirical/"EMPIRICAL_DATA_MANIFEST.csv")
    require(len(rows)>=15,f"too few empirical files ({len(rows)}); acquisition likely failed")
    classes=Counter(r["data_class"] for r in rows)
    for cls in REQUIRED_CLASSES:
        require(classes[cls]>0,f"required empirical class absent: {cls}")
    species=Counter(r["species"] for r in rows)
    for sp in REQUIRED_SPECIES:
        require(species[sp]>0,f"no empirical files for {sp}")

    empirical_hashes=set()
    ext_counts=Counter()
    class_bytes=Counter()
    species_bytes=Counter()
    for r in rows:
        p=root/r["local_path"]
        require(p.exists(),f"empirical file missing: {p}")
        require(p.stat().st_size==int(r["bytes"]),f"empirical byte mismatch: {p}")
        digest=sha256(p)
        require(digest==r["sha256"],f"empirical SHA mismatch: {p}")
        require(digest not in empirical_hashes,f"duplicate empirical bytes bundled twice: {p}")
        empirical_hashes.add(digest)
        ext_counts[p.suffix.lower()]+=1
        class_bytes[r["data_class"]]+=int(r["bytes"])
        species_bytes[r["species"]]+=int(r["bytes"])
        require(r["study"].strip(),"empirical row missing study")
        require(r["repository_or_publisher"].strip(),"empirical row missing repository/publisher")
        require(r["data_class"].strip(),"empirical row missing data class")
        require(r["directness"].strip(),"empirical row missing directness")
        require(r["source_url"].strip(),"empirical row missing source URL")
        require(r["variables_or_contents"].strip(),"empirical row missing contents description")
        require(r["intended_implementation_use"].strip(),"empirical row missing intended use")

    require(sum(class_bytes.values())>=100_000,
            "empirical payload is suspiciously tiny; raw study acquisition likely failed")
    require(ext_counts[".xlsx"]+ext_counts[".xls"]+ext_counts[".csv"]>=10,
            "not enough tabular study data files")
    require(classes["raw_repository_data"]+classes["raw_publisher_supplement"]>=8,
            "not enough actual raw/source-level study data")

    # Each target must have source-level empirical bytes, not just our aggregate CSV.
    for sp in REQUIRED_SPECIES:
        source_rows=[r for r in rows if r["species"]==sp and r["data_class"] in
                     {"raw_repository_data","raw_publisher_supplement","study_video"}]
        require(source_rows,f"{sp} has no raw/source-level study file")

    # Behavioral/movement material must be present, even though exact historical XY tracks
    # were not always publicly released.
    behavior_rows=[
        r for r in rows
        if re.search(r"(behav|movement|locom|shelter|thigmo|courtship|groom|walking|preference|feeding|predat)",
                     " ".join([r["study"],r["variables_or_contents"],r["intended_implementation_use"]]),re.I)
    ]
    require(len(behavior_rows)>=5,f"too little behavior/movement empirical material ({len(behavior_rows)} rows)")
    require(any(r["species"]=="Blattella germanica" for r in behavior_rows),
            "cockroach behavioral raw/supplement data missing")
    require(any(r["species"]=="Chelifer cancroides" for r in behavior_rows),
            "Chelifer behavior/predation data missing")

    # Critical source signals must appear either locally or in the remote-only manifest.
    remote=(empirical/"REMOTE_ONLY_RESTRICTED.csv").read_text(encoding="utf-8-sig")
    local_text="\n".join(" ".join(r.values()) for r in rows)
    evidence_blob=local_text+"\n"+remote
    for sp,signals in CRITICAL_SIGNALS.items():
        for sig in signals:
            require(sig.lower() in evidence_blob.lower(),f"critical R8.3 source signal absent: {sp} / {sig}")

    # Critical movement raw-data gaps must remain explicit instead of becoming fake data.
    require("10.1016/S0022-5193(03)00277-7" in remote,"first-instar raw track gap not documented")
    require("10.1002/ps.1634" in remote,"booklouse raw track gap not documented")
    require("10.1603/0046-225X-33.1.75" in remote,"grain-beetle EthoVision raw-track gap not documented")
    require("10.1242/jeb.243930" in remote,"Chelifer restricted locomotion supplement not documented")

    # Acquisition failures are permitted only if we still meet all hard empirical gates.
    failures=json.loads((empirical/"ACQUISITION_FAILURES.json").read_text())
    inventory=empirical/"WORKBOOK_INVENTORY.json"
    require(inventory.exists(),"workbook inventory missing")

    report={
        "status":"PASS",
        "authority":{"tasks":len(tasks),"sections":len(sections),"plan":str(plan_path.relative_to(root))},
        "visual":{"original_files":total_orig,"kept_files":total_kept,"dropped_files":total_orig-total_kept,
                  "keep_fraction":total_kept/total_orig,"unique_kept_hashes":len(hashes)},
        "empirical":{"files":len(rows),"bytes":sum(int(r["bytes"]) for r in rows),
                     "classes":dict(classes),"class_bytes":dict(class_bytes),
                     "species_files":dict(species),"species_bytes":dict(species_bytes),
                     "extensions":dict(ext_counts),"behavior_rows":len(behavior_rows),
                     "unique_hashes":len(empirical_hashes)},
        "acquisition_failures":failures,
    }
    (root/"04_RELEASE_AUDIT"/"R8_3_EMPIRICAL_VALIDATION.json").write_text(json.dumps(report,indent=2)+"\n")
    print(json.dumps(report,indent=2))

if __name__=="__main__":
    if len(sys.argv)!=2: fail("usage: validate_r8_3_empirical.py <package-root>")
    main(Path(sys.argv[1]).resolve())
