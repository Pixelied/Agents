#!/usr/bin/env python3
"""Hash final signed package bytes without modifying its sealed bundle."""
import argparse,hashlib,json,pathlib
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('root',type=pathlib.Path);p.add_argument('output',type=pathlib.Path);a=p.parse_args();root=a.root.resolve()
 files={}
 for f in sorted(root.rglob('*')):
  if f.is_symlink():raise ValueError('Unexpected symlink in this statically linked package')
  if f.is_file() and f.resolve()!=a.output.resolve():files[f.relative_to(root).as_posix()]=hashlib.sha256(f.read_bytes()).hexdigest()
 if not files:raise ValueError('Empty package')
 a.output.write_text(json.dumps({'root_name':root.name,'files':files,'native_input_safety_verified':False},indent=2,sort_keys=True)+'\n')
