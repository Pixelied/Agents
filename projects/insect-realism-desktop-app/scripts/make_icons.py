#!/usr/bin/env python3
"""Generate original procedural utility icons without third-party art or fonts."""
import math,pathlib,struct,zlib

def png(size):
 # The icon is a symbolic silhouette, not a physical-scale biology asset.
 def shape(x,y):
  for cx,cy,rx,ry in [(0,-.48,.18,.19),(0,-.02,.13,.25),(0,.49,.23,.30)]:
   if ((x-cx)/rx)**2+((y-cy)/ry)**2<=1:return True
  segments=[(0,-.48,0,.49)]
  for side in [-1,1]:
   for root,outer,tip in [(-.18,-.38,-.62),(0,.05,.02),(.2,.38,.7)]:
    segments.extend([(side*.10,root,side*.45,outer),(side*.45,outer,side*.67,tip)])
   segments.extend([(side*.08,-.58,side*.26,-.79),(side*.26,-.79,side*.46,-.85)])
  for ax,ay,bx,by in segments:
   dx,dy=bx-ax,by-ay;t=max(0,min(1,((x-ax)*dx+(y-ay)*dy)/(dx*dx+dy*dy)))
   if math.hypot(x-ax-t*dx,y-ay-t*dy)<.022:return True
  return False
 data=bytearray()
 for y in range(size):
  data.append(0)
  for x in range(size):
   coverage=sum(shape(((x+ox)/size-.5)*2.2,((y+oy)/size-.5)*2.2) for ox,oy in [(0.25,.25),(.75,.25),(.25,.75),(.75,.75)])
   data.extend([30,30,30,round(coverage*255/4)])
 def chunk(tag,payload):return struct.pack('>I',len(payload))+tag+payload+struct.pack('>I',zlib.crc32(tag+payload)&0xffffffff)
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',size,size,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(data,9))+chunk(b'IEND',b'')
def generate(out):
 out=pathlib.Path(out);out.mkdir(parents=True,exist_ok=True)
 p=png(256);(out/'InsectRealism.png').write_bytes(p)
 ico=struct.pack('<HHH',0,1,1)+struct.pack('<BBBBHHII',0,0,0,0,1,32,len(p),22)+p;(out/'InsectRealism.ico').write_bytes(ico)
 chunks=[]
 for tag,size in [(b'ic07',128),(b'ic08',256)]:
  payload=png(size);chunks.append(tag+struct.pack('>I',len(payload)+8)+payload)
 body=b''.join(chunks);(out/'InsectRealism.icns').write_bytes(b'icns'+struct.pack('>I',len(body)+8)+body)
 print('Generated original PNG, ICO and ICNS utility icons')
if __name__=='__main__':generate(pathlib.Path(__file__).resolve().parents[1]/'app/packaging/icons')
