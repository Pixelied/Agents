#!/usr/bin/env python3
"""Deterministic WiX v4 fragments for exactly the staged runtime files."""
import argparse,hashlib,pathlib,uuid,xml.etree.ElementTree as E
NS='http://wixtoolset.org/schemas/v4/wxs';E.register_namespace('',NS)
def tag(x):return '{'+NS+'}'+x
def ident(prefix,rel):return prefix+hashlib.sha256(rel.encode()).hexdigest()[:24]
def harvest(stage,output):
 stage=pathlib.Path(stage);root=E.Element(tag('Wix'));frag=E.SubElement(root,tag('Fragment'));ref=E.SubElement(frag,tag('DirectoryRef'),Id='INSTALLFOLDER');dirs={pathlib.Path('.'):('INSTALLFOLDER',ref)}
 for d in sorted((p for p in stage.rglob('*') if p.is_dir()),key=lambda p:(len(p.relative_to(stage).parts),str(p))):
  rel=d.relative_to(stage);parent=dirs[rel.parent][1];key=ident('D_',rel.as_posix());dirs[rel]=(key,E.SubElement(parent,tag('Directory'),Id=key,Name=d.name))
 group=E.SubElement(E.SubElement(root,tag('Fragment')),tag('ComponentGroup'),Id='RuntimeFiles')
 for f in sorted(stage.rglob('*')):
  if f.is_symlink():raise ValueError('Do not harvest symlinks')
  if not f.is_file():continue
  rel=f.relative_to(stage);name=rel.as_posix();key=ident('C_',name)
  c=E.SubElement(group,tag('Component'),Id=key,Directory=dirs[rel.parent][0],Guid=str(uuid.uuid5(uuid.NAMESPACE_URL,'studio.pixelied.insect-realism/'+name)),Bitness='always64')
  E.SubElement(c,tag('File'),Id='ApplicationExe' if name=='insect-realism.exe' else ident('F_',name),Source='$(var.StageDir)\\'+name.replace('/','\\'))
  E.SubElement(c,tag('RegistryValue'),Root='HKCU',Key='Software\\Pixelied Studio\\Insect Realism\\Components',Name=key,Type='integer',Value='1',KeyPath='yes')
  E.SubElement(c,tag('RemoveFolder'),Id=ident('R_',name),Directory=dirs[rel.parent][0],On='uninstall')
 E.indent(root);E.ElementTree(root).write(output,encoding='utf-8',xml_declaration=True)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('stage',type=pathlib.Path);p.add_argument('output',type=pathlib.Path);a=p.parse_args();harvest(a.stage,a.output)
