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
subprocess.run(["javac","-proc:none","--release","25","-cp",os.pathsep.join(map(str,cp+compile_libs)),"-d",str(classes),str(source_dir/"NativeApiSmoke.java")],check=True)
subprocess.run(["jar","--create","--file",str(mods/"native-api-smoke.jar"),"-C",str(classes),".","-C",str(source_dir/"resources"),"."],check=True)
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
print("Launching native rendering diagnostic on",backend,flush=True)
with console.open("wb") as out:
    proc=subprocess.Popen(command,cwd=game,env=env,stdout=out,stderr=subprocess.STDOUT)
    try:
        assert wait_log("[NativeApiSmoke] REGISTERED",120,proc),"native HUD registration failed"
        assert wait_log("minecraft:textures/atlas/gui.png-atlas",90,proc),"GUI resource initialization failed"
        time.sleep(8)
        shot("title-screen.png")
        probe.click(0.5,236/480)
        time.sleep(6)
        if "Preparing spawn" not in read_log() and "Starting integrated minecraft server" not in read_log() and "[NativeApiSmoke] BACKEND" not in read_log():probe.click(0.5,264/480)
        assert wait_log("[NativeApiSmoke] DRAW",100,proc),"native HUD did not submit its first frame"
        preference=re.search(r"\[NativeApiSmoke\] PREFERENCE ([^\r\n]+)",read_log())
        assert preference and preference.group(1).strip()==backend,("Preference was changed by Minecraft",preference.group(1) if preference else None)
        print("PASS: options preference retained",preference.group(1),flush=True)
        actual=re.search(r"\[NativeApiSmoke\] BACKEND ([^\r\n]+)",read_log())
        assert actual and backend in actual.group(1).lower(),("Backend fallback or mismatch",actual.group(1) if actual else None)
        print("PASS: real active GPU backend",actual.group(1),flush=True)
        dims=re.search(r"\[NativeApiSmoke\] DRAW (\d+) (\d+)",read_log());assert dims
        phases=set()
        for attempt in range(10):
            assert proc.poll() is None,"Minecraft exited during pixel verification"
            name=f"probe-{attempt}.png";shot(name)
            _,x,y,w,h=probe.window()
            p=subprocess.run(["java",str(source_dir/"CheckPixels.java"),str(logs/name),str(x),str(y),str(w),str(h),*dims.groups()],text=True,capture_output=True,timeout=20)
            print(p.stdout+p.stderr,flush=True)
            if p.returncode==0:
                phases.add(re.search(r"PHASE=(\d)",p.stdout).group(1))
                if len(phases)==2:break
            elif attempt==0:
                probe.key(0xff1b)
            time.sleep(1.2)
        assert phases=={"0","1"},"native rendering pixel/animation verification incomplete"
        assert "[NativeApiSmoke] UPLOAD 2" in read_log(),"dynamic texture update not observed"
        assert proc.poll() is None
        print("PASS: Minecraft actually drew the standard rectangle, custom fragment shader, nested clips and both dynamic texture phases on",backend,flush=True)
        # Quit with standard game window close; no forced JVM kill during normal path.
        probe.key(0xff1b)
        time.sleep(1)
        shot("final-screen.png")
    finally:
        proc.terminate()
        try:proc.wait(timeout=15)
        except subprocess.TimeoutExpired:proc.kill();proc.wait()
        latest=game/"logs/latest.log"
        if latest.exists():shutil.copyfile(latest,logs/"latest.log")
        if (game/"options.txt").exists():shutil.copyfile(game/"options.txt",logs/"final-options.txt")
        print(read_log()[-18000:],flush=True)
'''
# The verified launcher source owns the downloader and X11 test helpers.
generated=original.with_name(".native-api-smoke-generated.py")
generated.write_text(text[:a]+build+text[b:c]+tail)
try:
    subprocess.run([sys.executable,"-u",str(generated)],check=True)
finally:
    generated.unlink(missing_ok=True)
