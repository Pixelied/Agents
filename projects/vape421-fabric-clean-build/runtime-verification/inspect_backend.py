"""Inspect only the official backend option encoding; no game binary is published."""
import hashlib,json,pathlib,subprocess,urllib.request,zipfile
def get(url):
    with urllib.request.urlopen(url,timeout=60) as r:return r.read()
manifest=json.loads(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
meta=json.loads(get(next(v for v in manifest["versions"] if v["id"]=="26.2")["url"]))
item=meta["downloads"]["client"];data=get(item["url"])
assert item["sha1"]=="2dc72797acbc1b63fc16a11c4ac393605f453754"
assert hashlib.sha1(data).hexdigest()==item["sha1"]
p=pathlib.Path("official-client.jar");p.write_bytes(data)
out=pathlib.Path("backend-report");out.mkdir(exist_ok=True)
print("PASS official client SHA-1",item["sha1"],flush=True)
for name in ["net.minecraft.client.PreferredGraphicsApi","net.minecraft.client.Options"]:
    result=subprocess.run(["javap","-p","-c","-classpath",str(p),name],capture_output=True,text=True,check=True)
    (out/(name+".txt")).write_text(result.stdout)
    if name.endswith("PreferredGraphicsApi"):print(result.stdout,flush=True)
    else:
        lines=result.stdout.splitlines()
        hits={j for i,l in enumerate(lines) if "preferredGraphics" in l for j in range(max(0,i-7),min(len(lines),i+9))}
        print("\n".join(lines[j] for j in sorted(hits)),flush=True)
with zipfile.ZipFile(p) as z:
    names=[n for n in z.namelist() if n.endswith(".class") and any(w in n.lower() for w in ["graphicsapi","graphicsbackend","vulkandevice","gpudevice","backendselection"])]
    print("Backend implementation classes:",*names,sep="\n",flush=True)
    (out/"backend-classes.txt").write_text("\n".join(names))
p.unlink()
