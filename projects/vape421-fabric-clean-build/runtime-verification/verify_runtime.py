"""Download official pinned runtime and compile the native GUI API contract.
No game binaries are committed or published. Only public signatures and logs are retained.
"""
import hashlib,json,os,pathlib,subprocess,urllib.request,zipfile
from concurrent.futures import ThreadPoolExecutor
P=pathlib.Path
work=P("runtime-verification").resolve();work.mkdir(exist_ok=True)
report=P("runtime-api-report").resolve();report.mkdir(exist_ok=True)
def get(url,path=None,sha=None):
    for attempt in range(3):
        try:
            if path and path.exists(): data=path.read_bytes()
            else:
                with urllib.request.urlopen(urllib.request.Request(url,headers={"User-Agent":"NativeGuiVerification/1"}),timeout=45) as r:data=r.read()
            if sha and hashlib.sha1(data).hexdigest()!=sha:raise RuntimeError("SHA-1 mismatch: "+url)
            if path:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
            return data
        except Exception:
            if path and path.exists():path.unlink()
            if attempt==2:raise
manifest=json.loads(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
meta=json.loads(get(next(v for v in manifest["versions"] if v["id"]=="26.2")["url"]))
artifact=meta["downloads"]["client"]
client=work/"client.jar"
assert artifact["sha1"]=="2dc72797acbc1b63fc16a11c4ac393605f453754", artifact
get(artifact["url"],client,artifact["sha1"])
print("PASS official Minecraft 26.2 client SHA-1:",artifact["sha1"],"bytes:",client.stat().st_size,flush=True)
profile=json.loads(get("https://meta.fabricmc.net/v2/versions/loader/26.2/0.19.5/profile/json"))
def allowed(rules):
    if not rules:return True
    result=False
    for r in rules:
        o=r.get("os",{})
        if o.get("name","linux")!="linux" or o.get("arch","x86_64") not in ("x86_64","amd64","x64"):continue
        if r.get("features"):continue
        result=r["action"]=="allow"
    return result
entries=[]
for e in meta["libraries"]+profile.get("libraries",[]):
    if not allowed(e.get("rules")):continue
    if e.get("downloads",{}).get("artifact"):
        a=e["downloads"]["artifact"];entries.append((a["url"],work/"libs"/a["path"],a.get("sha1")))
    elif not e.get("downloads"):
        g,a,v,*c=e["name"].split(":");rel=g.replace(".","/")+"/"+a+"/"+v+"/"+a+"-"+v+("-"+c[0] if c else "")+".jar"
        entries.append((e.get("url","https://libraries.minecraft.net/").rstrip("/")+"/"+rel,work/"libs"/rel,e.get("sha1")))
def download(item):
    u,p,h=item;get(u,p,h);return p
with ThreadPoolExecutor(max_workers=12) as pool:libs=list(pool.map(download,entries))
api="0.161.0+26.2";api_rel="net/fabricmc/fabric-api/fabric-api/"+api+"/fabric-api-"+api+".jar";url="https://maven.fabricmc.net/"+api_rel
api_jar=work/"fabric-api.jar";get(url,api_jar,get(url+".sha1").decode().strip().split()[0])
with zipfile.ZipFile(api_jar) as z:
    for name in z.namelist():
        if name.startswith("META-INF/jars/") and name.endswith(".jar"):
            out=work/"api"/P(name).name;out.parent.mkdir(exist_ok=True);out.write_bytes(z.read(name));libs.append(out)
print("PASS Fabric Loader 0.19.5 profile and Fabric API",api,"with",len(libs),"runtime dependency JARs",flush=True)
cp=os.pathsep.join(map(str,[client,api_jar,*libs]))
classes=[
"net.minecraft.client.Minecraft","net.minecraft.resources.Identifier","net.minecraft.client.gui.GuiGraphicsExtractor",
"net.minecraft.client.gui.Font","net.minecraft.client.renderer.RenderPipelines","com.mojang.blaze3d.systems.RenderSystem",
"com.mojang.blaze3d.systems.GpuDevice","com.mojang.blaze3d.systems.GpuDeviceInfo","com.mojang.blaze3d.platform.NativeImage",
"net.minecraft.client.renderer.texture.DynamicTexture","net.minecraft.client.renderer.texture.AbstractTexture","net.minecraft.client.renderer.texture.TextureManager",
"net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry","net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement","net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements",
"net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents","net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents$EndTick","net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents$ClientStopping","net.fabricmc.fabric.api.event.Event",
"com.mojang.blaze3d.pipeline.RenderPipeline","com.mojang.blaze3d.pipeline.RenderPipeline$Builder","net.minecraft.client.gui.render.state.GuiRenderState","net.minecraft.client.gui.render.state.GuiElementRenderState","com.mojang.blaze3d.vertex.VertexFormat","com.mojang.blaze3d.vertex.VertexFormatElement","com.mojang.blaze3d.systems.CommandEncoder","com.mojang.blaze3d.systems.RenderPass","net.minecraft.client.Options"]
for name in classes:
    r=subprocess.run(["javap","-classpath",cp,"-public",name],capture_output=True,text=True)
    (report/(name+".txt")).write_text(r.stdout+r.stderr)
    print("SIGNATURE",name,"available" if r.returncode==0 else "not found",flush=True)
source=P(__file__).with_name("NativeContract.java")
p=subprocess.run(["javac","-proc:none","--release","25","-classpath",cp,"-d",str(work/"classes"),str(source)],capture_output=True,text=True)
(report/"compile.txt").write_text(p.stdout+p.stderr)
print(p.stdout+p.stderr,flush=True)
if p.returncode==0:print("PASS: native API contract compiled against exact official 26.2 binaries",flush=True)
else:print("FAIL: native API contract does not compile; see signatures and compile diagnostics",flush=True)
(report/"runtime.json").write_text(json.dumps({"minecraft":"26.2","client_sha1":artifact["sha1"],"loader":"0.19.5","fabric_api":api,"compile_exit":p.returncode},indent=2))
raise SystemExit(p.returncode)
