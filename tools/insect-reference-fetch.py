from __future__ import annotations

import csv
import hashlib
import json
import subprocess
import time
from pathlib import Path
from urllib.parse import quote, urljoin

import requests
from bs4 import BeautifulSoup

ROOT = Path("reference-images")
DIRS = [
    "ants/linepithema_humile/worker",
    "ants/linepithema_humile/queen",
    "ants/linepithema_humile/male",
    "ants/linepithema_humile/brood",
    "drosophila_melanogaster/adult",
    "drosophila_melanogaster/larva",
    "drosophila_melanogaster/pupa",
    "blattella_germanica/adult",
    "blattella_germanica/nymph",
    "cimex_lectularius/adult",
    "cimex_lectularius/nymph",
    "tribolium_castaneum/adult",
    "tribolium_castaneum/pupa",
    "source-pdfs",
]
for d in DIRS:
    (ROOT / d).mkdir(parents=True, exist_ok=True)

session = requests.Session()
session.headers.update({"User-Agent": "InsectRealism-reference-fetch/2.0"})
rows = []
warnings = []

def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def download(url: str, path: Path, min_bytes: int = 1000) -> None:
    last = None
    for attempt in range(6):
        r = session.get(url, timeout=120, allow_redirects=True)
        last = r
        if r.status_code == 429:
            time.sleep(4 + attempt * 4)
            continue
        r.raise_for_status()
        path.write_bytes(r.content)
        if path.stat().st_size < min_bytes:
            raise RuntimeError(f"{path} too small: {path.stat().st_size} bytes")
        return
    if last is not None:
        last.raise_for_status()
    raise RuntimeError("download retry budget exhausted")

def add(path: Path, species: str, stage: str, view: str, source_page: str,
        author: str, license_name: str, license_url: str,
        intended_use: str, authority: str, notes: str = "") -> None:
    rows.append({
        "local_file": path.relative_to(ROOT).as_posix(),
        "species": species,
        "stage_or_form": stage,
        "view": view,
        "source_url": source_page,
        "author_credit": author,
        "license": license_name,
        "license_url": license_url,
        "intended_use": intended_use,
        "geometry_authority": authority,
        "bytes": path.stat().st_size,
        "sha256": digest(path),
        "notes": notes,
    })

def commons(filename: str, out: str, species: str, stage: str, view: str,
            author: str, license_name: str, license_url: str,
            intended_use: str, authority: str) -> None:
    source = "https://commons.wikimedia.org/wiki/File:" + quote(filename.replace(" ", "_"))
    path = ROOT / out
    try:
        api = "https://commons.wikimedia.org/w/api.php"
        params = {
            "action": "query",
            "format": "json",
            "prop": "imageinfo",
            "iiprop": "url",
            "iiurlwidth": "500",
            "titles": "File:" + filename,
        }
        response = None
        for attempt in range(6):
            response = session.get(api, params=params, timeout=60)
            if response.status_code == 429:
                time.sleep(4 + attempt * 4)
                continue
            response.raise_for_status()
            break
        if response is None:
            raise RuntimeError("Commons API returned no response")
        response.raise_for_status()
        data = response.json()
        page = next(iter(data["query"]["pages"].values()))
        info = page["imageinfo"][0]
        direct = info.get("thumburl") or info["url"]
        download(direct, path)
        add(path, species, stage, view, source, author, license_name, license_url,
            intended_use, authority,
            "R3 audited measurements remain authoritative for physical scale.")
        time.sleep(1.0)
    except Exception as e:
        warnings.append(f"Commons {filename}: {e}")

COMMONS = [
    ("Linepithema humile casent0005323 dorsal 1.jpg",
     "ants/linepithema_humile/worker/worker_CASENT0005323_dorsal.jpg",
     "Linepithema humile", "mature worker", "dorsal",
     "AntWeb photographer / www.AntWeb.org", "CC BY 4.0",
     "https://creativecommons.org/licenses/by/4.0/", "geometry", "HIGH"),
    ("Linepithema humile casent0005323 profile 1.jpg",
     "ants/linepithema_humile/worker/worker_CASENT0005323_profile.jpg",
     "Linepithema humile", "mature worker", "profile",
     "AntWeb photographer / www.AntWeb.org", "CC BY 4.0",
     "https://creativecommons.org/licenses/by/4.0/", "geometry", "HIGH"),
    ("Linepithema humile casent0005323 head 1.jpg",
     "ants/linepithema_humile/worker/worker_CASENT0005323_head.jpg",
     "Linepithema humile", "mature worker", "head",
     "AntWeb photographer / www.AntWeb.org", "CC BY 4.0",
     "https://creativecommons.org/licenses/by/4.0/", "geometry", "HIGH"),
    ("Drosophila melanogaster (mosca de la fruta) vista lateral, bajo el microscopio electrónico de barrido.jpg",
     "drosophila_melanogaster/adult/adult_SEM_head_thorax.jpg",
     "Drosophila melanogaster", "adult", "SEM close-up",
     "Brandon Antonio Segura Torres & Priscilla Vieto Bonilla", "CC BY-SA 4.0",
     "https://creativecommons.org/licenses/by-sa/4.0/", "surface/head morphology", "MEDIUM"),
    ("Drosophila 2nd instar larva.jpg",
     "drosophila_melanogaster/larva/L2_reference.jpg",
     "Drosophila melanogaster", "larva L2", "lateral",
     "Wikimedia Commons contributor", "CC BY-SA 2.5",
     "https://creativecommons.org/licenses/by-sa/2.5/", "stage morphology/material", "MEDIUM"),
    ("Fruit Fly Pupa.jpg",
     "drosophila_melanogaster/pupa/pupa_reference.jpg",
     "Drosophila melanogaster", "pupa", "lateral",
     "Wikimedia Commons contributor", "CC BY-SA 4.0",
     "https://creativecommons.org/licenses/by-sa/4.0/", "stage morphology/material", "MEDIUM"),
    ("Drosophila melanogaster life cycle.jpg",
     "drosophila_melanogaster/life_cycle_context.jpg",
     "Drosophila melanogaster", "life cycle", "multi-stage",
     "Wikimedia Commons contributor", "CC BY 3.0",
     "https://creativecommons.org/licenses/by/3.0/", "life-cycle context only", "LOW"),
    ("Blatella germanica cdc.jpg",
     "blattella_germanica/adult/female_with_ootheca_CDC.jpg",
     "Blattella germanica", "adult female with ootheca", "dorsal",
     "CDC PHIL", "Public domain",
     "https://creativecommons.org/publicdomain/mark/1.0/", "reproductive morphology", "HIGH"),
    ("Blattella germanica 1236168.jpg",
     "blattella_germanica/adult/adult_reference.jpg",
     "Blattella germanica", "adult", "dorsal/oblique",
     "Clemson University / USDA Cooperative Extension Slide Series", "CC BY 3.0 US",
     "https://creativecommons.org/licenses/by/3.0/us/", "adult geometry/material", "MEDIUM"),
    ("Cimex lectularius.jpg",
     "cimex_lectularius/adult/adult_CDC_PD.jpg",
     "Cimex lectularius", "adult", "dorsal",
     "CDC / World Health Organization donation", "Public domain",
     "https://creativecommons.org/publicdomain/mark/1.0/", "adult geometry/material", "HIGH"),
    ("Red flour beetle Tribolium castaneum.png",
     "tribolium_castaneum/adult/adult_larva_comparison.png",
     "Tribolium castaneum", "adult/larva comparison", "comparison",
     "Wikimedia Commons contributor", "CC BY-SA 3.0",
     "https://creativecommons.org/licenses/by-sa/3.0/", "morphology/context", "MEDIUM"),
    ("201703 Red flour beetle pupa.svg",
     "tribolium_castaneum/pupa/pupa_reference.svg",
     "Tribolium castaneum", "pupa", "illustration",
     "Wikimedia Commons contributor", "CC BY 4.0",
     "https://creativecommons.org/licenses/by/4.0/", "pupa morphology context", "LOW"),
    ("Tribolium.castaneum.jpg",
     "tribolium_castaneum/adult/adult_reference.jpg",
     "Tribolium castaneum", "adult", "dorsal/oblique",
     "Wikimedia Commons contributor", "CC BY-SA 3.0",
     "https://creativecommons.org/licenses/by-sa/3.0/", "adult geometry/material", "MEDIUM"),
]
for args in COMMONS:
    commons(*args)

def cdc_phil(pid: int, out: str, species: str, stage: str, view: str,
             use: str, authority: str) -> None:
    page = f"https://wwwn.cdc.gov/phil/Details.aspx?pid={pid}"
    path = ROOT / out
    try:
        response = session.get(page, timeout=60)
        response.raise_for_status()
        soup = BeautifulSoup(response.text, "html.parser")
        candidates = []
        for tag in soup.find_all(["img", "source"]):
            raw = tag.get("src") or tag.get("data-src") or tag.get("srcset")
            if not raw:
                continue
            raw = raw.split(",")[0].strip().split(" ")[0]
            url = urljoin(page, raw)
            low = url.lower()
            if any(x in low for x in ["logo", "icon", "spinner", "sprite", "blank"]):
                continue
            try:
                img = session.get(url, timeout=60)
                ctype = img.headers.get("content-type", "")
                if img.ok and ctype.startswith("image/") and len(img.content) > 20000:
                    candidates.append((len(img.content), url, img.content))
            except Exception:
                pass
        if not candidates:
            raise RuntimeError("no usable displayed image found")
        candidates.sort(reverse=True)
        _, chosen, content = candidates[0]
        path.write_bytes(content)
        add(path, species, stage, view, page, "CDC", "Public domain",
            "https://creativecommons.org/publicdomain/mark/1.0/",
            use, authority,
            "PHIL detail page states Copyright Restrictions: None; local copy is the largest displayed source image.")
    except Exception as e:
        warnings.append(f"CDC PHIL {pid}: {e}")

CDC = [
    (12703, "cimex_lectularius/nymph/nymph_PHIL12703_PD.jpg",
     "Cimex lectularius", "nymph", "dorsal", "nymph geometry/material", "HIGH"),
    (12704, "cimex_lectularius/adult/adult_PHIL12704_PD.jpg",
     "Cimex lectularius", "adult", "dorsal", "adult geometry/material", "HIGH"),
    (12705, "cimex_lectularius/adult/head_thorax_PHIL12705_PD.jpg",
     "Cimex lectularius", "adult", "head/thorax close-up", "surface anatomy", "MEDIUM"),
    (15247, "cimex_lectularius/adult/male_female_PHIL15247_PD.jpg",
     "Cimex lectularius", "adult male + female", "dorsal comparison", "sexual dimorphism", "HIGH"),
    (26933, "blattella_germanica/nymph/nymph_stages_PHIL26933_PD.jpg",
     "Blattella germanica", "nymph stages", "dorsal comparison", "nymph morphology/context", "HIGH"),
    (26930, "blattella_germanica/life_cycle_PHIL26930_PD.jpg",
     "Blattella germanica", "life cycle", "illustration", "life-cycle context", "LOW"),
    (6324, "blattella_germanica/adult/male_female_PHIL6324_PD.jpg",
     "Blattella germanica", "adult male + female", "dorsal comparison", "sexual dimorphism", "HIGH"),
    (28479, "blattella_germanica/adult/adult_dorsal_PHIL28479_PD.jpg",
     "Blattella germanica", "adult", "dorsal", "adult geometry/material", "HIGH"),
]
for args in CDC:
    cdc_phil(*args)

def antweb_specimen(specimen: str, form: str, out_dir: str) -> None:
    page = f"https://www.antweb.org/specimen.do?code={specimen.lower()}"
    directory = ROOT / out_dir
    try:
        response = session.get(page, timeout=60)
        response.raise_for_status()
        soup = BeautifulSoup(response.text, "html.parser")
        candidates = []
        seen = set()
        for tag in soup.find_all("img"):
            raw = tag.get("src") or tag.get("data-src")
            if not raw:
                continue
            url = urljoin(page, raw)
            if url in seen:
                continue
            seen.add(url)
            low = url.lower()
            if any(x in low for x in ["logo", "icon", "banner", "loading", "map"]):
                continue
            try:
                img = session.get(url, timeout=60)
                ctype = img.headers.get("content-type", "")
                if img.ok and ctype.startswith("image/") and len(img.content) > 30000:
                    candidates.append((len(img.content), url, img.content))
            except Exception:
                pass
        candidates.sort(reverse=True)
        for i, (_, url, content) in enumerate(candidates[:3], 1):
            path = directory / f"{form}_{specimen}_{i}.jpg"
            path.write_bytes(content)
            add(path, "Linepithema humile", form, "AntWeb specimen image", page,
                "AntWeb photographer / specimen record", "CC BY 4.0",
                "https://creativecommons.org/licenses/by/4.0/",
                f"{form} geometry reference", "HIGH",
                "Exact target species/form; measured R3 dimensions remain physical-scale authority.")
        if not candidates:
            warnings.append(f"AntWeb {form} {specimen}: no downloadable specimen image discovered")
    except Exception as e:
        warnings.append(f"AntWeb {form} {specimen}: {e}")

antweb_specimen("CASENT0246288", "queen", "ants/linepithema_humile/queen")
antweb_specimen("CASENT0724858", "male", "ants/linepithema_humile/male")

paper_url = "https://www.scielo.cl/pdf/bres/v43n1/art04.pdf"
pdf = ROOT / "source-pdfs/solis_2010_linepithema_immatures.pdf"
try:
    download(paper_url, pdf, 100000)
    add(pdf, "Linepithema humile", "egg/larvae/pupae", "source paper PDF", paper_url,
        "Solis et al.", "CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/",
        "primary brood morphology source", "HIGH",
        "R3 corrected worker-vs-male larval labels must be used.")
    pages = {}
    for pnum in range(1, 25):
        try:
            text = subprocess.check_output(
                ["pdftotext", "-f", str(pnum), "-l", str(pnum), str(pdf), "-"],
                text=True, stderr=subprocess.DEVNULL)
        except Exception:
            break
        low = text.lower()
        if ("fig. 1" in low or "figure 1" in low) and 1 not in pages:
            pages[1] = pnum
        if ("fig. 8" in low or "figure 8" in low) and 8 not in pages:
            pages[8] = pnum
    for figno, stage, use in [
        (1, "larval stages", "larval geometry/stage comparison"),
        (8, "worker/male pupae", "pupal geometry/sex comparison"),
    ]:
        pnum = pages.get(figno)
        if not pnum:
            warnings.append(f"SciELO Figure {figno}: page not located")
            continue
        prefix = ROOT / f"ants/linepithema_humile/brood/solis2010_figure{figno}_page"
        subprocess.check_call([
            "pdftoppm", "-f", str(pnum), "-l", str(pnum), "-r", "220",
            "-png", "-singlefile", str(pdf), str(prefix)
        ])
        out = Path(str(prefix) + ".png")
        add(out, "Linepithema humile", stage, f"paper page with Figure {figno}",
            paper_url, "Solis et al.", "CC BY 4.0",
            "https://creativecommons.org/licenses/by/4.0/",
            use, "HIGH", "Whole page kept so labels/caption remain attached.")
except Exception as e:
    warnings.append(f"SciELO brood paper: {e}")

(ROOT / "LINK_ONLY_REFERENCES.md").write_text(
"""# Link-only / unresolved reference gaps

Do not replace these gaps with generic or generated biology.

- Argentine-ant queen: AntWeb specimen CASENT0246288
  https://www.antweb.org/specimen.do?name=casent0246288
- Argentine-ant male: AntWeb specimen CASENT0724858
  https://www.antweb.org/specimen.do?name=casent0724858
- Argentine-ant callow worker: exact target-species photographic color/timing reference remains a gap.
- German-cockroach first-instar locomotion is the calibrated stage. A later-nymph photograph is context only.
- Bed-bug instar 1-5 physical geometry remains governed by R3 measured morphometrics.
- Tribolium adult leg-phase gait remains a research gate; photographs do not close it.
""",
encoding="utf-8")

fields = [
    "local_file", "species", "stage_or_form", "view", "source_url",
    "author_credit", "license", "license_url", "intended_use",
    "geometry_authority", "bytes", "sha256", "notes",
]
with (ROOT / "MANIFEST.csv").open("w", newline="", encoding="utf-8") as f:
    writer = csv.DictWriter(f, fieldnames=fields)
    writer.writeheader()
    writer.writerows(rows)

(ROOT / "FETCH_REPORT.json").write_text(json.dumps({
    "downloaded_entries": len(rows),
    "warnings": warnings,
    "policy": {
        "scale_authority": "R3 audited measurements/data",
        "images": "geometry/material/context references only",
        "generated_images_are_scientific_authority": False,
    }
}, indent=2) + "\n", encoding="utf-8")

(ROOT / "README.md").write_text(
"""# Insect modeling reference images

Curated visual references for Codex/Astra.

Authority order:
1. Audited R3 measurements/provenance control physical scale and calibrated biology.
2. Exact-species/exact-stage specimen or scientific images guide geometry.
3. Natural photographs guide material, color, and posture.
4. Life-cycle illustrations/comparison plates are context only.
5. Generated imagery is never scientific authority.

Read MANIFEST.csv before using a file. Never average unrelated species, life stages,
sexes, castes, or feeding states into one model.
""",
encoding="utf-8")

with (ROOT / "SHA256SUMS.txt").open("w", encoding="utf-8") as f:
    for path in sorted(ROOT.rglob("*")):
        if path.is_file() and path.name != "SHA256SUMS.txt":
            f.write(f"{digest(path)}  {path.relative_to(ROOT).as_posix()}\n")

print(json.dumps({"downloaded_entries": len(rows), "warnings": warnings}, indent=2))
