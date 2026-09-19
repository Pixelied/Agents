#!/usr/bin/env python3
"""Stage a real native executable and its auditable resources; never manufactures binaries."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import plistlib
import shutil
import tomllib
from verify_package import inspect_pe,inspect_macho,verify_package

ROOT=Path(__file__).resolve().parents[1]

def stage(binary:Path,output:Path,platform:str,license_dir:Path)->None:
    binary=binary.resolve(strict=True)
    payload=binary.read_bytes()
    if len(payload)<4096:raise ValueError('refuse placeholder binary')
    (inspect_pe if platform=='windows' else inspect_macho)(payload)
    if output.exists():raise ValueError('output must be a new staging directory: '+str(output))
    if not (license_dir/'THIRD_PARTY_NOTICES.md').is_file():
        raise ValueError('collect dependency licenses before packaging')
    app_version=tomllib.loads((ROOT/'app/Cargo.toml').read_text(encoding='utf-8'))['workspace']['package']['version']
    profile_report=json.loads((ROOT/'app/assets/creature-profiles/runtime-profiles.report.json').read_text(encoding='utf-8'))
    if platform=='macos':
        executable=output/'Contents/MacOS/InsectRealism';resources=output/'Contents/Resources'
        info=plistlib.loads((ROOT/'app/packaging/macos/Info.plist').read_bytes())
        info['CFBundleShortVersionString']=app_version;info['CFBundleVersion']=app_version
        executable.parent.mkdir(parents=True)
        (output/'Contents/Info.plist').write_bytes(plistlib.dumps(info))
    else:
        output.mkdir(parents=True);executable=output/'InsectRealism.exe';resources=output/'Resources'
    resources.mkdir(parents=True)
    icon_name = 'InsectRealism.icns' if platform == 'macos' else 'InsectRealism.ico'
    shutil.copy2(ROOT/'app/packaging/icons'/icon_name, resources/icon_name)
    shutil.copy2(binary,executable);executable.chmod(0o755)
    (resources/'creature-profiles').mkdir()
    for filename in ['runtime-profiles.bin','runtime-profiles.report.json']:
        shutil.copy2(ROOT/'app/assets/creature-profiles'/filename,resources/'creature-profiles'/filename)
    shutil.copytree(ROOT/'app/assets/presets',resources/'presets')
    shutil.copytree(ROOT/'app/crates/rendering/shaders',resources/'shaders')
    shutil.copy2(ROOT/'docs/BIOLOGY_NOTICES.md',resources/'BIOLOGY_NOTICES.md')
    shutil.copy2(ROOT/'LICENSE',resources/'APP_LICENSE.txt')
    shutil.copy2(license_dir/'THIRD_PARTY_NOTICES.md',resources/'THIRD_PARTY_NOTICES.md')
    shutil.copytree(license_dir/'licenses',resources/'licenses')
    (resources/'versions.json').write_text(json.dumps({'app_version':app_version,'profile_version':profile_report['profile_version'],
        'profile_schema':profile_report['schema_version'],'profile_sha256':profile_report['sha256'],
        'qualification':'development build; native acceptance must be recorded separately'},indent=2)+'\n',encoding='utf-8')
    verify_package(output,platform)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--binary',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--platform',choices=['macos','windows'],required=True);p.add_argument('--licenses',type=Path,required=True)
    a=p.parse_args();stage(a.binary,a.output,a.platform,a.licenses)
    print(a.output)
if __name__=='__main__':
    try:main()
    except (OSError,ValueError,KeyError) as e:raise SystemExit('Staging failed: '+str(e))
