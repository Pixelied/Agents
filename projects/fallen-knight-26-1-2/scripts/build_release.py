from __future__ import annotations

import argparse
import pathlib
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
DP = ROOT / "datapacks" / "fallen_knight"
RP = ROOT / "resourcepacks" / "fallen_knight"
VERSION = "26.1.2"


def add_tree(zf: zipfile.ZipFile, root: pathlib.Path) -> None:
    for path in sorted(root.rglob("*")):
        if path.is_file() and path.name != ".DS_Store" and "__pycache__" not in path.parts and path.suffix != ".pyc":
            zf.write(path, path.relative_to(root).as_posix())


def build_pack(root: pathlib.Path, destination: pathlib.Path) -> None:
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        add_tree(zf, root)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=pathlib.Path, default=ROOT / "dist")
    args = parser.parse_args()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)

    # Structure NBT is deterministic generated source. Generate it fresh so binary
    # transfer/storage cannot corrupt the release.
    subprocess.run([sys.executable, str(ROOT / "scripts" / "generate_structures.py")], cwd=ROOT, check=True)

    dp_name = f"Fallen-Knight-Datapack-{VERSION}.zip"
    rp_name = f"Fallen-Knight-Resource-Pack-{VERSION}.zip"
    bundle_name = f"Fallen-Knight-{VERSION}.zip"
    dp_zip = out / dp_name
    rp_zip = out / rp_name

    build_pack(DP, dp_zip)
    build_pack(RP, rp_zip)

    install = f"""FALLEN KNIGHT - MINECRAFT JAVA {VERSION}\n\n1. Put {dp_name} directly in your world's datapacks folder. Do not unzip it.\n2. Put {rp_name} directly in your Minecraft resourcepacks folder. Do not unzip it.\n3. Enable the Fallen Knight resource pack in-game.\n4. For the custom Fallen Knight appearance, install both Entity Model Features (EMF) and Entity Texture Features (ETF) on the client.\n5. Enter the world and run /reload.\n6. Quick test: /function fallen_knight:debug/start_test_fight\n7. Reset the quick test: /function fallen_knight:debug/cleanup_test_fights\n\nDo not call functions under fallen_knight:arena/* directly; several are internal macro functions and require arguments.\n"""

    bundle = out / bundle_name
    with zipfile.ZipFile(bundle, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.write(dp_zip, dp_name)
        zf.write(rp_zip, rp_name)
        zf.writestr("INSTALL.txt", install)

    print(dp_zip)
    print(rp_zip)
    print(bundle)


if __name__ == "__main__":
    main()
