#!/usr/bin/env python3
"""Build-only retrieval of omitted license TEXT from exact upstream crate revisions.

No runtime network dependency. Never fetch moving branches or font/assets/code.
Requests are derived from Cargo metadata and each crate's .cargo_vcs_info.json.
"""
from __future__ import annotations
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import re
import urllib.error
import urllib.request

NAMES=('LICENSE-MIT','LICENSE-APACHE','LICENSE','LICENSE.md','LICENSE.txt','COPYING','COPYING.LESSER','UNLICENSE','license','license.md','LICENSE-MIT.md','LICENSE-APACHE.md')

def validate_request(item: dict) -> dict:
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+',item.get('repo','')) or '..' in item['repo']:
        raise ValueError('Expected a plain GitHub owner/repository')
    if not re.fullmatch('[0-9a-f]{40}',item.get('commit','')):
        raise ValueError('License source must use the exact 40-character crate revision')
    packages=item.get('packages',[])
    if not packages or any(not isinstance(p,str) or not re.fullmatch(r'[A-Za-z0-9_.+\-]+',p) or '..' in p for p in packages):
        raise ValueError('Unsafe or empty package list')
    return item

def fetch_one(item: dict, output: Path) -> dict:
    item=validate_request(item)
    records=[]
    for name in NAMES:
        url=f"https://raw.githubusercontent.com/{item['repo']}/{item['commit']}/{name}"
        try:
            request=urllib.request.Request(url,headers={'User-Agent':'InsectRealism-build-license-audit/1'})
            with urllib.request.urlopen(request,timeout=30) as response:
                data=response.read(2_000_001)
        except urllib.error.HTTPError as exc:
            if exc.code==404: continue
            raise
        if len(data)>2_000_000: raise ValueError('Oversized upstream license')
        text=data.decode('utf-8')
        if len(text.strip())<50: raise ValueError('Empty or implausibly short upstream license')
        for package in item['packages']:
            dest=output/package/('UPSTREAM-'+name)
            dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_bytes(data)
        records.append({'name':name,'url':url,'sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data)})
    if not records: raise ValueError(f"No root license TEXT found at {item['repo']}@{item['commit']}")
    return {**item,'licenses':records}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--requests',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    a=p.parse_args()
    requests=json.loads(a.requests.read_text('utf-8'))
    for item in requests: validate_request(item)
    a.output.mkdir(parents=True,exist_ok=True)
    with ThreadPoolExecutor(max_workers=6) as pool:
        records=list(pool.map(lambda item:fetch_one(item,a.output),requests))
    (a.output/'UPSTREAM_SOURCES.json').write_text(json.dumps(records,indent=2)+'\n',encoding='utf-8')
    print(f'Fetched license text for {sum(len(r["packages"]) for r in records)} crate versions from {len(records)} pinned revisions')
if __name__=='__main__':main()
