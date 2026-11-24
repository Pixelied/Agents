"""Run an actual official Minecraft demo client in a disposable Xvfb world.

No account credentials, no multiplayer server, and no modifications to the
downloaded game. The tested mod is an already-built CI artifact.
"""
import concurrent.futures
import ctypes as C
import ctypes.util
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

root=Path("minecraft-smoke").resolve()
root.mkdir(exist_ok=True)
logs=Path("smoke-results").resolve()
logs.mkdir(exist_ok=True)
game=root/"game"
game.mkdir(exist_ok=True)
mods=game/"mods"
mods.mkdir(exist_ok=True)
lib=root/"libraries"
assets=root/"assets"
natives=root/"natives"
natives.mkdir(exist_ok=True)

def fetch(url,path=None,sha1=None):
    for attempt in range(3):
        try:
            if path is not None and path.exists():
                data=path.read_bytes()
                if sha1 is None or hashlib.sha1(data).hexdigest()==sha1:
                    return data
            req=urllib.request.Request(url,headers={"User-Agent":"FabricPackagingSmoke/1.0"})
            with urllib.request.urlopen(req,timeout=60) as response:
                data=response.read()
            if sha1 and hashlib.sha1(data).hexdigest()!=sha1:
                raise ValueError("SHA1 mismatch: "+url)
            if path is not None:
                path.parent.mkdir(parents=True,exist_ok=True)
                path.write_bytes(data)
            return data
        except Exception:
            if attempt==2:
                raise
            time.sleep(2**attempt)

def get_json(url):
    return json.loads(fetch(url))

def allowed(rules):
    if not rules:
        return True
    allow=False
    features={"is_demo_user":True,"has_custom_resolution":True}
    for rule in rules:
        os_rule=rule.get("os",{})
        if os_rule.get("name","linux")!="linux":
            continue
        if os_rule.get("arch") and os_rule["arch"] not in ("x86_64","amd64","x64"):
            continue
        if os_rule.get("version") and not re.search(os_rule["version"],platform.release()):
            continue
        if any(features.get(k,False)!=v for k,v in rule.get("features",{}).items()):
            continue
        allow=rule.get("action")=="allow"
    return allow

def maven_library(entry):
    parts=entry["name"].split(":")
    group,artifact,version=parts[:3]
    classifier="-"+parts[3] if len(parts)>3 else ""
    rel=group.replace(".","/")+"/"+artifact+"/"+version+"/"+artifact+"-"+version+classifier+".jar"
    url=entry.get("url","https://libraries.minecraft.net/").rstrip("/")+"/"+rel
    path=lib/rel
    fetch(url,path,entry.get("sha1"))
    return path

version="26.2"
manifest=get_json("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
item=next(x for x in manifest["versions"] if x["id"]==version)
meta=get_json(item["url"])
client=root/"client.jar"
fetch(meta["downloads"]["client"]["url"],client,meta["downloads"]["client"]["sha1"])
profile=get_json("https://meta.fabricmc.net/v2/versions/loader/26.2/0.19.5/profile/json")
cp=[]
for entry in profile.get("libraries",[]):
    if allowed(entry.get("rules")):
        cp.append(maven_library(entry))
for entry in meta["libraries"]:
    if not allowed(entry.get("rules")):
        continue
    dl=entry.get("downloads",{})
    artifact=dl.get("artifact")
    if artifact:
        path=lib/artifact["path"]
        fetch(artifact["url"],path,artifact.get("sha1"))
        cp.append(path)
    elif not dl:
        cp.append(maven_library(entry))
    classifier=entry.get("natives",{}).get("linux")
    if classifier:
        artifact=dl["classifiers"][classifier.replace("${arch}","64")]
        path=lib/artifact["path"]
        fetch(artifact["url"],path,artifact.get("sha1"))
        with zipfile.ZipFile(path) as z:
            for name in z.namelist():
                if name.endswith(".so") or ".so." in name:
                    (natives/Path(name).name).write_bytes(z.read(name))
cp.append(client)
index=meta["assetIndex"]
index_path=assets/"indexes"/(index["id"]+".json")
objects=json.loads(fetch(index["url"],index_path,index.get("sha1")))["objects"]
hashes=sorted({x["hash"] for x in objects.values()})
def download_asset(h):
    rel=h[:2]+"/"+h
    fetch("https://resources.download.minecraft.net/"+rel,assets/"objects"/rel,h)
print("Downloading official demo assets:",len(hashes),flush=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=24) as pool:
    for i,_ in enumerate(pool.map(download_asset,hashes),1):
        if i%500==0:
            print("Assets",i,"/",len(hashes),flush=True)
api_base="https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/"
versions=[e.text for e in ET.fromstring(fetch(api_base+"maven-metadata.xml")).findall("./versioning/versions/version") if e.text and e.text.endswith("+26.2")]
if not versions:
    raise RuntimeError("No official Fabric API for 26.2")
api="0.161.0+26.2"
assert api in versions, (api,versions[-5:])
api_file="fabric-api-"+api+".jar"
fetch(api_base+api+"/"+api_file,mods/api_file)
jars=list(Path("test-mod").glob("vape421-fabric-4.21-linux-x64.jar"))
assert len(jars)==1,jars
with zipfile.ZipFile(jars[0]) as z:
    assert json.loads(z.read("fabric.mod.json"))["version"] == "4.21-fabric.2"
    assert not any(n.startswith("org/lwjgl/") for n in z.namelist())
shutil.copyfile(jars[0],mods/jars[0].name)
(game/"options.txt").write_text("guiScale:2\nfullscreen:false\nonboardAccessibility:false\ntutorialStep:none\npauseOnLostFocus:false\n",encoding="utf-8")
replacements={
    "natives_directory":str(natives),"launcher_name":"FabricSmoke","launcher_version":"1",
    "classpath":os.pathsep.join(str(p) for p in cp),"classpath_separator":os.pathsep,
    "library_directory":str(lib),"version_name":version,"version_type":"release",
    "auth_player_name":"FabricSmoke","auth_uuid":"00000000000000000000000000000000",
    "auth_access_token":"0","auth_session":"0","user_type":"msa","user_properties":"{}",
    "auth_xuid":"0","clientid":"0","game_directory":str(game),
    "assets_root":str(assets),"assets_index_name":index["id"],
    "resolution_width":"854","resolution_height":"480"
}
def substitute(s):
    return re.sub(r"\$\{([^}]+)\}",lambda m:replacements.get(m[1],m[0]),s)
def args(values):
    result=[]
    for value in values:
        if isinstance(value,str):
            result.append(substitute(value))
        elif allowed(value.get("rules")):
            vs=value["value"]
            if isinstance(vs,str):
                vs=[vs]
            result.extend(substitute(s) for s in vs)
    return result
jvm=args(meta.get("arguments",{}).get("jvm",[]))+args(profile.get("arguments",{}).get("jvm",[]))
if "-cp" not in jvm and "-classpath" not in jvm:
    jvm.extend(["-cp",replacements["classpath"]])
game_args=args(meta["arguments"]["game"])+args(profile.get("arguments",{}).get("game",[]))
if "--demo" not in game_args:
    game_args.append("--demo")
if "--width" not in game_args:
    game_args+=["--width","854","--height","480"]
command=["java","-Xmx3g","--enable-native-access=ALL-UNNAMED"]+jvm+[profile["mainClass"]]+game_args
if any("${" in x for x in command):
    raise RuntimeError("Unresolved launcher arguments: "+repr([x for x in command if "${" in x]))
env={k:v for k,v in os.environ.items() if k not in ("GITHUB_TOKEN","GH_TOKEN","ACTIONS_RUNTIME_TOKEN","VAPE_ACCESS_TOKEN","VAPE_ONLINE_BASE_URL")}
env["LIBGL_ALWAYS_SOFTWARE"]="1"
console=logs/"client-console.log"
def read_log():
    return console.read_text(encoding="utf-8",errors="replace") if console.exists() else ""
# Use X11 directly; avoid installing xdotool/scrot via slow apt mirrors.
class DisplayProbe:
    def __init__(self):
        self.x=C.CDLL("libX11.so.6")
        self.xt=C.CDLL("libXtst.so.6")
        self.x.XOpenDisplay.argtypes=[C.c_char_p]; self.x.XOpenDisplay.restype=C.c_void_p
        self.d=self.x.XOpenDisplay(None)
        if not self.d:
            raise RuntimeError("Cannot open virtual X11 display")
        self.x.XSetInputFocus.argtypes=[C.c_void_p,C.c_ulong,C.c_int,C.c_ulong]
        self.x.XFlush.argtypes=[C.c_void_p]
        self.x.XDefaultRootWindow.argtypes=[C.c_void_p]; self.x.XDefaultRootWindow.restype=C.c_ulong
        self.x.XQueryTree.argtypes=[C.c_void_p,C.c_ulong,C.POINTER(C.c_ulong),C.POINTER(C.c_ulong),C.POINTER(C.POINTER(C.c_ulong)),C.POINTER(C.c_uint)]
        self.x.XFetchName.argtypes=[C.c_void_p,C.c_ulong,C.POINTER(C.c_void_p)]
        self.x.XFree.argtypes=[C.c_void_p]
        self.x.XGetGeometry.argtypes=[C.c_void_p,C.c_ulong,C.POINTER(C.c_ulong),C.POINTER(C.c_int),C.POINTER(C.c_int),C.POINTER(C.c_uint),C.POINTER(C.c_uint),C.POINTER(C.c_uint),C.POINTER(C.c_uint)]
        self.x.XTranslateCoordinates.argtypes=[C.c_void_p,C.c_ulong,C.c_ulong,C.c_int,C.c_int,C.POINTER(C.c_int),C.POINTER(C.c_int),C.POINTER(C.c_ulong)]
        self.x.XKeysymToKeycode.argtypes=[C.c_void_p,C.c_ulong]; self.x.XKeysymToKeycode.restype=C.c_ubyte
        self.xt.XTestFakeMotionEvent.argtypes=[C.c_void_p,C.c_int,C.c_int,C.c_int,C.c_ulong]
        self.xt.XTestFakeButtonEvent.argtypes=[C.c_void_p,C.c_uint,C.c_int,C.c_ulong]
        self.xt.XTestFakeKeyEvent.argtypes=[C.c_void_p,C.c_uint,C.c_int,C.c_ulong]
    def window(self):
        root=self.x.XDefaultRootWindow(self.d)
        candidates=[]
        def walk(win,depth):
            if depth>8:
                return
            name=C.c_void_p()
            if self.x.XFetchName(self.d,win,C.byref(name)) and name.value:
                title=C.string_at(name).decode("utf-8",errors="replace")
                self.x.XFree(name)
                if "minecraft" in title.lower():
                    r=C.c_ulong(); x=C.c_int(); y=C.c_int(); w=C.c_uint(); h=C.c_uint(); b=C.c_uint(); d=C.c_uint()
                    if self.x.XGetGeometry(self.d,win,C.byref(r),C.byref(x),C.byref(y),C.byref(w),C.byref(h),C.byref(b),C.byref(d)):
                        rx=C.c_int(); ry=C.c_int(); child=C.c_ulong()
                        self.x.XTranslateCoordinates(self.d,win,root,0,0,C.byref(rx),C.byref(ry),C.byref(child))
                        if w.value>=300 and h.value>=200:
                            candidates.append((win,rx.value,ry.value,w.value,h.value))
            r=C.c_ulong(); p=C.c_ulong(); children=C.POINTER(C.c_ulong)(); count=C.c_uint()
            if self.x.XQueryTree(self.d,win,C.byref(r),C.byref(p),C.byref(children),C.byref(count)):
                ids=[children[i] for i in range(count.value)]
                if children:
                    self.x.XFree(children)
                for child in ids:
                    walk(child,depth+1)
        walk(root,0)
        if not candidates:
            raise RuntimeError("Minecraft window not found")
        return max(candidates,key=lambda item:item[3]*item[4])
    def click(self,rx,ry):
        win,x,y,w,h=self.window()
        self.x.XSetInputFocus(self.d,win,2,0)
        self.xt.XTestFakeMotionEvent(self.d,-1,x+int(w*rx),y+int(h*ry),0)
        self.xt.XTestFakeButtonEvent(self.d,1,1,0)
        self.xt.XTestFakeButtonEvent(self.d,1,0,0)
        self.x.XFlush(self.d)
    def key(self,keysym):
        win,*_=self.window()
        self.x.XSetInputFocus(self.d,win,2,0)
        code=self.x.XKeysymToKeycode(self.d,keysym)
        self.xt.XTestFakeKeyEvent(self.d,code,1,0)
        self.xt.XTestFakeKeyEvent(self.d,code,0,0)
        self.x.XFlush(self.d)
probe=DisplayProbe()
def shot(name):
    try:
        subprocess.run(["java",str(Path(__file__).resolve().parent/"CaptureScreen.java"),str(logs/name)],check=True,timeout=12)
    except Exception as e:
        print("Screenshot failed:",e,flush=True)
def wait_log(marker,timeout,proc):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        content=read_log()
        if marker in content:
            return True
        if proc.poll() is not None:
            return False
        time.sleep(1)
    return False
print("Launching official 26.2 demo with Fabric API",api,flush=True)
with console.open("wb") as out:
    proc=subprocess.Popen(command,cwd=game,env=env,stdout=out,stderr=subprocess.STDOUT)
    try:
        assert wait_log("[Vape421] Fabric entrypoint registered",120,proc),"Fabric entrypoint was not registered"
        print("PASS: real Minecraft Fabric entrypoint registered",flush=True)
        assert wait_log("Sound engine started",120,proc),"Game did not initialize audio and reach resource loading"
        print("PASS: real Minecraft OpenAL sound engine started",flush=True)
        time.sleep(8)
        if proc.poll() is not None:
            raise RuntimeError("Client exited before main menu")
        print("PASS: real Minecraft survived startup after OpenAL initialization",flush=True)
        shot("title-screen.png")
        probe.click(0.5,236/480)
        time.sleep(10)
        shot("after-demo-click.png")
        if "Preparing spawn" not in read_log() and "Starting integrated minecraft server" not in read_log() and "[Vape421] Fabric initialization completed" not in read_log():
            probe.click(0.5,264/480)
        if not wait_log("[Vape421] Fabric initialization completed",100,proc):
            shot("world-or-failure.png")
            if "[Vape421] initialization failed" in read_log() or "[Vape421] Fabric startup failed" in read_log():
                raise RuntimeError("Recovered runtime failed during world startup")
            raise RuntimeError("World initialization not confirmed; inspect screenshot and log")
        print("PASS: recovered runtime initialized in a real demo world",flush=True)
        shot("world-before-gui.png")
        probe.key(0xffe2)
        time.sleep(5)
        shot("right-shift-result.png")
        if proc.poll() is not None:
            raise RuntimeError("Game exited after Right Shift")
        assert "ClickGUI 26.2: render callback queued" in read_log(), "Right Shift did not produce the original GUI render callback"
        print("PASS: actual original ClickGUI render callback fired after Right Shift",flush=True)
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=15)
        except subprocess.TimeoutExpired:
            proc.kill()
        latest=game/"logs/latest.log"
        if latest.exists():
            shutil.copyfile(latest,logs/"latest.log")
        print(read_log()[-16000:],flush=True)
