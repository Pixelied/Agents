"""Run the native-only diagnostic in the existing official demo launcher.
The original client, gameplay mod, and personal configuration are never loaded.
"""
from pathlib import Path
import os,sys,subprocess
original=Path(__file__).resolve().parent.parent/"minecraft_smoke.py"
text=original.read_text()
a=text.index('jars=list(Path("test-mod")')
b=text.index("replacements={",a)
c=text.index('print("Launching official 26.2 demo',b)
build=r'''
# Compile the diagnostic against the actual downloaded runtime and nested API jars.
source_dir=Path(__file__).resolve().parent/"runtime-verification"
compile_libs=[mods/api_file]
with zipfile.ZipFile(mods/api_file) as z:
    for name in z.namelist():
        if name.startswith("META-INF/jars/") and name.endswith(".jar"):
            p=root/"api-jars"/Path(name).name;p.parent.mkdir(exist_ok=True);p.write_bytes(z.read(name));compile_libs.append(p)
classes=root/"probe-classes";classes.mkdir(exist_ok=True)
for cls in ["net.minecraft.client.gui.screens.Screen","net.minecraft.client.gui.Gui","net.minecraft.client.gui.components.EditBox","com.mojang.blaze3d.platform.Window"]:
    report=subprocess.run(["javap","-p","-c","-l","-classpath",str(client),cls],capture_output=True,text=True,check=True)
    (logs/(cls+".txt")).write_text(report.stdout)

subprocess.run(["javac","-proc:none","--release","25","-cp",os.pathsep.join(map(str,cp+compile_libs)),"-d",str(classes),str(source_dir/"UniversalMesh.java"),str(source_dir/"BatchGeometry.java"),str(source_dir/"NativeGuiBatchRenderer.java"),*[str(p) for p in sorted((source_dir/"preview").glob("*.java"))]],check=True)
subprocess.run(["jar","--create","--file",str(mods/"native-api-smoke.jar"),"-C",str(classes),".","-C",str(source_dir/"resources"),"assets","-C",str(source_dir/"preview-resources"),"."],check=True)
shutil.copyfile(mods/"native-api-smoke.jar",logs/"native-api-smoke.jar")
backend=os.environ["PROBE_BACKEND"]
assert backend in ("opengl","vulkan")
with zipfile.ZipFile(client) as z:
    data_version=json.loads(z.read("version.json"))["world_version"]
assert isinstance(data_version,int) and data_version>0
# Options.load applies historical migrations before decoding the fields. Never
# let a synthetic current-version options fixture masquerade as a legacy file.
options="version:"+str(data_version)+"\nguiScale:2\nfullscreen:false\nonboardAccessibility:false\ntutorialStep:none\npauseOnLostFocus:false\nrenderDistance:5\nsimulationDistance:5\nmaxFps:30\npreferredGraphicsBackend:"+json.dumps(backend)+"\n"
(game/"options.txt").write_text(options,encoding="utf-8")
(logs/"initial-options.txt").write_text(options,encoding="utf-8")
fix=subprocess.run(["javap","-p","-c","-classpath",str(client),"net.minecraft.util.datafix.fixes.OptionsForceDefaultGraphicsApiFix"],check=True,capture_output=True,text=True)
(logs/"graphics-option-migration.txt").write_text(fix.stdout)
print("Verified current options data version",data_version,"and requested backend",backend,flush=True)
print("PASS: native probe compiled against official client and Fabric API",flush=True)
'''
tail=r'''
command.insert(1,"-Dvape421.preview.ci=true")
print("Launching native Vulkan preview",flush=True)
with console.open("wb") as out:
    proc=subprocess.Popen(command,cwd=game,env=env,stdout=out,stderr=subprocess.STDOUT)
    try:
        assert wait_log("[VapeVulkanPreview] REGISTERED",120,proc),"entrypoint registration failed"
        assert wait_log("minecraft:textures/atlas/gui.png-atlas",90,proc),"resource initialization failed"
        time.sleep(8)
        shot("title-screen.png")
        probe.click(0.5,236/480)
        time.sleep(6)
        if "Preparing spawn" not in read_log() and "Starting integrated minecraft server" not in read_log() and "[VapeVulkanPreview] BACKEND" not in read_log():probe.click(0.5,264/480)
        assert wait_log("[VapeVulkanPreview] HUD_DRAW",100,proc),"native preview HUD did not render"
        assert "[VapeVulkanPreview] BACKEND Vulkan" in read_log(),"incorrect backend"
        print("PASS: real Vulkan HUD extraction",flush=True)
        probe.key(0xff1b);time.sleep(2)
        def shift():
            win,*_=probe.window()
            probe.x.XSetInputFocus(probe.d,win,2,0)
            code=probe.x.XKeysymToKeycode(probe.d,0xffe2)
            probe.xt.XTestFakeKeyEvent(probe.d,code,1,0);probe.x.XFlush(probe.d);time.sleep(.5)
            probe.xt.XTestFakeKeyEvent(probe.d,code,0,0);probe.x.XFlush(probe.d)
        shift()
        assert wait_log("[VapeVulkanPreview] MENU_DRAW",30,proc),"Right Shift menu failed"
        dims=re.search(r"\[VapeVulkanPreview\] MENU_DRAW (\d+) (\d+) (\-?\d+) (\-?\d+) (\d+) (\d+)",read_log());assert dims
        gw,gh,x,y,pw,ph=map(int,dims.groups())
        time.sleep(2);shot("preview-menu.png")
        print("PASS: interactive native GUI opened using Right Shift",flush=True)
        probe.click((x+30)/gw,(y+44)/gh)
        assert wait_log("[VapeVulkanPreview] COORDS false",20,proc),"coordinates button did not change"
        assert "coordinates=false" in (game/"config/vape421/vulkan-preview.properties").read_text()
        probe.click((x+30)/gw,(y+71)/gh)
        for ch in "scoreboard":
            probe.key(ord(ch));time.sleep(.08)
        assert wait_log("[VapeVulkanPreview] SEARCH results=1",20,proc),"search did not narrow results"
        time.sleep(2);shot("preview-search.png")
        probe.key(0xff1b);time.sleep(2)
        (game/"preview.reload").touch()
        assert wait_log("[VapeVulkanPreview] RELOAD_OK",120,proc),"resource reload failed"
        shift();time.sleep(3);shot("preview-reloaded.png")
        assert read_log().count("[VapeVulkanPreview] MENU_DRAW")>=2,"reopen after reload failed"
        (game/"preview.stop").touch()
        assert proc.wait(timeout=60)==0,"Minecraft did not exit normally"
        assert "[VapeVulkanPreview] STOP" in read_log()
        assert "[VapeVulkanPreview] DISABLED" not in read_log()
        print("PASS: interactive controls, search, persistence, reload, reopen and clean shutdown",flush=True)
    finally:
        if proc.poll() is None:
            proc.terminate()
            try:proc.wait(timeout=15)
            except subprocess.TimeoutExpired:proc.kill();proc.wait()
        latest=game/"logs/latest.log"
        if latest.exists():shutil.copyfile(latest,logs/"latest.log")
        print(read_log()[-18000:],flush=True)
'''
generated=original.with_name(".native-preview-generated.py")
generated.write_text(text[:a]+build+text[b:c]+tail)
try:
    subprocess.run([sys.executable,"-u",str(generated)],check=True)
finally:
    generated.unlink(missing_ok=True)
