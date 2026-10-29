"""The Shangri-La Frontier modpack: the mod plus a few client companions, for Modrinth and CurseForge.

  python3 tools/modpack.py resolve   # pin the newest compatible companion versions (needs network)
  python3 tools/modpack.py build     # build/modpack/*.mrpack and *-curseforge.zip from modpack/pack.json

Both packs hold the same files:
  * Shangri-La-Frontier-<version>.mrpack imports into the Modrinth App, Prism Launcher, ATLauncher...
  * Shangri-La-Frontier-<version>-curseforge.zip imports into the CurseForge app (Create Custom
    Profile -> Import) and other launchers that read CurseForge packs.
Companions are downloaded by the launcher from Modrinth's or CurseForge's CDN (only their pinned
ids and hashes are stored here); the Frontier jar itself rides along as an override. Companions
are client-only, so the same pack also sets up a dedicated server with just the mod.
"""
import glob
import hashlib
import html
import json
import os
import sys
import urllib.parse
import urllib.request
import zipfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
PACK = os.path.join(ROOT, "modpack")
MODRINTH = "https://api.modrinth.com/v2"
# CurseForge's own API needs a key; cfwidget mirrors the public project pages without one.
CFWIDGET = "https://api.cfwidget.com"

# (Modrinth slug, CurseForge project id, install folder, required?, Modrinth loaders, why it's in the pack)
COMPANIONS = [
    ("sodium", 394468, "mods", True, ["neoforge"], "Faster rendering; needed by Iris."),
    ("iris", 455508, "mods", True, ["neoforge"], "Shader pack loader."),
    ("complementary-reimagined", 627557, "shaderpacks", True, ["iris"], "The shader pack the pack switches on."),
    ("distanthorizons", 508933, "mods", False, ["neoforge"], "Optional: see the designed continent to the horizon."),
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
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def curseforge_file(project_id, filename):
    """The CurseForge file id of the same file Modrinth serves, so both packs are identical."""
    files = get(f"{CFWIDGET}/{project_id}").get("files", [])
    match = [f for f in files if f["name"] == filename]
    if not match:
        raise SystemExit(f"{filename} is not on CurseForge project {project_id}; pick a version both sites have")
    return match[0]["id"]


def resolve():
    p = props()
    files = []
    for slug, cf_project, folder, required, loaders, why in COMPANIONS:
        q = urllib.parse.urlencode({"game_versions": json.dumps([p["minecraft_version"]]), "loaders": json.dumps(loaders)})
        versions = [v for v in get(f"{MODRINTH}/project/{slug}/version?{q}") if v["version_type"] == "release"]
        if not versions:
            raise SystemExit(f"no release of {slug} for {p['minecraft_version']} / {loaders}")
        v = versions[0]
        f = next((f for f in v["files"] if f["primary"]), v["files"][0])
        cf_file = curseforge_file(cf_project, f["filename"])
        files.append({
            "path": f"{folder}/{f['filename']}",
            "hashes": {"sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"]},
            "env": {"client": "required" if required else "optional", "server": "unsupported"},
            "downloads": [f["url"]],
            "fileSize": f["size"],
            "_project": slug, "_version": v["version_number"], "_why": why,
            "_curseforge": {"projectID": cf_project, "fileID": cf_file},
        })
        print(f"{slug}: {v['version_number']} -> {f['filename']} (CurseForge {cf_project}/{cf_file})")
    json.dump({"files": files}, open(os.path.join(PACK, "pack.json"), "w"), indent=2)
    shader = next(f for f in files if f["_project"] == "complementary-reimagined")
    with open(os.path.join(PACK, "overrides/config/iris.properties"), "w") as out:
        out.write("# Written by the Shangri-La Frontier modpack: switch the bundled shader pack on.\n")
        out.write("enableShaders=true\n")
        out.write(f"shaderPack={os.path.basename(shader['path'])}\n")


def write_overrides(z, jar):
    """The Frontier jar and the pack's config files, under overrides/ (both formats use that folder)."""
    z.write(jar, f"overrides/mods/{os.path.basename(jar)}")
    for path in glob.glob(os.path.join(PACK, "overrides/**/*"), recursive=True):
        if os.path.isfile(path):
            z.write(path, os.path.relpath(path, PACK))


def build_modrinth(p, pinned, jar, out):
    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": p["mod_version"],
        "name": "Shangri-La Frontier",
        "summary": "The Shangri-La Frontier mod with shaders and a faster renderer, ready to play.",
        "files": [{k: v for k, v in f.items() if not k.startswith("_")} for f in pinned],
        "dependencies": {"minecraft": p["minecraft_version"], "neoforge": p["neo_version"]},
    }
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, indent=2))
        write_overrides(z, jar)


def build_curseforge(p, pinned, jar, out):
    manifest = {
        "minecraft": {
            "version": p["minecraft_version"],
            "modLoaders": [{"id": f"neoforge-{p['neo_version']}", "primary": True}],
        },
        "manifestType": "minecraftModpack",
        "manifestVersion": 1,
        "name": "Shangri-La Frontier",
        "version": p["mod_version"],
        "author": "camerondawkins0",
        "files": [{"projectID": f["_curseforge"]["projectID"], "fileID": f["_curseforge"]["fileID"],
                   "required": f["env"]["client"] == "required"} for f in pinned],
        "overrides": "overrides",
    }
    items = "".join(f'<li><a href="https://www.curseforge.com/projects/{f["_curseforge"]["projectID"]}">'
                    f'{html.escape(f["_project"])}</a> {html.escape(f["_version"])} &mdash; {html.escape(f["_why"])}</li>\n'
                    for f in pinned)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("manifest.json", json.dumps(manifest, indent=2))
        z.writestr("modlist.html", f"<ul>\n<li>Shangri-La Frontier {html.escape(p['mod_version'])} (included)</li>\n{items}</ul>\n")
        write_overrides(z, jar)


def build():
    p = props()
    jars = [j for j in glob.glob(os.path.join(ROOT, "build/libs/*.jar")) if not j.endswith(("-sources.jar", "-javadoc.jar"))]
    if not jars:
        raise SystemExit("build the mod first (./gradlew build)")
    jar = max(jars, key=os.path.getmtime)
    pinned = json.load(open(os.path.join(PACK, "pack.json")))["files"]
    os.makedirs(os.path.join(ROOT, "build/modpack"), exist_ok=True)
    name = f"build/modpack/Shangri-La-Frontier-{p['mod_version']}"
    for out, writer in ((f"{name}.mrpack", build_modrinth), (f"{name}-curseforge.zip", build_curseforge)):
        out = os.path.join(ROOT, out)
        writer(p, pinned, jar, out)
        sha = hashlib.sha256(open(out, "rb").read()).hexdigest()[:12]
        print(f"wrote {os.path.relpath(out, ROOT)} ({os.path.getsize(out) // 1024} KiB, sha256 {sha}...)")


if __name__ == "__main__":
    {"resolve": resolve, "build": build}[sys.argv[1] if len(sys.argv) > 1 else "build"]()
