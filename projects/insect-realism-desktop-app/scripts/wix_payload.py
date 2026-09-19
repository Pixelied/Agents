#!/usr/bin/env python3
"""Generate deterministic WiX v4 components from the VERIFIED staged payload, no glob omissions."""
import argparse
from pathlib import Path
import uuid
import xml.etree.ElementTree as ET
from verify_package import verify_package,verify_manifest
NS='http://wixtoolset.org/schemas/v4/wxs';ET.register_namespace('',NS)
GUID_NAMESPACE=uuid.UUID('887204d0-8839-49d6-a242-f9e0e0d56c72')
def node(tag,attributes=None):return ET.Element('{'+NS+'}'+tag,attributes or {})
def identifier(prefix,path):return prefix+uuid.uuid5(GUID_NAMESPACE,path).hex

def generate(root:Path,output:Path):
    verify_package(root,'windows');verify_manifest(root)
    wix=node('Wix');fragment=node('Fragment');wix.append(fragment)
    directory=node('DirectoryRef',{'Id':'INSTALLFOLDER'});fragment.append(directory)
    directories={'':directory};components=[]
    for path in sorted(root.rglob('*')):
        if path.is_symlink():raise ValueError('installer payload must not contain symlinks')
        relative=path.relative_to(root).as_posix()
        parent=path.parent.relative_to(root).as_posix()
        parent='' if parent=='.' else parent
        if path.is_dir():
            element=node('Directory',{'Id':identifier('Dir',relative),'Name':path.name})
            directories[parent].append(element);directories[relative]=element;continue
        component_id=identifier('Cmp',relative)
        component=node('Component',{'Id':component_id,'Guid':str(uuid.uuid5(GUID_NAMESPACE,relative)).upper()})
        # HKCU keypaths avoid per-user directory keypath/repair ambiguities.
        registry=node('RegistryValue',{'Root':'HKCU','Key':'Software\\Pixelied\\InsectRealism\\Components',
            'Name':component_id,'Type':'integer','Value':'1','KeyPath':'yes'})
        component.append(registry)
        component.append(node('File',{'Id':'MainExecutable' if relative=='InsectRealism.exe' else identifier('File',relative),
            'Source':str(path.resolve()),'Name':path.name}))
        directories[parent].append(component);components.append(component_id)
    # MSI removes installed files, not arbitrary application directory trees. Register
    # one empty-folder removal per created directory; preserve any user-added contents.
    for relative, parent in sorted(directories.items()):
        directory_id='INSTALLFOLDER' if not relative else identifier('Dir',relative)
        identity='directory:'+relative
        component_id=identifier('Cleanup',identity)
        component=node('Component',{'Id':component_id,'Guid':str(uuid.uuid5(GUID_NAMESPACE,identity)).upper()})
        component.append(node('RegistryValue',{'Root':'HKCU','Key':'Software\\Pixelied\\InsectRealism\\Components',
            'Name':component_id,'Type':'integer','Value':'1','KeyPath':'yes'}))
        component.append(node('RemoveFolder',{'Id':identifier('Remove',identity),'Directory':directory_id,'On':'uninstall'}))
        parent.append(component);components.append(component_id)
    group=node('ComponentGroup',{'Id':'ApplicationFiles'});fragment.append(group)
    for name in components:group.append(node('ComponentRef',{'Id':name}))
    ET.indent(wix);output.write_bytes(ET.tostring(wix,encoding='utf-8',xml_declaration=True))
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('root',type=Path);p.add_argument('output',type=Path)
    a=p.parse_args();generate(a.root,a.output)
