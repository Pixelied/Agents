#!/usr/bin/env python3
from __future__ import annotations

import csv, hashlib, io, json, math, os, re, shutil, tarfile, tempfile, time, zipfile
from collections import defaultdict
from pathlib import Path
from urllib.parse import quote, urlparse
import xml.etree.ElementTree as ET

import requests

UA = "InsectRealism-R8.3-Empirical/1.0 (+https://github.com/Pixelied/Agents)"
S = requests.Session()
S.headers.update({"User-Agent": UA})
MIB = 1024 * 1024

SPECIES = {
    "blattella_germanica": "Blattella germanica",
    "liposcelis_bostrychophila": "Liposcelis bostrychophila",
    "oryzaephilus_surinamensis": "Oryzaephilus surinamensis",
    "chelifer_cancroides": "Chelifer cancroides",
}
TARGET_KEEP_FRACTION = 0.55

DRYAD = [
    ("Blattella germanica", "10.5061/dryad.063h8", "raw_repository_data",
     "diet intake, growth, body mass, nitrogen, survival; adult contextual behavior/physiology"),
    ("Blattella germanica", "10.5061/dryad.6tt66", "raw_repository_data",
     "behavioral and electrophysiological taste-response data"),
    ("Blattella germanica", "10.5061/dryad.jsxksn08r", "raw_repository_data",
     "feeding acceptance/consumption and sugar-aversion experiment data"),
    ("Blattella germanica", "10.5061/dryad.f82ns", "raw_repository_data",
     "nutrient consumption and sexual maturation/life-history data"),
    ("Blattella germanica", "10.5061/dryad.6q573n5zz", "raw_repository_data",
     "olfactory-learning behavioral data"),
    ("Blattella germanica", "10.5061/dryad.t76hdr81k", "raw_repository_data",
     "courtship event data plus life-history parameters including first-instar cohorts"),
]

ZENODO = [
    ("Oryzaephilus surinamensis", 4939311, "raw_repository_data",
     "cuticle thickness/melanization, hydrocarbons, water loss, mortality, population growth and qPCR"),
]

# Only data/video supplements with clear implementation value are selected.
PMC = [
    ("Blattella germanica", "PMC9385682",
     [r"mmc(?:2|3|4|5)\.xlsx$"],
     "raw_publisher_supplement",
     "male/female/nymph 15-min and adult 24-h shelter/thigmotaxis preference tables"),
    ("Blattella germanica", "PMC6393502",
     [r"\.xlsx$", r"\.mp4$"],
     "raw_publisher_supplement",
     "courtship/grooming behavioral dataset and original study video"),
    ("Blattella germanica", "PMC6804909",
     [r"\.mov$"],
     "study_video",
     "adult courtship locomotor/postural reference videos"),
    ("Oryzaephilus surinamensis", "PMC4641671",
     [r"\.xlsx$"],
     "raw_publisher_supplement",
     "individual knockdown and lethality-index observations"),
    ("Oryzaephilus surinamensis", "PMC8113238",
     [r"\.xlsx$"],
     "raw_publisher_supplement",
     "quantitative source data for cuticle/symbiont study"),
    ("Oryzaephilus surinamensis", "PMC13043703",
     [r"\.xlsx$"],
     "raw_publisher_supplement",
     "source data for survival/fitness/symbiont-replacement study"),
    ("Chelifer cancroides", "PMC8778599",
     [r"\.zip$"],
     "raw_publisher_supplement",
     "raw aphid/cytotoxic venom assay data"),
    ("Liposcelis bostrychophila", "PMC11737068",
     [r"\.xlsx$"],
     "raw_publisher_supplement",
     "developmental-stage olfactory-gene tables"),
    ("Liposcelis bostrychophila", "PMC3835895",
     [r"\.xls$"],
     "raw_publisher_supplement",
     "transcriptome annotation/expression validation tables"),
    ("Liposcelis bostrychophila", "PMC12028104",
     [r"\.zip$"],
     "raw_publisher_supplement",
     "Rickettsia abundance and fitness supplementary data"),
]

EDMOND = [
    ("Oryzaephilus surinamensis", "10.17617/3.5l", "raw_repository_data",
     "raw quantitative cuticle measurements from glyphosate/symbiont study"),
    ("Oryzaephilus surinamensis", "10.17617/3.5s", "raw_repository_data",
     "age-series cuticle/predation/fungal-defense dataset"),
]

MENDELEY = [
    ("Liposcelis bostrychophila", "5zdf96kr72", 1, "raw_repository_data",
     "acute/sublethal toxicity, reproduction and hatchability data; CC BY 4.0"),
]

DIRECT_FILES = [
    ("Oryzaephilus surinamensis", "adult",
     "USDA-ARS contact-insecticide metal-surface raw datasheet",
     "10.15482/USDA.ADC/25013951.v1", "Ag Data Commons / USDA-ARS",
     "raw_repository_data", "direct target species within a six-species raw datasheet",
     "CC0 / U.S. public domain", "https://ndownloader.figshare.com/files/57974548",
     "Datasheets_-_Metal.xlsx",
     "individual arena observations at 1, 3 and 7 d: live, affected/uncoordinated/twitching/unable-to-walk, dead; surface/treatment context",
     "adult impairment/movement-state response and surface-context evidence"),
    ("Oryzaephilus surinamensis", "adult",
     "USDA-ARS contact-insecticide concrete-surface raw datasheet",
     "10.15482/USDA.ADC/25013951.v1", "Ag Data Commons / USDA-ARS",
     "raw_repository_data", "direct target species within a six-species raw datasheet",
     "CC0 / U.S. public domain", "https://ndownloader.figshare.com/files/57974551",
     "Datasheets_-_Concrete.xlsx",
     "individual arena observations at 1, 3 and 7 d: live, affected/uncoordinated/twitching/unable-to-walk, dead; surface/treatment context",
     "adult impairment/movement-state response and surface-context evidence"),
]

REMOTE_ONLY = [
    {
        "species":"Chelifer cancroides","stage":"adult","study":"Locomotion in the pseudoscorpion Chelifer cancroides",
        "doi":"10.1242/jeb.243930","url":"https://static-movie-usa.glencoesoftware.com/source/10.1242/292/c5d450faf59d0a1a6f3c8fff7403b5c64591dee2/JEB243930.MovieS2.avi",
        "kind":"study_video","reason":"publisher copyright/reuse not established for redistribution",
        "use":"direct 500-fps forward/backward/upside-down gait validation"
    },
    {
        "species":"Chelifer cancroides","stage":"adult","study":"Locomotion in the pseudoscorpion Chelifer cancroides",
        "doi":"10.1242/jeb.243930","url":"https://doi.org/10.1242/jeb.243930",
        "kind":"supplementary_tables","reason":"Tables S1/S2 contain EthoVision detail but redistribution license not established",
        "use":"direct 1-hour locomotion and high-speed microstop measurements"
    },
    {
        "species":"Chelifer cancroides","stage":"adult + nymph cohorts","study":"Predation behaviour against Varroa destructor with Liposcelis entomophila alternative prey",
        "doi":"10.1080/00218839.2025.2582286","url":"https://doi.org/10.1080/00218839.2025.2582286",
        "kind":"predation_raw_observations","reason":"article is accessible for published results but no openly redistributable raw trial file was located",
        "use":"prey choice, cohort-specific predation and repeat-attack context"
    },
    {
        "species":"Liposcelis bostrychophila","stage":"adult female","study":"Acute lethal and behavioral sublethal responses of two stored-product psocids to surface insecticides",
        "doi":"10.1002/ps.1634","url":"https://doi.org/10.1002/ps.1634",
        "kind":"movement_raw_tracks","reason":"study reports tracking metrics but raw trajectories were not located in an open repository",
        "use":"movement speed/turn/path assay"
    },
    {
        "species":"Blattella germanica","stage":"~24 h first instar","study":"A model of animal movements in a bounded space",
        "doi":"10.1016/S0022-5193(03)00277-7","url":"https://doi.org/10.1016/S0022-5193(03)00277-7",
        "kind":"trajectory_raw_data","reason":"exact-stage published model/measurements exist but original track files were not released",
        "use":"core first-instar bounded-space locomotion"
    },
    {
        "species":"Oryzaephilus surinamensis","stage":"adult female","study":"Response to Food Odor Emanating Through Consumer Packaging Films",
        "doi":"10.1603/0046-225X-33.1.75","url":"https://doi.org/10.1603/0046-225X-33.1.75",
        "kind":"ethovision_raw_tracks","reason":"paper contains representative tracks and aggregate statistics; original EthoVision coordinates not publicly archived",
        "use":"adult speed, distance, sinuosity, localized search and edge behavior"
    },
]

def sha256(path: Path) -> str:
    h=hashlib.sha256()
    with path.open("rb") as f:
        for b in iter(lambda:f.read(MIB), b""): h.update(b)
    return h.hexdigest()

def safe_name(name: str) -> str:
    name=re.sub(r"[^A-Za-z0-9._+-]+","_",name).strip("._")
    return name or "file"

def get(url, **kw):
    last=None
    for i in range(2):
        try:
            r=S.get(url,timeout=kw.pop("timeout",(10,25)),allow_redirects=True,**kw)
            r.raise_for_status()
            return r
        except Exception as e:
            last=e; time.sleep(1+i)
    raise RuntimeError(f"GET failed {url}: {last}")

def get_json(url):
    return get(url).json()

def download(url: str, dest: Path):
    dest.parent.mkdir(parents=True,exist_ok=True)
    with get(url,stream=True) as r:
        ct=(r.headers.get("Content-Type") or "").lower()
        with dest.open("wb") as f:
            for chunk in r.iter_content(MIB):
                if chunk: f.write(chunk)
    head=dest.read_bytes()[:512].lower()
    if b"<html" in head or b"<!doctype html" in head:
        dest.unlink(missing_ok=True)
        raise ValueError(f"HTML/error page downloaded instead of data: {url}")
    if dest.stat().st_size < 16:
        raise ValueError(f"tiny/invalid data file: {url}")
    return ct

def manifest_row(rows, species, stage, study, doi, repo, data_class, directness, license_, path, source_url, contents, intended):
    path=Path(path)
    if not path.exists():
        raise FileNotFoundError(f"empirical manifest target does not exist: {path}")
    parts=path.parts
    if "02_EMPIRICAL_DATA" in parts:
        i=parts.index("02_EMPIRICAL_DATA")
        local_path=str(Path(*parts[i:]))
    else:
        local_path=str(path)
    rows.append({
        "species":species,"life_stage_or_form":stage,"study":study,"doi":doi,"repository_or_publisher":repo,
        "data_class":data_class,"directness":directness,"license_or_reuse":license_,"local_path":local_path,
        "bytes":path.stat().st_size,"sha256":sha256(path),"source_url":source_url,
        "variables_or_contents":contents,"intended_implementation_use":intended
    })

def curate_images(root: Path):
    src=root/"02_REFERENCE_DATA"
    dst=root/"03_REFERENCE_MEDIA"
    dst.mkdir(exist_ok=True)
    kept_all=[]; dropped_all=[]; summary=[]
    for slug,species in SPECIES.items():
        base=src/slug
        man=base/"05_REFERENCE_MEDIA"/"MANIFEST.csv"
        if not man.exists(): raise RuntimeError(f"missing manifest {man}")
        with man.open(newline="",encoding="utf-8-sig") as f: rows=list(csv.DictReader(f))
        target=max(1,round(len(rows)*TARGET_KEEP_FRACTION))
        target_words=["adult"] if slug!="blattella_germanica" else ["first","nymph","instar","juvenile"]
        def quality(r):
            stage=(r.get("life_stage_or_form") or "").lower()
            score=0.0
            if any(w in stage for w in target_words): score+=8
            if stage and "unlabeled" not in stage and "unknown" not in stage: score+=4
            if r.get("creator"): score+=1
            if r.get("source_reference"): score+=1
            if r.get("event_date"): score+=0.5
            try:
                area=max(1,int(r.get("width_px") or 0)*int(r.get("height_px") or 0))
                score+=min(4.0,math.log10(area)/2.0)
            except: pass
            return score
        strata=defaultdict(list)
        for r in rows:
            aspect="unknown"
            try:
                w,h=int(r["width_px"]),int(r["height_px"])
                ratio=w/max(1,h)
                aspect="wide" if ratio>1.35 else "tall" if ratio<0.74 else "square"
            except: pass
            key=(r.get("dataset_key") or "none", r.get("life_stage_or_form") or "unlabeled", aspect)
            strata[key].append(r)
        for arr in strata.values(): arr.sort(key=lambda r:(quality(r),int(r.get("bytes") or 0)),reverse=True)
        selected=[]
        keys=sorted(strata, key=lambda k:max(quality(r) for r in strata[k]), reverse=True)
        while len(selected)<target:
            progress=False
            for k in keys:
                if strata[k] and len(selected)<target:
                    selected.append(strata[k].pop(0)); progress=True
            if not progress: break
        # Keep the corpus visually strong without letting a handful of enormous
        # files preserve most of R8.2's image byte weight. Maintain the selected
        # file count while swapping oversized references for the best smaller
        # unselected alternatives until retained visual bytes are <=65%.
        original_bytes=sum(int(r.get("bytes") or 0) for r in rows)
        selected_ids={r["sha256"] for r in selected}
        selected_bytes=sum(int(r.get("bytes") or 0) for r in selected)
        if original_bytes and selected_bytes/original_bytes > 0.65:
            remaining=[r for r in rows if r["sha256"] not in selected_ids]
            remaining.sort(key=lambda r:(-quality(r), int(r.get("bytes") or 0)))
            # Largest selected items are candidates for replacement first.
            for oldr in sorted(list(selected), key=lambda r:int(r.get("bytes") or 0), reverse=True):
                if selected_bytes/original_bytes <= 0.65: break
                oldb=int(oldr.get("bytes") or 0)
                # Prefer a high-quality replacement at least 35% smaller.
                candidates=[
                    r for r in remaining
                    if int(r.get("bytes") or 0) < oldb*0.65 and quality(r) >= quality(oldr)-3.0
                ]
                if not candidates:
                    candidates=[r for r in remaining if int(r.get("bytes") or 0) < oldb*0.5]
                if not candidates: continue
                newr=max(candidates, key=lambda r:(quality(r), -int(r.get("bytes") or 0)))
                selected.remove(oldr); selected.append(newr)
                remaining.remove(newr); remaining.append(oldr)
                selected_bytes += int(newr.get("bytes") or 0)-oldb
            selected_ids={r["sha256"] for r in selected}
        selected_sha=selected_ids
        outdir=dst/slug
        imgout=outdir/"images"
        imgout.mkdir(parents=True,exist_ok=True)
        for r in rows:
            old=base/r["local_path"]
            if r["sha256"] in selected_sha:
                new=imgout/old.name
                shutil.move(str(old),str(new))
                rr=dict(r); rr["local_path"]=str(new.relative_to(root)); rr["curation_status"]="kept"
                kept_all.append(rr)
            else:
                rr=dict(r); rr["curation_status"]="dropped_from_bundle"
                rr["removal_reason"]="R8.3 quality/diversity curation; recoverable from source_url/download_url and original SHA-256"
                dropped_all.append(rr)
        # Preserve R8.2 source/provenance documents, but label old count/readme
        # files as historical so they cannot be mistaken for the current curated corpus.
        p=base/"02_SOURCES"
        if p.exists(): shutil.copytree(p,outdir/"02_SOURCES",dirs_exist_ok=True)
        p=base/"ACQUISITION_SUMMARY.json"
        if p.exists(): shutil.copy2(p,outdir/"ORIGINAL_R8_2_ACQUISITION_SUMMARY.json")
        p=base/"README.md"
        if p.exists(): shutil.copy2(p,outdir/"ORIGINAL_R8_2_ACQUISITION_README.md")
        kept_bytes=sum(int(r.get("bytes") or 0) for r in selected)
        original_bytes=sum(int(r.get("bytes") or 0) for r in rows)
        summary.append({"species":species,"original_files":len(rows),"kept_files":len(selected),"dropped_files":len(rows)-len(selected),
                        "keep_fraction":len(selected)/len(rows),"original_bytes":original_bytes,
                        "kept_bytes":kept_bytes,"byte_keep_fraction":(kept_bytes/original_bytes if original_bytes else 0.0)})
    shutil.rmtree(src)
    fields=sorted(set().union(*(r.keys() for r in kept_all+dropped_all)))
    for name,data in [("KEPT_REFERENCE_MEDIA.csv",kept_all),("DROPPED_REFERENCE_MEDIA.csv",dropped_all)]:
        with (dst/name).open("w",newline="",encoding="utf-8") as f:
            w=csv.DictWriter(f,fieldnames=fields); w.writeheader(); w.writerows(data)
    (dst/"CURATION_SUMMARY.json").write_text(json.dumps(summary,indent=2)+"\n",encoding="utf-8")
    return summary

def dryad_download(species, doi, data_class, use, outroot, rows, failures):
    try:
        dsurl="https://datadryad.org/api/v2/datasets/"+quote("doi:"+doi,safe="")
        ds=get_json(dsurl)
        version_href=((ds.get("_links") or {}).get("stash:version") or {}).get("href")
        if not version_href:
            # API sometimes expects DOI without an extra doi: prefix.
            dsurl="https://datadryad.org/api/v2/datasets/"+quote(doi,safe="")
            ds=get_json(dsurl)
            version_href=((ds.get("_links") or {}).get("stash:version") or {}).get("href")
        if version_href.startswith("/"):
            version_href="https://datadryad.org"+version_href
        v=get_json(version_href)
        files_href=((v.get("_links") or {}).get("stash:files") or {}).get("href")
        if files_href and files_href.startswith("/"):
            files_href="https://datadryad.org"+files_href
        fl=get_json(files_href)
        filelist=fl.get("_embedded",{}).get("stash:files") or fl.get("data") or []
        lic=ds.get("license") or v.get("license") or "Dryad repository dataset; verify metadata"
        ddir=outroot/safe_name(species)/"raw_repository"/safe_name(doi)
        for item in filelist:
            links=item.get("_links") or {}
            url=(links.get("stash:download") or {}).get("href") or item.get("download")
            name=item.get("path") or item.get("fileName") or item.get("name") or f"dryad_{item.get('id','file')}"
            if not url: continue
            if url.startswith("/"):
                url="https://datadryad.org"+url
            p=ddir/safe_name(Path(name).name)
            download(url,p)
            manifest_row(rows,species,"study-specific",f"Dryad dataset {doi}",doi,"Dryad",data_class,
                         "direct target species; stage/form as documented inside dataset",str(lic),p,
                         url,use,use)
    except Exception as e:
        failures.append({"source":"Dryad","species":species,"id":doi,"error":str(e)})

def zenodo_download(species, record, data_class, use, outroot, rows, failures):
    try:
        rec=get_json(f"https://zenodo.org/api/records/{record}")
        lic=((rec.get("metadata") or {}).get("license") or {}).get("id") or str((rec.get("metadata") or {}).get("license") or "")
        doi=(rec.get("metadata") or {}).get("doi") or str(record)
        ddir=outroot/safe_name(species)/"raw_repository"/f"zenodo_{record}"
        for item in rec.get("files") or []:
            url=(item.get("links") or {}).get("content") or (item.get("links") or {}).get("self")
            if not url: continue
            p=ddir/safe_name(item.get("key") or item.get("name") or "file")
            download(url,p)
            manifest_row(rows,species,"mixed exact-species stages",f"Zenodo record {record}",doi,"Zenodo",data_class,
                         "direct target species; stage/form documented per sheet",lic,p,url,use,use)
    except Exception as e:
        failures.append({"source":"Zenodo","species":species,"id":record,"error":str(e)})

def pmc_download(species, pmcid, patterns, data_class, use, outroot, rows, failures):
    try:
        xml=get(f"https://www.ncbi.nlm.nih.gov/pmc/utils/oa/oa.fcgi?id={pmcid}").text
        root=ET.fromstring(xml)
        record=root.find(".//record")
        if record is None: raise RuntimeError("PMC OA record not found")
        license_=record.attrib.get("license","PMC Open Access; inspect article license")
        href=None
        for link in record.findall(".//link"):
            if link.attrib.get("format") in ("tgz","tar.gz"):
                href=link.attrib.get("href"); break
        if not href: raise RuntimeError("OA package tgz link missing")
        if href.startswith("ftp://"): href="https://"+href[len("ftp://"):]
        with tempfile.TemporaryDirectory() as td:
            tgz=Path(td)/"pkg.tgz"; download(href,tgz)
            extract=Path(td)/"x"; extract.mkdir()
            with tarfile.open(tgz,"r:gz") as tf: tf.extractall(extract)
            matches=[]
            for p in extract.rglob("*"):
                if p.is_file() and any(re.search(pat,p.name,re.I) for pat in patterns):
                    matches.append(p)
            if not matches: raise RuntimeError(f"no files matched {patterns}")
            ddir=outroot/safe_name(species)/"publisher_supplement"/pmcid
            for src in matches:
                dst=ddir/safe_name(src.name); dst.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(src,dst)
                manifest_row(rows,species,"study-specific",f"PMC Open Access article {pmcid}",pmcid,"PubMed Central",data_class,
                             "direct target species; exact stage/form varies by file",license_,dst,
                             href,use,use)
    except Exception as e:
        failures.append({"source":"PMC","species":species,"id":pmcid,"error":str(e)})

def edmond_download(species, doi, data_class, use, outroot, rows, failures):
    try:
        api="https://edmond.mpg.de/api/datasets/:persistentId/?persistentId="+quote("doi:"+doi,safe="")
        j=get_json(api)
        data=j.get("data") or {}
        ver=data.get("latestVersion") or {}
        files=ver.get("files") or []
        lic=ver.get("license") or data.get("license") or "Edmond open dataset; verify record license"
        ddir=outroot/safe_name(species)/"raw_repository"/safe_name(doi)
        for ent in files:
            df=ent.get("dataFile") or {}
            fid=df.get("id"); name=df.get("filename") or f"file_{fid}"
            if not fid: continue
            url=f"https://edmond.mpg.de/api/access/datafile/{fid}"
            p=ddir/safe_name(name); download(url,p)
            manifest_row(rows,species,"study-specific",f"Edmond dataset {doi}",doi,"Edmond / Max Planck Society",
                         data_class,"direct target species; stage/form documented per file",str(lic),
                         p,url,use,use)
    except Exception as e:
        failures.append({"source":"Edmond","species":species,"id":doi,"error":str(e)})

def mendeley_download(species, dsid, version, data_class, use, outroot, rows, failures):
    try:
        urls=[
            f"https://api.mendeley.com/datasets/{dsid}/versions/{version}/files",
            f"https://data.mendeley.com/public-api/datasets/{dsid}/versions/{version}/files",
        ]
        files=None
        for u in urls:
            try:
                j=get_json(u)
                if isinstance(j,list) and j: files=j; break
                if isinstance(j,dict) and (j.get("files") or j.get("data")): files=j.get("files") or j.get("data"); break
            except Exception: pass
        if not files:
            html=get(f"https://data.mendeley.com/datasets/{dsid}/{version}").text
            found=sorted(set(re.findall(r'https://data\\.mendeley\\.com/public-files/datasets/[^"\\\\ ]+',html)))
            files=[{"name":Path(urlparse(x).path).name or "mendeley_file","download_url":x} for x in found]
        if not files: raise RuntimeError("Mendeley file discovery failed")
        ddir=outroot/safe_name(species)/"raw_repository"/f"mendeley_{dsid}_v{version}"
        for item in files:
            url=item.get("download_url") or item.get("downloadUrl") or item.get("url")
            if isinstance(url,dict): url=url.get("download") or url.get("href")
            if not url: continue
            name=item.get("filename") or item.get("name") or Path(urlparse(url).path).name or "file"
            p=ddir/safe_name(name); download(url,p)
            manifest_row(rows,species,"study-specific",f"Mendeley Data {dsid} v{version}",f"10.17632/{dsid}.{version}",
                         "Mendeley Data",data_class,"direct target species","CC BY 4.0",
                         p,url,use,use)
    except Exception as e:
        failures.append({"source":"Mendeley","species":species,"id":dsid,"error":str(e)})

def direct_files_download(outroot, rows, failures):
    for species,stage,study,doi,repo,data_class,directness,license_,url,name,contents,intended in DIRECT_FILES:
        try:
            p=outroot/safe_name(species)/"raw_repository"/safe_name(doi)/safe_name(name)
            download(url,p)
            manifest_row(rows,species,stage,study,doi,repo,data_class,directness,license_,p,url,contents,intended)
        except Exception as e:
            failures.append({"source":repo,"species":species,"id":doi,"url":url,"error":str(e)})

def write_published_aggregates(outroot: Path, rows):
    agg=outroot/"published_aggregate_extracts"; agg.mkdir(parents=True,exist_ok=True)
    files={
      "chelifer_cancroides_jeb2022_locomotion.csv":(
        "Chelifer cancroides","adult","10.1242/jeb.243930","Journal of Experimental Biology",
        """metric,value,uncertainty_or_range,unit,context
body_length,3.09,0.47 SD; 2.13-3.84,mm,n=10 adults
pedipalp_length,3.88,0.72 SD; 2.98-5.45,mm,n=10 adults
one_hour_distance,14,approximately,m/hour,arena activity
mobile_time,61,,percent,1 h EthoVision arena
immobile_time,39,,percent,1 h EthoVision arena
microstop_duration,0.16,0.04 SD,s,high-speed forward walking
backward_peak_speed,17,up to,body_lengths_per_s,escape
upside_down_peak_speed,4,up to,body_lengths_per_s,upside-down walking
high_speed_sampling,500,,frames_per_s,footfall analysis
"""
      ),
      "chelifer_cancroides_vantoor2026_predation_published.csv":(
        "Chelifer cancroides","adult + proto/deuto/tritonymph cohorts","10.1080/00218839.2025.2582286","Journal of Apicultural Research",
        """metric,value,unit,context
varroa_presented_per_cohort,15,individuals,cohort predation arenas
varroa_killed_min,25,percent,across Chelifer cohorts within 4 h
varroa_killed_max,80,percent,across Chelifer cohorts within 4 h
observation_window,4,hours,cohort predation assay
adult_female_choice,varroa_and_psocid_equal,qualitative,published result
adult_male_choice,psocid_preferred,qualitative,published result
protonymph_choice,psocid_preferred,qualitative,published result
deutonymph_choice,psocid_preferred,qualitative,published result
tritonymph_choice,psocid_preferred,qualitative,published result
alternative_psocid_species,Liposcelis_entomophila,taxon,not L. bostrychophila
"""
      ),
      "oryzaephilus_surinamensis_mowery2004_movement.csv":(
        "Oryzaephilus surinamensis","mated adult female","10.1603/0046-225X-33.1.75","Environmental Entomology",
        """surface,n,duration_s,distance_cm_mean,distance_cm_se,velocity_cm_s_mean,velocity_cm_s_se
Cello,76,300,104.4,3.9,0.349,0.013
120_AB-X,76,300,81.9,3.4,0.274,0.011
"""
      ),
      "oryzaephilus_surinamensis_kavallieratos2024_mobility_table3.csv":(
        "Oryzaephilus surinamensis","adult","10.1002/ps.8262","Pest Management Science",
        """treatment,n,duration_s,walking_s_mean,walking_s_se,stops_n_mean,stops_n_se,stops_s_mean,stops_s_se,climbing_n_mean,climbing_n_se,climbing_s_mean,climbing_s_se,upturned_n_mean,upturned_n_se,upturned_s_mean,upturned_s_se
Control,30,900,532.3,38.9,6.5,1.2,145.7,29.7,10.8,8.6,209.4,29.4,0.9,0.29,0.1,0.1
Etofenprox_LC10,30,900,238.5,31.9,8.5,1.2,375.3,56.5,12.4,1.3,277.5,32.7,1.3,0.4,7.4,6.8
Etofenprox_LC30,30,900,217.1,31.3,8.0,1.5,414.4,51.0,7.6,1.2,212.0,28.4,1.1,0.3,55.3,28.0
Lambda_cyhalothrin_LC10,30,900,135.1,38.8,24.1,16.8,374.2,65.0,6.9,2.2,120.6,33.1,1.2,0.4,250.2,65.8
Lambda_cyhalothrin_LC30,30,900,90.3,26.7,3.9,1.1,613.1,63.1,2.8,0.8,67.4,26.4,1.1,0.3,139.2,48.1
Deltamethrin_LC10,30,900,148.9,32.0,3.3,0.6,541.1,66.64,4.5,1.1,150.0,36.2,0.7,0.2,49.7,31.8
Deltamethrin_LC30,30,900,84.3,19.6,33.9,29.9,529.0,64.0,3.3,1.0,93.2,29.4,0.5,0.1,163.4,56.6
Alpha_cypermethrin_LC10,30,900,242.3,28.6,3.5,0.4,355.4,40.7,8.9,1.2,257.9,31.9,0.2,0.1,7.4,7.1
Alpha_cypermethrin_LC30,30,900,175.7,29.0,3.1,0.4,360.0,53.1,7.1,0.7,357.2,47.4,0.4,0.2,0.9,0.4
PAD_LC10,30,900,269.4,42.4,2.9,0.5,327.9,57.9,5.8,0.8,245.6,43.0,0.6,0.2,38.9,30.5
PAD_LC30,30,900,306.8,36.6,2.9,0.4,353.6,52.7,8.4,1.3,224.9,28.1,0.3,0.1,1.5,1.3
"""
      ),
      "liposcelis_bostrychophila_guedes2008_movement.csv":(
        "Liposcelis bostrychophila","adult female","10.1002/ps.1634","Pest Management Science",
        """metric,mean,se,unit,context
movement_speed,0.36,0.02,cm/s,10 min concrete arena; insecticide-response study
pyrethrin_half_arena_time_unsprayed,57.48,4.55,percent,half-sprayed arena
pyrethrin_half_arena_time_sprayed,41.58,4.325,percent,half-sprayed arena
"""
      ),
      "liposcelis_bostrychophila_wang2000_life_history.csv":(
        "Liposcelis bostrychophila","egg-to-adult/adult","10.1603/0013-8746(2000)093[0261:DAROTP]2.0.CO;2","Annals of the Entomological Society of America",
        """metric,value,unit,context
egg_to_adult_at_20C,41.9,days,constant 20 C
egg_to_adult_at_32_5C,18.1,days,constant 32.5 C
egg_to_adult_survival_at_27_5C,82.9,percent,constant 27.5 C
eggs_per_female_at_27_5C,74.7,eggs,highest reported treatment
eggs_per_female_at_20C,51.9,eggs,20 C
intrinsic_rate_increase_at_30C,0.0946,per_day,30 C
doubling_time_at_30C,7.3,days,30 C
mean_generation_time_at_30C,43.2,days,30 C
"""
      ),
      "blattella_germanica_jeanson2003_first_instar.csv":(
        "Blattella germanica","~24 h first instar","10.1016/S0022-5193(03)00277-7","Journal of Theoretical Biology",
        """parameter,value,unit
body_length,3.0,mm
body_width,2.0,mm
antenna_length,3.0,mm
central_speed,11.0,mm/s
peripheral_speed,10.6,mm/s
central_stop_hazard,0.03,s^-1
peripheral_stop_hazard,0.08,s^-1
peripheral_exit_hazard,0.12,s^-1
transport_mean_free_path,23.2,mm
departure_angle_lognormal_geometric_mean,36.6,degrees
departure_angle_geometric_sd,2.14,ratio
short_stop_probability,0.93,probability
short_stop_mean,5.87,s
long_stop_mean,700,s
peripheral_threshold,5,mm
observed_sampling_interval,0.68,s
paper_model_dt,0.2,s
"""
      ),
    }
    for fname,(species,stage,doi,publisher,text) in files.items():
        p=agg/fname; p.write_text(text,encoding="utf-8")
        manifest_row(rows,species,stage,f"Published aggregate extract {doi}",doi,publisher,
                     "published_aggregate_extract","direct target species/stage as stated; aggregate not raw observations",
                     "factual numerical extract; source publication terms apply",p,
                     "https://doi.org/"+doi,"published numerical values transcribed into machine-readable CSV",
                     "calibration/gating where raw observations were not publicly released")

def write_remote(outroot: Path):
    p=outroot/"REMOTE_ONLY_RESTRICTED.csv"
    fields=["species","stage","study","doi","url","kind","reason","use"]
    with p.open("w",newline="",encoding="utf-8") as f:
        w=csv.DictWriter(f,fieldnames=fields); w.writeheader(); w.writerows(REMOTE_ONLY)

def main(root: Path):
    summary=curate_images(root)
    empirical=root/"02_EMPIRICAL_DATA"; empirical.mkdir(exist_ok=True)
    rows=[]; failures=[]
    for x in DRYAD: dryad_download(*x,empirical,rows,failures)
    for x in ZENODO: zenodo_download(*x,empirical,rows,failures)
    for x in PMC: pmc_download(*x,empirical,rows,failures)
    for x in EDMOND: edmond_download(*x,empirical,rows,failures)
    for x in MENDELEY: mendeley_download(*x,empirical,rows,failures)
    direct_files_download(empirical,rows,failures)
    write_published_aggregates(empirical,rows)
    write_remote(empirical)
    fields=["species","life_stage_or_form","study","doi","repository_or_publisher","data_class","directness",
            "license_or_reuse","local_path","bytes","sha256","source_url","variables_or_contents","intended_implementation_use"]
    with (empirical/"EMPIRICAL_DATA_MANIFEST.csv").open("w",newline="",encoding="utf-8") as f:
        w=csv.DictWriter(f,fieldnames=fields); w.writeheader(); w.writerows(rows)
    (empirical/"ACQUISITION_FAILURES.json").write_text(json.dumps(failures,indent=2)+"\n",encoding="utf-8")
    classes=defaultdict(lambda:{"files":0,"bytes":0})
    for r in rows:
        classes[r["data_class"]]["files"]+=1; classes[r["data_class"]]["bytes"]+=int(r["bytes"])
    report={
        "visual_curation":summary,
        "empirical_files":len(rows),
        "empirical_bytes":sum(int(r["bytes"]) for r in rows),
        "by_data_class":classes,
        "acquisition_failures":failures,
    }
    (empirical/"EMPIRICAL_ACQUISITION_SUMMARY.json").write_text(json.dumps(report,indent=2,default=dict)+"\n",encoding="utf-8")
    print(json.dumps(report,indent=2,default=dict))

if __name__=="__main__":
    import sys
    if len(sys.argv)!=2: raise SystemExit("usage: build_empirical_bundle.py <R8.2-unpacked-root>")
    main(Path(sys.argv[1]).resolve())
