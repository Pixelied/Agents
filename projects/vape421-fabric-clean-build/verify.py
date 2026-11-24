import hashlib
import json
import subprocess
import sys
import zipfile
from pathlib import Path
root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
jars = list((root / "build/libs").glob("vape421-fabric-4.21-*.jar"))
assert len(jars) == 1, jars
jar = jars[0]
with zipfile.ZipFile(jar) as z:
    assert z.testzip() is None
    entries = z.namelist()
    meta = json.loads(z.read("fabric.mod.json"))
    assert meta["entrypoints"]["client"] == ["gg.vape.fabric.FabricClient"]
    assert meta["depends"]["minecraft"] == "26.2"
    assert meta["version"] == "4.21-fabric.2"
    forbidden=[n for n in entries if n.startswith(("org/lwjgl/","net/java/games/","org/apache/commons/io/")) or (not n.startswith("fabric-native/") and n.lower().endswith((".dll",".so",".dylib",".jnilib")))]
    assert not forbidden, forbidden
    print("PASS: zero LWJGL/JInput/Commons IO collisions or legacy native binaries")
    assert len([n for n in entries if n.startswith("fabric-native/") and not n.endswith("/")]) == 1
    assert not any(n.endswith(".exe") for n in entries)
    assert "gg/vape/Vape.class" in entries
    assert "gg/vape/fabric/InProcessBytecode.class" in entries
    print("Archive verified:", jar.name, "entries", len(entries))
subprocess.run(["java", "--enable-native-access=ALL-UNNAMED", "-cp", str(jar), str(here / "BridgeSmoke.java")], check=True)
sha = hashlib.sha256(jar.read_bytes()).hexdigest()
jar.with_suffix(".jar.sha256").write_text(f"{sha}  {jar.name}\n", encoding="utf-8")
print("SHA256", sha)
