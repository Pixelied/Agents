"""Obtain public Gson and Guava libraries from the official Minecraft 26.2 metadata.
The Minecraft executable and game assets are not downloaded or redistributed.
"""
import pathlib,urllib.request,json,hashlib
p=pathlib.Path("ux-test-deps");p.mkdir(exist_ok=True)
def get(url):
    with urllib.request.urlopen(url,timeout=60) as response:return response.read()
manifest=json.loads(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
meta=json.loads(get(next(v for v in manifest["versions"] if v["id"]=="26.2")["url"]))
report={}
for entry in meta["libraries"]:
    name=entry["name"]
    if not (name.startswith("com.google.code.gson:gson:") or name.startswith("com.google.guava:")):continue
    a=entry.get("downloads",{}).get("artifact")
    if not a:continue
    data=get(a["url"])
    assert hashlib.sha1(data).hexdigest()==a["sha1"],name
    out=p/pathlib.Path(a["path"]).name;out.write_bytes(data)
    report[out.name]={"coordinate":name,"sha1":a["sha1"],"sha256":hashlib.sha256(data).hexdigest(),"url":a["url"]}
    print("Verified public library",name,a["sha1"],flush=True)
assert any(k.startswith("gson-") for k in report)
assert any(k.startswith("guava-") for k in report)
(p/"manifest.json").write_text(json.dumps(report,indent=2)+"\n")
