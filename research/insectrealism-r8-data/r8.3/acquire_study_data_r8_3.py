#!/usr/bin/env python3
"""Acquire legally redistributable empirical study data for InsectRealism R8.3.

This intentionally prioritizes raw tables, source-data workbooks, experimental
recordings and stage/life-history data over more reference photography.

Publisher files with unclear or restrictive redistribution rights are NEVER
silently bundled; those are written to REMOTE_ONLY_HIGH_VALUE_DATA.csv.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import shutil
import tarfile
import tempfile
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import quote, urlparse

import requests

UA = "InsectRealism-R8.3 empirical-data-builder (+https://github.com/Pixelied/Agents)"
S = requests.Session()
S.headers.update({"User-Agent": UA, "Accept": "*/*"})

ALLOWED_LICENSE_PATTERNS = [
    r"creativecommons\.org/licenses/by/(?:2\.0|2\.5|3\.0|4\.0)",
    r"creativecommons\.org/licenses/by-sa/(?:2\.0|2\.5|3\.0|4\.0)",
    r"creativecommons\.org/publicdomain/(?:zero|mark)/1\.0",
    r"\bcc0\b",
    r"\bcc-zero\b",
    r"\bcc-by(?:-sa)?-(?:2(?:\.0)?|2\.5|3(?:\.0)?|4(?:\.0)?)\b",
    r"\bcc by(?:-sa)?(?: |-|/)?(?:2(?:\.0)?|2\.5|3(?:\.0)?|4(?:\.0)?)\b",
    r"public domain",
]
BLOCKED_LICENSE_PATTERNS = [
    r"by-nc", r"noncommercial", r"by-nd", r"no derivatives", r"all rights reserved"
]

FIELDS = [
    "species", "source_id", "title", "source_type", "repository", "doi_or_id",
    "license", "evidence_class", "life_stage_or_form", "intended_use",
    "baseline_calibration_permission", "original_url", "local_path", "bytes",
    "sha256", "source_reported_digest", "notes"
]


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def slug_species(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def sanitize(name: str) -> str:
    name = Path(name).name
    name = re.sub(r"[^A-Za-z0-9._()+ -]+", "_", name)
    return name[:220] or "data.bin"


def get(url: str, **kwargs):
    last = None
    for attempt in range(5):
        try:
            r = S.get(url, timeout=(20, 180), allow_redirects=True, **kwargs)
            # Hard client errors are not transient. Record them once instead of
            # wasting repeated minutes on a blocked/dead repository endpoint.
            if 400 <= r.status_code < 500 and r.status_code != 429:
                r.raise_for_status()
            r.raise_for_status()
            return r
        except requests.HTTPError as e:
            last = e
            status = getattr(e.response, "status_code", None)
            if status is not None and 400 <= status < 500 and status != 429:
                break
            time.sleep(min(16, 2 ** attempt))
        except Exception as e:
            last = e
            time.sleep(min(16, 2 ** attempt))
    raise RuntimeError(f"GET failed after retries: {url}: {last}")


def get_json(url: str):
    return get(url, headers={"Accept": "application/json"}).json()


def license_allowed(text: str) -> bool:
    t = (text or "").strip().lower()
    if not t:
        return False
    if any(re.search(p, t, re.I) for p in BLOCKED_LICENSE_PATTERNS):
        return False
    return any(re.search(p, t, re.I) for p in ALLOWED_LICENSE_PATTERNS)


def copy_stream(url: str, dest: Path) -> tuple[int, str, str]:
    dest.parent.mkdir(parents=True, exist_ok=True)
    h = hashlib.sha256()
    total = 0
    with S.get(url, stream=True, timeout=(20, 300), allow_redirects=True) as r:
        r.raise_for_status()
        ct = (r.headers.get("Content-Type") or "").lower()
        with dest.open("wb") as f:
            for block in r.iter_content(1024 * 1024):
                if not block:
                    continue
                f.write(block)
                h.update(block)
                total += len(block)
    # Reject common "downloaded an error/login page" failure mode.
    head = dest.read_bytes()[:512].lower()
    if (b"<html" in head or b"<!doctype html" in head) and dest.suffix.lower() not in {".html", ".htm"}:
        dest.unlink(missing_ok=True)
        raise RuntimeError(f"Downloaded HTML instead of data from {url}")
    return total, h.hexdigest(), ct


class Builder:
    def __init__(self, out: Path, cfg: dict):
        self.out = out
        self.cfg = cfg
        self.rows: list[dict] = []
        self.errors: list[dict] = []
        self.seen_hashes: dict[str, str] = {}
        self.raw = out / "RAW_OPEN_DATA"
        self.recordings = out / "EXPERIMENTAL_RECORDINGS"
        self.context = out / "CONTEXTUAL_BIOLOGY_DATA"
        for p in (self.raw, self.recordings, self.context):
            p.mkdir(parents=True, exist_ok=True)

    def area_for(self, evidence_class: str) -> Path:
        if evidence_class == "experimental_recordings":
            return self.recordings
        if evidence_class == "contextual_biology_data":
            return self.context
        return self.raw

    def add_file(
        self, *, species: str, source_id: str, title: str, source_type: str,
        repository: str, doi_or_id: str, license_text: str, evidence_class: str,
        intended_use: str, original_url: str, src_path: Path,
        life_stage_or_form: str = "study-specific; see source",
        baseline_permission: str = "contextual_or_gated",
        source_reported_digest: str = "", notes: str = "",
    ) -> bool:
        digest = sha256(src_path)
        if digest in self.seen_hashes:
            self.errors.append({
                "source_id": source_id, "kind": "deduplicated",
                "detail": f"same SHA-256 as {self.seen_hashes[digest]}"
            })
            return False
        self.seen_hashes[digest] = source_id

        dest_dir = self.area_for(evidence_class) / slug_species(species) / source_id
        dest_dir.mkdir(parents=True, exist_ok=True)
        dest = dest_dir / sanitize(src_path.name)
        if dest.exists():
            dest = dest.with_name(f"{dest.stem}_{digest[:10]}{dest.suffix}")
        shutil.copy2(src_path, dest)

        self.rows.append({
            "species": species,
            "source_id": source_id,
            "title": title,
            "source_type": source_type,
            "repository": repository,
            "doi_or_id": doi_or_id,
            "license": license_text,
            "evidence_class": evidence_class,
            "life_stage_or_form": life_stage_or_form,
            "intended_use": intended_use,
            "baseline_calibration_permission": baseline_permission,
            "original_url": original_url,
            "local_path": str(dest.relative_to(self.out)),
            "bytes": dest.stat().st_size,
            "sha256": digest,
            "source_reported_digest": source_reported_digest,
            "notes": notes,
        })
        return True

    def record_error(self, source_id: str, kind: str, detail: str):
        self.errors.append({"source_id": source_id, "kind": kind, "detail": detail})

    def acquire_direct(self, spec: dict):
        sid = spec["source_id"]
        try:
            parsed = urlparse(spec["url"])
            title = spec["title"]
            ext = Path(parsed.path).suffix
            if not ext:
                if "xlsx" in title.lower():
                    ext = ".xlsx"
                elif "csv" in title.lower():
                    ext = ".csv"
                elif "gz" in title.lower():
                    ext = ".gz"
                else:
                    ext = ".bin"
            tmp = Path(tempfile.mkstemp(suffix=ext)[1])
            try:
                copy_stream(spec["url"], tmp)
                self.add_file(
                    species=spec["species"], source_id=sid, title=title,
                    source_type="direct_repository_file", repository=urlparse(spec["url"]).netloc,
                    doi_or_id=spec.get("doi", ""), license_text=spec["license"],
                    evidence_class=spec["evidence_class"], intended_use=spec["role"],
                    original_url=spec["url"], src_path=tmp,
                    baseline_permission="not_baseline_unless_current_plan_explicitly_promotes",
                    notes="Exact file downloaded from source URL registered in R8.3."
                )
            finally:
                tmp.unlink(missing_ok=True)
        except Exception as e:
            self.record_error(sid, "direct_download_failed", str(e))

    def acquire_zenodo(self, spec: dict):
        sid = spec["source_id"]
        try:
            meta = get_json(f"https://zenodo.org/api/records/{spec['record_id']}")
            lic = (
                (meta.get("metadata") or {}).get("license", {}).get("id")
                or (meta.get("metadata") or {}).get("license", {}).get("title")
                or ""
            )
            if not license_allowed(lic):
                self.record_error(sid, "license_blocked", f"Zenodo license={lic!r}")
                return
            allowed = {x.lower() for x in spec["allowed_extensions"]}
            allow_all = "*" in allowed
            for f in meta.get("files", []):
                name = f.get("key") or f.get("filename") or "zenodo_data.bin"
                if not allow_all and Path(name).suffix.lower() not in allowed:
                    continue
                url = ((f.get("links") or {}).get("content")
                       or (f.get("links") or {}).get("self"))
                if not url:
                    continue
                tmp = Path(tempfile.mkstemp(suffix=Path(name).suffix)[1])
                try:
                    copy_stream(url, tmp)
                    self.add_file(
                        species=spec["species"], source_id=sid,
                        title=(meta.get("metadata") or {}).get("title", f"Zenodo {spec['record_id']}"),
                        source_type="repository_raw_file", repository="Zenodo",
                        doi_or_id=(meta.get("metadata") or {}).get("doi", str(spec["record_id"])),
                        license_text=lic, evidence_class=spec["evidence_class"],
                        intended_use=spec["role"], original_url=url, src_path=tmp,
                        baseline_permission="contextual_or_gated",
                        source_reported_digest=(f.get("checksum") or ""),
                        notes=f"Zenodo file: {name}"
                    )
                finally:
                    tmp.unlink(missing_ok=True)
        except Exception as e:
            self.record_error(sid, "zenodo_failed", str(e))

    def acquire_dryad(self, spec: dict):
        sid = spec["source_id"]
        doi_id = f"doi:{spec['doi']}"
        encoded = quote(doi_id, safe="")
        try:
            ds = get_json(f"https://datadryad.org/api/v2/datasets/{encoded}")
            lic = ds.get("license") or spec.get("license", "")
            if not license_allowed(lic) and "cc0" not in str(spec.get("license","")).lower():
                self.record_error(sid, "license_blocked", f"Dryad license={lic!r}")
                return
            version_href = ((ds.get("_links") or {}).get("stash:version") or {}).get("href")
            if not version_href:
                versions = get_json(f"https://datadryad.org/api/v2/datasets/{encoded}/versions")
                emb = (versions.get("_embedded") or {}).get("stash:versions") or []
                if not emb:
                    raise RuntimeError("No Dryad version available")
                version_id = emb[0]["id"]
            else:
                version_id = int(version_href.rstrip("/").split("/")[-1])
            files = get_json(f"https://datadryad.org/api/v2/versions/{version_id}/files")
            entries = (files.get("_embedded") or {}).get("stash:files") or []
            if not entries:
                raise RuntimeError("No Dryad file entries")
            for f in entries:
                name = f.get("path") or f"dryad_{f.get('id','file')}.bin"
                href = (((f.get("_links") or {}).get("stash:download") or {}).get("href"))
                if not href:
                    continue
                url = href if href.startswith("http") else "https://datadryad.org" + href
                tmp = Path(tempfile.mkstemp(suffix=Path(name).suffix)[1])
                try:
                    copy_stream(url, tmp)
                    digest_reported = f.get("digest") or ""
                    digest_type = (f.get("digestType") or "").lower()
                    if digest_reported and digest_type in {"sha-256","sha256"}:
                        if sha256(tmp).lower() != digest_reported.lower():
                            raise RuntimeError(f"Dryad SHA mismatch for {name}")
                    self.add_file(
                        species=spec["species"], source_id=sid,
                        title=ds.get("title", f"Dryad {spec['doi']}"),
                        source_type="repository_raw_file", repository="Dryad",
                        doi_or_id=spec["doi"], license_text=lic or "CC0",
                        evidence_class=spec["evidence_class"], intended_use=spec["role"],
                        original_url=url, src_path=tmp,
                        baseline_permission="contextual_or_gated",
                        source_reported_digest=(f"{digest_type}:{digest_reported}" if digest_reported else ""),
                        notes=f"Dryad path: {name}"
                    )
                finally:
                    tmp.unlink(missing_ok=True)
        except Exception as e:
            self.record_error(sid, "dryad_failed", str(e))

    @staticmethod
    def _pmc_license_text(nxml_root: ET.Element) -> str:
        texts = []
        for elem in nxml_root.iter():
            tag = elem.tag.split("}")[-1].lower()
            if tag in {"license", "license-p", "copyright-statement"}:
                texts.append(" ".join("".join(elem.itertext()).split()))
                for k, v in elem.attrib.items():
                    if "href" in k.lower():
                        texts.append(v)
        return " | ".join(x for x in texts if x)

    def acquire_edmond(self, spec: dict):
        """Acquire published Max Planck Edmond research data with explicit license audit."""
        sid = spec["source_id"]
        try:
            persistent = quote("doi:" + spec["doi"], safe="")
            meta = get_json(
                "https://edmond.mpg.de/api/datasets/:persistentId/?persistentId=" + persistent
            )
            data = meta.get("data") or {}
            ver = data.get("latestVersion") or {}
            files = ver.get("files") or []
            if not files:
                raise RuntimeError("Edmond dataset returned no published files")

            raw_lic = ver.get("license") or data.get("license")
            if isinstance(raw_lic, dict):
                lic = (
                    raw_lic.get("name") or raw_lic.get("title")
                    or raw_lic.get("identifier") or raw_lic.get("uri") or ""
                )
            else:
                lic = str(raw_lic or "")
            terms = str(ver.get("termsOfUse") or data.get("termsOfUse") or "")

            # Current Edmond Terms of Use state that a dataset published without
            # an assigned license is released under CC0. A dataset-specific
            # license/terms always overrides that repository default.
            if not lic.strip():
                if terms.strip() and not license_allowed(terms):
                    self.record_error(sid, "license_blocked",
                                      "Edmond custom terms present and not recognized as redistributable: " + terms[:500])
                    return
                lic = "CC0 1.0 (Edmond documented default when no dataset-specific license is assigned)"
            elif not license_allowed(lic):
                combined = (lic + " " + terms).strip()
                if not license_allowed(combined):
                    self.record_error(sid, "license_blocked",
                                      "Edmond license/terms not redistribution-compatible: " + combined[:500])
                    return
                lic = combined

            for ent in files:
                df = ent.get("dataFile") or {}
                fid = df.get("id")
                name = df.get("filename") or ("edmond_" + str(fid) + ".bin")
                if not fid:
                    continue
                url = f"https://edmond.mpg.de/api/access/datafile/{fid}"
                tmp = Path(tempfile.mkstemp(suffix=Path(name).suffix)[1])
                try:
                    copy_stream(url, tmp)
                    self.add_file(
                        species=spec["species"], source_id=sid,
                        title=ver.get("datasetVersion") or data.get("identifier") or
                              f"Edmond dataset {spec['doi']}",
                        source_type="repository_raw_file", repository="Edmond / Max Planck Society",
                        doi_or_id=spec["doi"], license_text=lic,
                        evidence_class=spec["evidence_class"],
                        intended_use=spec["role"], original_url=url, src_path=tmp,
                        baseline_permission=spec.get(
                            "calibration_permission", "contextual_or_gated"
                        ),
                        source_reported_digest=(
                            (str(df.get("checksum", {}).get("type", "")) + ":" +
                             str(df.get("checksum", {}).get("value", ""))).strip(":")
                            if isinstance(df.get("checksum"), dict) else ""
                        ),
                        notes=f"Edmond file: {name}; published dataset DOI {spec['doi']}"
                    )
                finally:
                    tmp.unlink(missing_ok=True)
        except Exception as e:
            self.record_error(sid, "edmond_failed", str(e))

    def acquire_pmc(self, spec: dict):
        sid = spec["source_id"]
        pmcid = spec["pmcid"]
        try:
            xml = get(f"https://pmc.ncbi.nlm.nih.gov/utils/oa/oa.fcgi?id={pmcid}").text
            root = ET.fromstring(xml)
            link = None
            for node in root.findall(".//link"):
                if (node.attrib.get("format") or "").lower() == "tgz":
                    link = node.attrib.get("href")
                    break
            if not link:
                raise RuntimeError("PMC OA package tgz link not found")
            if link.startswith("ftp://ftp.ncbi.nlm.nih.gov/"):
                link = "https://ftp.ncbi.nlm.nih.gov/" + link.split("ftp.ncbi.nlm.nih.gov/", 1)[1]
            elif link.startswith("ftp://"):
                link = "https://" + link[6:]

            tgz = Path(tempfile.mkstemp(suffix=".tar.gz")[1])
            work = Path(tempfile.mkdtemp(prefix=f"{pmcid}-"))
            try:
                copy_stream(link, tgz)
                with tarfile.open(tgz, "r:gz") as tf:
                    base = work.resolve()
                    for m in tf.getmembers():
                        target = (work / m.name).resolve()
                        if not str(target).startswith(str(base) + os.sep) and target != base:
                            raise RuntimeError("Unsafe path in PMC archive")
                    tf.extractall(work)
                nxmls = list(work.rglob("*.nxml"))
                if not nxmls:
                    raise RuntimeError("PMC package contains no NXML")
                nroot = ET.parse(nxmls[0]).getroot()
                lic = self._pmc_license_text(nroot)
                if not license_allowed(lic):
                    self.record_error(sid, "license_blocked", lic[:500])
                    return

                refs = []
                xlink = "{http://www.w3.org/1999/xlink}href"
                for elem in nroot.iter():
                    tag = elem.tag.split("}")[-1].lower()
                    if tag in {"supplementary-material", "media"}:
                        href = elem.attrib.get(xlink) or elem.attrib.get("href")
                        if href:
                            refs.append(href)
                allowed = {x.lower() for x in spec["extensions"]}
                found = 0
                for href in sorted(set(refs)):
                    candidate = nxmls[0].parent / href
                    if not candidate.exists():
                        matches = list(work.rglob(Path(href).name))
                        if matches:
                            candidate = matches[0]
                    if not candidate.exists() or not candidate.is_file():
                        continue
                    if candidate.suffix.lower() not in allowed:
                        continue
                    evidence_class = spec["evidence_class"]
                    if candidate.suffix.lower() in {".mp4",".m4v",".mov",".avi",".tif",".tiff"}:
                        evidence_class = "experimental_recordings"
                    self.add_file(
                        species=spec["species"], source_id=sid,
                        title=f"{pmcid} supplementary material",
                        source_type="publisher_open_supplement", repository="NCBI PMC OA package",
                        doi_or_id=pmcid, license_text=lic,
                        evidence_class=evidence_class, intended_use=spec["role"],
                        original_url=f"https://pmc.ncbi.nlm.nih.gov/articles/{pmcid}/",
                        src_path=candidate,
                        baseline_permission="not_baseline_unless_current_plan_explicitly_promotes",
                        notes=f"OA-package supplementary href: {href}"
                    )
                    found += 1
                if not found:
                    self.record_error(sid, "no_matching_supplements",
                                      f"Package acquired/licensed, but no configured extensions among {refs}")
            finally:
                tgz.unlink(missing_ok=True)
                shutil.rmtree(work, ignore_errors=True)
        except Exception as e:
            self.record_error(sid, "pmc_failed", str(e))

    def write_outputs(self):
        manifest = self.out / "STUDY_DATA_MANIFEST.csv"
        with manifest.open("w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=FIELDS)
            w.writeheader()
            w.writerows(self.rows)

        with (self.out / "ACQUISITION_ERRORS_AND_BLOCKS.csv").open("w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=["source_id","kind","detail"])
            w.writeheader()
            w.writerows(self.errors)

        with (self.out / "REMOTE_ONLY_HIGH_VALUE_DATA.csv").open("w", newline="", encoding="utf-8") as f:
            fields = ["species","source_id","title","url","role","reason"]
            w = csv.DictWriter(f, fieldnames=fields)
            w.writeheader()
            w.writerows(self.cfg.get("remote_only", []))

        summary = {
            "files_acquired": len(self.rows),
            "bytes_acquired": sum(int(r["bytes"]) for r in self.rows),
            "by_species": {},
            "by_evidence_class": {},
            "errors_or_blocks": len(self.errors),
        }
        for r in self.rows:
            s = summary["by_species"].setdefault(r["species"], {"files":0,"bytes":0})
            s["files"] += 1; s["bytes"] += int(r["bytes"])
            e = summary["by_evidence_class"].setdefault(r["evidence_class"], {"files":0,"bytes":0})
            e["files"] += 1; e["bytes"] += int(r["bytes"])
        (self.out / "STUDY_DATA_SUMMARY.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")

        # Per-file digest list for all acquired bytes.
        lines = []
        for r in sorted(self.rows, key=lambda x: x["local_path"]):
            lines.append(f"{r['sha256']}  {r['local_path']}")
        (self.out / "STUDY_DATA_SHA256SUMS.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
        return summary


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--config", required=True, type=Path)
    ap.add_argument("--output", required=True, type=Path)
    args = ap.parse_args()
    cfg = json.loads(args.config.read_text(encoding="utf-8"))
    args.output.mkdir(parents=True, exist_ok=True)
    b = Builder(args.output, cfg)

    for spec in cfg.get("dryad", []):
        b.acquire_dryad(spec)
    for spec in cfg.get("edmond", []):
        b.acquire_edmond(spec)
    for spec in cfg.get("pmc", []):
        b.acquire_pmc(spec)
    for spec in cfg.get("direct", []):
        b.acquire_direct(spec)
    for spec in cfg.get("zenodo", []):
        b.acquire_zenodo(spec)

    summary = b.write_outputs()
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
