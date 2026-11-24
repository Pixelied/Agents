import hashlib
import json
import shutil
import subprocess
import sys
from pathlib import Path

here = Path(__file__).resolve().parent
target = Path(sys.argv[1]).resolve()
source_commit = "5a6ff6ca04c62f32aebf6eee0c983c1a1a5602c4"
actual = subprocess.check_output(["git", "-C", str(target), "rev-parse", "HEAD"], text=True).strip()
assert actual == source_commit, (actual, source_commit)
def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
java_root = target / "src/main/java"
baseline = {p.relative_to(target).as_posix(): digest(p) for p in java_root.rglob("*.java")}
changes = json.loads((here / "changes.json").read_text(encoding="utf-8"))
for rel, replacements in changes.items():
    path = target / rel
    text = path.read_bytes().decode("utf-8")
    for old, new in replacements:
        assert text.count(old) == 1, (rel, old, text.count(old))
        text = text.replace(old, new, 1)
    path.write_bytes(text.encode("utf-8"))
for path in (here / "overlay").rglob("*"):
    if path.is_file():
        dest = target / path.relative_to(here / "overlay")
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, dest)
gradle = target / "build.gradle"
original = gradle.read_text(encoding="utf-8")
base = original.split("tasks.register('injectionJar'")[0]
base = base.replace("    compileOnly 'org.jetbrains:annotations:24.1.0'", "    compileOnly 'net.fabricmc:fabric-loader:0.18.4'\n    compileOnly 'org.lwjgl:lwjgl-glfw:3.3.6'\n    compileOnly 'org.lwjgl:lwjgl:3.3.6'\n    compileOnly 'org.jetbrains:annotations:24.1.0'")
base = base.replace("options.release = (project.findProperty('targetRelease') ?: '17') as int", "options.release = 17")
gradle.write_bytes((base + (here / "fabric-build.gradle").read_text(encoding="utf-8")).encode("utf-8"))
expected = {
    "src/main/java/gg/vape/Vape.java": "584a91c14fff23933dcefe314e168cd95626fb5054ca57dc15ee86133b46f209",
    "src/main/java/gg/vape/runtime/NativeBridge.java": "b531dc812e2849f16c37915bf4b40eda17a0f2ff64fffd26bdb711791c2c05cd",
    "src/main/java/gg/vape/sync/SyncThread.java": "84fd8e31a3c918b745ae5ccc07046397a03318c635250a1d64f07d7714bc06e5",
    "src/main/java/gg/vape/fabric/FabricClient.java": "92fb6598cd00686ffa4dbb4073fbd83a5981f91997a6641714dd37324e622d96",
    "src/main/java/gg/vape/fabric/FabricInput.java": "8d85389467f53d7f1706d5b193c17af4f4180cda11f021395de3da90a52bfc9f",
    "src/main/java/gg/vape/fabric/InProcessBytecode.java": "2c33372bc3e47db8c4e85613698fec33749c32325b99c493f460211f0a2f1ed9",
    "fabric-native/bridge.c": "856f6179f47907c9a5dcf575149696d71b6b1dcfe5676aa5059c6a8c15b1117d",
}
for rel, sha in expected.items():
    assert digest(target / rel) == sha, (rel, digest(target / rel), sha)
for rel, sha in baseline.items():
    if rel not in changes:
        assert digest(target / rel) == sha, rel
print(f"Verified {len(baseline) - len(changes)} unchanged original Java source files")
shutil.rmtree(target / "native", ignore_errors=True)
