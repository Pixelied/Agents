#!/usr/bin/env python3
"""Collect dependency notices from Cargo's exact resolved source, never guess unknown licenses."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess

# An explicit allowlist for this build pipeline, not a general legal opinion.
ALLOWED={'MIT','Apache-2.0','BSD-2-Clause','BSD-3-Clause','ISC','Zlib','0BSD','CC0-1.0',
         'Unicode-DFS-2016','Unicode-3.0','BSL-1.0','OFL-1.1','Ubuntu-font-1.0','MPL-2.0','Unlicense',
         'Apache-2.0 WITH LLVM-exception'}
FONT_EXTENSIONS={'.ttf','.otf','.ttc','.woff','.woff2'}

def select_license(expression:str|None)->list[str]|None:
    if not expression:return None
    # Legacy Cargo manifests used '/' for an OR expression. Never rewrite AND/WITH.
    if re.fullmatch(r'[A-Za-z0-9.+-]+(?:\s*/\s*[A-Za-z0-9.+-]+)+',expression):
        expression=expression.replace('/', ' OR ')
    tokens=re.findall(r'\(|\)|[A-Za-z0-9.+-]+',expression)
    if ''.join(tokens)!=''.join(expression.split()):return None
    index=0
    def atom():
        nonlocal index
        if index>=len(tokens):raise ValueError('missing license')
        if tokens[index]=='(':
            index+=1;value=alternative()
            if index>=len(tokens) or tokens[index]!=')':raise ValueError('unclosed group')
            index+=1;return value
        license=tokens[index];index+=1
        if license in ('AND','OR','WITH',')'):raise ValueError('unexpected operator')
        if index<len(tokens) and tokens[index]=='WITH':
            index+=1
            if index>=len(tokens):raise ValueError('missing exception')
            license+=' WITH '+tokens[index];index+=1
        return [license] if license in ALLOWED else None
    def conjunction():
        nonlocal index
        result=atom()
        while index<len(tokens) and tokens[index]=='AND':
            index+=1;other=atom()
            result=result+other if result is not None and other is not None else None
        return result
    def alternative():
        nonlocal index
        result=conjunction()
        while index<len(tokens) and tokens[index]=='OR':
            index+=1;other=conjunction()
            result=other if other==['MIT'] else (result if result is not None else other)
        return result
    try:
        result=alternative()
        return result if index==len(tokens) else None
    except ValueError:return None

def is_license_file(path:Path)->bool:
    if path.suffix.lower() in FONT_EXTENSIONS:return False
    name=path.name.lower()
    return (name.startswith(('license','licence','copying','copyright','notice'))
            or '-license' in name or name in ('ofl.txt','ufl.txt','hack-regular.txt'))

def application_dependencies(desktop, nodes):
    selected=set();pending=[desktop]
    while pending:
        package_id=pending.pop()
        if package_id in selected:continue
        selected.add(package_id)
        for dep in nodes.get(package_id,{}).get('deps',[]):
            if any(kind.get('kind') != 'dev' for kind in dep.get('dep_kinds',[])):
                pending.append(dep['pkg'])
    return selected

def license_supplements(package,base):
    directory=Path(__file__).resolve().parents[1]/'app/packaging/third-party-license-supplements'
    index=directory/'index.json'
    if not index.exists():return []
    entries=json.loads(index.read_text(encoding='utf-8'))['entries'];out=[]
    for entry in entries:
        if entry['crate']!=package['name'] or entry['version']!=package['version']:continue
        vcs=json.loads((base/'.cargo_vcs_info.json').read_text(encoding='utf-8'))
        if vcs['git']['sha1']!=entry['upstream_commit']:raise ValueError('license source commit mismatch')
        path=directory/entry['local_path']
        if not path.resolve().is_relative_to(directory.resolve()):raise ValueError('unsafe license supplement path')
        data=path.read_bytes()
        if hashlib.sha256(data).hexdigest()!=entry['sha256']:raise ValueError('license supplement digest mismatch')
        if 'declaration_manifest_sha256' in entry:
            actual=hashlib.sha256((base/'Cargo.toml').read_bytes()).hexdigest()
            if actual!=entry['declaration_manifest_sha256']:raise ValueError('license declaration manifest mismatch')
        out.append((path,entry))
    return out

def collect(manifest:Path,target:str,output:Path)->None:
    command=['cargo','metadata','--format-version=1','--locked','--manifest-path',str(manifest),'--filter-platform',target]
    metadata=json.loads(subprocess.check_output(command,text=True,encoding='utf-8'))
    packages={p['id']:p for p in metadata['packages']}
    nodes={n['id']:n for n in metadata['resolve']['nodes']}
    desktop=next(p['id'] for p in metadata['packages'] if p['name']=='desktop-app' and p['source'] is None)
    selected=application_dependencies(desktop,nodes)
    output.mkdir(parents=True,exist_ok=True);(output/'licenses').mkdir(exist_ok=True)
    rows=[];unresolved=[]
    workspace=Path(metadata['workspace_root']).parent
    for id in sorted(selected,key=lambda x:(packages[x]['name'],packages[x]['version'])):
        package=packages[id];name=package['name'];version=package['version'];base=Path(package['manifest_path']).parent
        choice=select_license(package.get('license'))
        texts=[]
        for path in base.rglob('*'):
            if path.is_file() and is_license_file(path) and path.stat().st_size<=1_048_576:
                # A vendored crate may not cause reads outside its own package directory.
                if path.resolve().is_relative_to(base.resolve()):texts.append(path)
        if package.get('license_file'):
            explicit=base/package['license_file']
            if explicit.exists() and explicit.resolve().is_relative_to(base.resolve()):texts.append(explicit)
        if package['source'] is None and (workspace/'LICENSE').is_file():texts.append(workspace/'LICENSE')
        supplements=license_supplements(package,base)
        texts.extend(path for path,_ in supplements)
        texts=list(dict.fromkeys(texts))
        if choice is None or not texts:unresolved.append(f'{name} {version}: license={package.get("license")!r}; texts={len(texts)}')
        copied=[]
        for path in texts:
            relative=path.relative_to(base).as_posix() if path.is_relative_to(base) else path.name
            destination=output/'licenses'/f'{name}-{version}'/relative
            destination.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(path,destination)
            copied.append({'path':destination.relative_to(output).as_posix(),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
        if any(entry.get('standard_terms') for _,entry in supplements):
            destination=output/'licenses'/f'{name}-{version}'
            shutil.copyfile(base/'Cargo.toml',destination/'LICENSE-SOURCE-DECLARATION.toml')
            lines=['# Original package copyright notices','',
                'These lines are retained verbatim from the resolved package. No copyright holder is inferred.',
                'Full MIT terms accompany the upstream SPDX declaration; upstream SDK caveats, where present, are retained.', '']
            for source in sorted(base.rglob('*')):
                if not source.is_file() or source.suffix.lower() not in ('.rs','.md','.txt','.toml'):continue
                if not source.resolve().is_relative_to(base.resolve()):continue
                for number,line in enumerate(source.read_text(encoding='utf-8').splitlines(),1):
                    if 'copyright' in line.lower():lines.append(f'{source.relative_to(base)}:{number}: {line}')
            (destination/'COPYRIGHT-NOTICES.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8')
        if choice is not None and 'MPL-2.0' in choice:
            # Provide the exact unmodified corresponding crate source with its license.
            # Do not change the original MPL source notices or relabel them as app MIT.
            for source in base.rglob('*'):
                if not source.is_file() or source.suffix.lower() in FONT_EXTENSIONS:continue
                if not source.resolve().is_relative_to(base.resolve()):raise ValueError('unsafe dependency source path')
                destination=output/'licenses'/f'{name}-{version}'/'corresponding-source'/source.relative_to(base)
                destination.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,destination)
        rows.append({'name':name,'version':version,'spdx':package.get('license'),'selected_branch':choice,
                     'repository':package.get('repository'),'license_files':copied,
                     'supplement_provenance':[entry for _,entry in supplements],
                     'corresponding_source_included':choice is not None and 'MPL-2.0' in choice})
    report={'target':target,'source':'Cargo.lock and resolved package manifests','packages':rows,'unresolved':unresolved}
    (output/'LICENSE_AUDIT.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    notice=['# Third-party software notices','',
            'Resolved application dependencies (including build-time dependencies). Original license and notice texts are in `licenses/`.',
            'MPL-2.0 dependencies include their exact unmodified corresponding source under `licenses/<crate>/corresponding-source/`. Those files remain MPL-2.0, independently of the application MIT license.',
            'No standalone font files are redistributed here. A compiled egui binary may embed its dependency-provided fonts; their supplied license notices are retained.',
            '', '| Package | Version | Declared SPDX | Selected allowed branch |','|---|---|---|---|']
    for row in rows:notice.append(f'| {row["name"]} | {row["version"]} | {row["spdx"]} | {" AND ".join(row["selected_branch"] or ["REVIEW REQUIRED"])} |')
    (output/'THIRD_PARTY_NOTICES.md').write_text('\n'.join(notice)+'\n',encoding='utf-8')
    if unresolved:raise ValueError('Unresolved license review:\n'+'\n'.join(unresolved))
    print(json.dumps({'target':target,'packages':len(rows),'unresolved':0,'output':str(output)}))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--manifest',type=Path,required=True)
    p.add_argument('--target',required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();collect(a.manifest,a.target,a.output)
if __name__=='__main__':
    try:main()
    except (ValueError,OSError,subprocess.CalledProcessError) as e:raise SystemExit(str(e))
