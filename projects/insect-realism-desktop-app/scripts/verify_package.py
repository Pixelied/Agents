#!/usr/bin/env python3
"""Inspect real staged packages. Header/integrity checks do NOT certify native launch or input safety."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import plistlib
import struct

MANIFEST = 'PACKAGE_SHA256.json'
FONT_SUFFIXES = {'.ttf','.otf','.woff','.woff2','.ttc'}

def inspect_pe(data: bytes) -> dict:
    if len(data) < 64 or data[:2] != b'MZ':
        raise ValueError('not a PE executable')
    offset = struct.unpack_from('<I', data, 0x3c)[0]
    if offset > len(data)-24 or data[offset:offset+4] != b'PE\0\0':
        raise ValueError('invalid PE signature/header offset')
    machine = struct.unpack_from('<H', data, offset+4)[0]
    optional_size = struct.unpack_from('<H', data, offset+20)[0]
    opt = offset+24
    if optional_size < 70 or opt+optional_size > len(data):
        raise ValueError('truncated PE optional header')
    magic = struct.unpack_from('<H',data,opt)[0]
    subsystem = struct.unpack_from('<H',data,opt+68)[0]
    if machine != 0x8664 or magic != 0x20b:
        raise ValueError('package requires a Windows x64 PE32+ executable')
    if subsystem != 2:
        raise ValueError('Windows executable is not GUI-subsystem (console must not appear)')
    return {'format':'PE32+','architecture':'x64','subsystem':subsystem,'native_launch_verified':False}

def inspect_macho(data: bytes) -> dict:
    if len(data) < 32 or struct.unpack_from('<I',data)[0] != 0xfeedfacf:
        raise ValueError('package requires a thin 64-bit Mach-O executable')
    _, cpu, _, filetype, commands, command_bytes, _, _ = struct.unpack_from('<8I',data)
    if filetype != 2 or cpu not in (0x0100000c,0x01000007):
        raise ValueError('not a supported executable Mach-O')
    if command_bytes > len(data)-32:
        raise ValueError('truncated Mach-O load commands')
    return {'format':'Mach-O','architecture':'arm64' if cpu==0x0100000c else 'x64',
            'load_commands':commands,'native_launch_verified':False}

def inventory(root: Path) -> dict:
    result={}
    for parent, dirs, names in os.walk(root,followlinks=False):
        parent=Path(parent)
        for name in sorted(dirs+names):
            p=parent/name
            relative=p.relative_to(root).as_posix()
            if relative==MANIFEST:continue
            if p.is_symlink():
                result[relative]={'symlink':os.readlink(p)}
            elif p.is_file():
                result[relative]=hashlib.sha256(p.read_bytes()).hexdigest()
    return dict(sorted(result.items()))

def write_manifest(root: Path) -> None:
    root=root.resolve(strict=True)
    (root/MANIFEST).write_text(json.dumps(inventory(root),indent=2)+'\n',encoding='utf-8')

def verify_manifest(root: Path) -> None:
    root=root.resolve(strict=True)
    expected=json.loads((root/MANIFEST).read_text(encoding='utf-8'))
    if not isinstance(expected,dict):raise ValueError('manifest is not an object')
    for name,value in expected.items():
        p=PurePosixPath(name)
        if not name or p.is_absolute() or '..' in p.parts or '\\' in name or name==MANIFEST:
            raise ValueError('unsafe manifest path')
        if not (isinstance(value,str) and len(value)==64) and not (
            isinstance(value,dict) and set(value)=={'symlink'} and isinstance(value['symlink'],str)):
            raise ValueError('invalid checksum/symlink entry')
    actual=inventory(root)
    if expected!=actual:
        changed=sorted(name for name in expected.keys()|actual.keys() if expected.get(name)!=actual.get(name))
        raise ValueError('package integrity mismatch: '+', '.join(changed[:8]))

def verify_resources(resources:Path)->dict:
    files=[p for p in resources.rglob('*') if p.is_file()]
    if any(p.suffix.lower() in FONT_SUFFIXES for p in files):
        raise ValueError('standalone font files are not allowed in this deliverable')
    profile=resources/'creature-profiles/runtime-profiles.bin'
    report=json.loads((resources/'creature-profiles/runtime-profiles.report.json').read_text(encoding='utf-8'))
    content=profile.read_bytes()
    if content[:8]!=b'ANTBIO01' or len(content)<100:
        raise ValueError('missing or invalid biology bundle envelope')
    if struct.unpack_from('<Q',content,8)[0] != len(content)-48:
        raise ValueError('biology payload length differs from envelope')
    if hashlib.sha256(content[48:]).digest() != content[16:48]:
        raise ValueError('biology payload digest differs from envelope')
    if hashlib.sha256(content).hexdigest()!=report['sha256']:
        raise ValueError('biology bundle digest differs from its provenance report')
    # Compositing uses wgpu's fixed-function premultiplied blend state in the ant pass.
    # A second, unused composite.wgsl is intentionally not required or manufactured.
    for name in ['presets/realistic.toml','presets/light.toml','presets/heavy.toml','presets/nightmare.toml',
                 'shaders/ant.wgsl','BIOLOGY_NOTICES.md','APP_LICENSE.txt','THIRD_PARTY_NOTICES.md','versions.json']:
        if not (resources/name).is_file() or not (resources/name).stat().st_size:
            raise ValueError('missing package resource: '+name)
    versions=json.loads((resources/'versions.json').read_text(encoding='utf-8'))
    if (versions['profile_version']!=report['profile_version'] or versions['profile_schema']!=report['schema_version']
        or versions.get('profile_sha256')!=report['sha256']):
        raise ValueError('profile version metadata mismatch')
    return versions

def verify_icon(path: Path, platform: str) -> None:
    """Validate the staged icon container, not native visual appearance."""
    if not path.is_file():
        raise ValueError('missing package icon: ' + path.name)
    data = path.read_bytes()
    if platform == 'macos':
        if len(data) < 16 or data[:4] != b'icns' or struct.unpack_from('>I', data, 4)[0] != len(data):
            raise ValueError('invalid macOS icon container')
        offset = 8
        while offset < len(data):
            if offset + 8 > len(data):
                raise ValueError('truncated macOS icon chunk')
            length = struct.unpack_from('>I', data, offset + 4)[0]
            if length < 8 or offset + length > len(data):
                raise ValueError('invalid macOS icon chunk length')
            offset += length
    else:
        if len(data) < 22 or data[:4] != b'\x00\x00\x01\x00':
            raise ValueError('invalid Windows icon container')
        count = struct.unpack_from('<H', data, 4)[0]
        directory_end = 6 + 16 * count
        if not count or directory_end > len(data):
            raise ValueError('truncated Windows icon directory')
        for index in range(count):
            length, offset = struct.unpack_from('<II', data, 6 + index * 16 + 8)
            if length < 8 or offset < directory_end or offset + length > len(data):
                raise ValueError('invalid Windows icon image bounds')

def verify_package(root:Path,platform:str)->dict:
    root=root.resolve(strict=True)
    if platform=='macos':
        info=plistlib.loads((root/'Contents/Info.plist').read_bytes())
        if info.get('LSUIElement') is not True or info.get('CFBundlePackageType')!='APPL':
            raise ValueError('macOS bundle must be an LSUIElement utility app')
        name=info.get('CFBundleExecutable')
        if name!='InsectRealism':raise ValueError('unexpected bundle executable')
        binary=root/'Contents/MacOS'/name
        if not os.access(binary,os.X_OK):raise ValueError('macOS executable permission missing')
        if binary.stat().st_size<4096:raise ValueError('refuse placeholder Mach-O payload')
        details=inspect_macho(binary.read_bytes())
        if info.get('CFBundleIconFile') != 'InsectRealism.icns':
            raise ValueError('macOS bundle icon metadata missing or unexpected')
        verify_icon(root/'Contents/Resources/InsectRealism.icns', 'macos')
        versions=verify_resources(root/'Contents/Resources')
        if info.get('CFBundleShortVersionString')!=versions['app_version']:
            raise ValueError('bundle application version mismatch')
    else:
        binary=root/'InsectRealism.exe'
        if binary.stat().st_size<4096:raise ValueError('refuse placeholder PE payload')
        details=inspect_pe(binary.read_bytes())
        verify_icon(root/'Resources/InsectRealism.ico', 'windows')
        versions=verify_resources(root/'Resources')
    details.update({'versions':versions,'binary_sha256':hashlib.sha256(binary.read_bytes()).hexdigest(),
                    'signature_verified':False,'native_input_verified':False})
    return details

def main()->None:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('root',type=Path)
    parser.add_argument('--platform',choices=['macos','windows'])
    parser.add_argument('--write-manifest',action='store_true')
    parser.add_argument('--verify-manifest',action='store_true')
    parser.add_argument('--json',type=Path)
    args=parser.parse_args()
    if args.write_manifest:write_manifest(args.root)
    if args.verify_manifest:verify_manifest(args.root)
    result=verify_package(args.root,args.platform) if args.platform else {'integrity_checked':args.verify_manifest}
    text=json.dumps(result,indent=2)+'\n'
    if args.json:args.json.write_text(text,encoding='utf-8')
    print(text,end='')
if __name__=='__main__':
    try:main()
    except (OSError,ValueError,KeyError,struct.error) as e:raise SystemExit('Package verification failed: '+str(e))
