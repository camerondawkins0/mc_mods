"""The Shangri-La Frontier modpack: the mod plus a few client companions, as a Modrinth .mrpack.

  python3 tools/modpack.py resolve   # pin the newest compatible companion versions (needs network)
  python3 tools/modpack.py build     # zip build/modpack/*.mrpack from modpack/pack.json + build/libs

The .mrpack imports into the Modrinth App, Prism Launcher, ATLauncher and others. Companions are
downloaded by the launcher from Modrinth's CDN (only their pinned hashes are stored here); the
Frontier jar itself rides along as an override. Companions are client-only, so the same pack
also sets up a dedicated server with just the mod.
"""
import glob
import hashlib
import json
import os
import sys
import urllib.parse
import urllib.request
import zipfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
PACK = os.path.join(ROOT, "modpack")
API = "https://api.modrinth.com/v2"

# (Modrinth project slug, install folder, required?, loaders to match, why it's in the pack)
COMPANIONS = [
    ("sodium", "mods", True, ["neoforge"], "Faster rendering; needed by Iris."),
    ("iris", "mods", True, ["neoforge"], "Shader pack loader."),
    ("complementary-reimagined", "shaderpacks", True, ["iris"], "The shader pack the pack switches on."),
    ("distanthorizons", "mods", False, ["neoforge"], "Optional: see the designed continent to the horizon."),
]


def props():
    out = {}
    for line in open(os.path.join(ROOT, "gradle.properties")):
        if "=" in line and not line.lstrip().startswith("#"):
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip()
    return out


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "camerondawkins0/mc_mods shangri-la-frontier modpack"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def resolve():
    p = props()
    files = []
    for slug, folder, required, loaders, why in COMPANIONS:
        q = urllib.parse.urlencode({"game_versions": json.dumps([p["minecraft_version"]]), "loaders": json.dumps(loaders)})
        versions = [v for v in get(f"{API}/project/{slug}/version?{q}") if v["version_type"] == "release"]
        if not versions:
            raise SystemExit(f"no release of {slug} for {p['minecraft_version']} / {loaders}")
        v = versions[0]
        f = next((f for f in v["files"] if f["primary"]), v["files"][0])
        files.append({
            "path": f"{folder}/{f['filename']}",
            "hashes": {"sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"]},
            "env": {"client": "required" if required else "optional", "server": "unsupported"},
            "downloads": [f["url"]],
            "fileSize": f["size"],
            "_project": slug, "_version": v["version_number"], "_why": why,
        })
        print(f"{slug}: {v['version_number']} -> {f['filename']}")
    json.dump({"files": files}, open(os.path.join(PACK, "pack.json"), "w"), indent=2)
    shader = next(f for f in files if f["_project"] == "complementary-reimagined")
    with open(os.path.join(PACK, "overrides/config/iris.properties"), "w") as out:
        out.write("# Written by the Shangri-La Frontier modpack: switch the bundled shader pack on.\n")
        out.write("enableShaders=true\n")
        out.write(f"shaderPack={os.path.basename(shader['path'])}\n")


def build():
    p = props()
    jars = [j for j in glob.glob(os.path.join(ROOT, "build/libs/*.jar")) if not j.endswith(("-sources.jar", "-javadoc.jar"))]
    if not jars:
        raise SystemExit("build the mod first (./gradlew build)")
    jar = max(jars, key=os.path.getmtime)
    pinned = json.load(open(os.path.join(PACK, "pack.json")))["files"]
    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": p["mod_version"],
        "name": "Shangri-La Frontier",
        "summary": "The Shangri-La Frontier mod with shaders and a faster renderer, ready to play.",
        "files": [{k: v for k, v in f.items() if not k.startswith("_")} for f in pinned],
        "dependencies": {"minecraft": p["minecraft_version"], "neoforge": p["neo_version"]},
    }
    os.makedirs(os.path.join(ROOT, "build/modpack"), exist_ok=True)
    out = os.path.join(ROOT, f"build/modpack/Shangri-La-Frontier-{p['mod_version']}.mrpack")
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, indent=2))
        z.write(jar, f"overrides/mods/{os.path.basename(jar)}")
        for path in glob.glob(os.path.join(PACK, "overrides/**/*"), recursive=True):
            if os.path.isfile(path):
                z.write(path, os.path.relpath(path, PACK))
    sha = hashlib.sha256(open(out, "rb").read()).hexdigest()[:12]
    print(f"wrote {os.path.relpath(out, ROOT)} ({os.path.getsize(out) // 1024} KiB, sha256 {sha}...)")


if __name__ == "__main__":
    {"resolve": resolve, "build": build}[sys.argv[1] if len(sys.argv) > 1 else "build"]()
