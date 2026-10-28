"""Boots a NeoForge dedicated server with the mod installed, generates terrain and
structures in the Frontier dimension, and fails if anything in the log looks broken.

Usage: python3 tools/ci_boot_test.py <server-dir>
"""
import os
import re
import signal
import subprocess
import sys
import threading
import time

SERVER_DIR = sys.argv[1] if len(sys.argv) > 1 else "server"
BOOT_TIMEOUT = 900

COMMANDS = [
    "execute in slfrontier:frontier run forceload add -3096 -3096 -2904 -2904",
    "execute in slfrontier:frontier run locate biome slfrontier:fastera_plains",
    "execute in slfrontier:frontier run locate biome slfrontier:diremarsh_waste",
    "execute in slfrontier:frontier run locate biome slfrontier:prismatic_forest_grotto",
    "execute in slfrontier:frontier run locate biome slfrontier:hidden_garden",
    "execute in slfrontier:frontier run locate biome slfrontier:nightslayer_woods",
    "execute in slfrontier:frontier run locate biome slfrontier:rampant_forest",
    "execute in slfrontier:frontier run locate biome slfrontier:lightless_barrens",
    "execute in slfrontier:frontier run locate biome slfrontier:thirdrema_highlands",
    # Everything is placed inside the force-loaded area (commands refuse unloaded chunks), far
    # from Firstia so the test pile doesn't end up on the map or next to the real towns.
    "execute in slfrontier:frontier run place structure slfrontier:wezaemon_tomb -3000 100 -3000",
    "execute in slfrontier:frontier run place structure slfrontier:nightslayer_den -3000 100 -3000",
    "execute in slfrontier:frontier run place structure slfrontier:empire_bee_hive -3000 100 -3000",
    "execute in slfrontier:frontier run place structure slfrontier:mud_digger_lair -3000 100 -3000",
    "execute in slfrontier:frontier run place structure slfrontier:divinity_ruins -3000 100 -3000",
    "execute in slfrontier:frontier run place structure slfrontier:shadow_altar -3000 100 -3000",
    "execute in slfrontier:frontier run place template slfrontier:town/firstia -3056 120 -3056",
    "execute in slfrontier:frontier run place template slfrontier:town/secondil -3064 140 -3064",
    "execute in slfrontier:frontier run place template slfrontier:town/thirdrema -3056 100 -3080",
    "execute in slfrontier:frontier run place template slfrontier:town/rabituza -3064 180 -3064",
    "execute in slfrontier:frontier run place template slfrontier:landmark/serpent_bridge -3040 200 -3028",
    "execute in slfrontier:frontier run summon slfrontier:lycagon -3000 120 -3000",
    "execute in slfrontier:frontier run summon slfrontier:wezaemon -2996 120 -3000",
    "execute in slfrontier:frontier run summon slfrontier:mud_digger -2992 120 -3000",
    "execute in slfrontier:frontier run summon slfrontier:empire_bee_queen -2988 120 -3000",
    "execute in slfrontier:frontier run summon slfrontier:emul -2984 120 -3000",
    "execute in slfrontier:frontier run summon slfrontier:setsuna -2980 120 -3000",
    "execute in slfrontier:frontier run summon slfrontier:redcap_goblin -2976 120 -3000",
    "execute in slfrontier:frontier run summon slfrontier:ravenous_snake -2972 120 -3000",
    "execute in slfrontier:frontier run summon slfrontier:woodmage -2968 120 -3000",
    "execute in slfrontier:frontier run summon slfrontier:kirin -2964 120 -3000",
    "execute in slfrontier:frontier run summon slfrontier:lycagon_shadow -2960 120 -3000",
    "execute in slfrontier:frontier run summon slfrontier:pencilgon -2956 120 -3000",
    "execute in slfrontier:frontier run summon slfrontier:oikatzo -2952 120 -3000",
    "slf debug selftest",
    "slf debug storytest",
    "save-all",
]

BAD_PATTERNS = [
    re.compile(p) for p in [
        r"Exception",
        r"\bERROR\b",
        r"FAILED during APPLY",
        r"was not applied",
        r"Feature order cycle",
        r"Unknown (?:block|item|entity|structure|template|biome)",
        r"not loaded",
        r"Failed to place",
        r"Could not find",
        r"Couldn't parse",
        r"Failed to (?:load|parse|decode)",
        r"No key \w+ in MapLike",
        r"Error loading registry data",
    ]
]
# Lines that are noisy on headless CI but harmless.
IGNORE_PATTERNS = [
    re.compile(p) for p in [
        r"Ambiguity between arguments",
        r"Can't keep up",
        r"narrator",
        r"OpenGL",
        r"Failed to load (?:properties|eula)",
    ]
]


def main():
    script = "./run.sh"
    os.chmod(os.path.join(SERVER_DIR, "run.sh"), 0o755)
    with open(os.path.join(SERVER_DIR, "user_jvm_args.txt"), "a") as f:
        f.write("\n-Xmx3G\n")
    proc = subprocess.Popen(
        [script, "nogui"], cwd=SERVER_DIR, stdin=subprocess.PIPE,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1, start_new_session=True)

    def kill():
        # run.sh starts java as a child; take the whole process group down with it.
        try:
            os.killpg(proc.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
    lines = []
    done = threading.Event()

    def pump():
        for line in proc.stdout:
            sys.stdout.write(line)
            lines.append(line)
            if re.search(r"Done \([\d.,]+s\)!", line):
                done.set()

    t = threading.Thread(target=pump, daemon=True)
    t.start()

    start = time.time()
    while not done.is_set():
        if proc.poll() is not None:
            print("Server exited before finishing boot", file=sys.stderr)
            sys.exit(1)
        if time.time() - start > BOOT_TIMEOUT:
            kill()
            print("Server did not finish booting in time", file=sys.stderr)
            sys.exit(1)
        time.sleep(1)

    boot_index = len(lines)
    for cmd in COMMANDS:
        print(f">>> {cmd}", flush=True)
        proc.stdin.write(cmd + "\n")
        proc.stdin.flush()
        time.sleep(60 if "storytest" in cmd else 10 if "forceload" in cmd or "place" in cmd else 3)
    time.sleep(20)
    proc.stdin.write("stop\n")
    proc.stdin.flush()
    try:
        proc.wait(timeout=900)
    except subprocess.TimeoutExpired:
        kill()
    t.join(timeout=10)

    problems = []
    for line in lines:
        if any(p.search(line) for p in IGNORE_PATTERNS):
            continue
        if any(p.search(line) for p in BAD_PATTERNS):
            problems.append(line.rstrip())
    command_output = "".join(lines[boot_index:])
    for needed in ["Self-test passed", "Story test passed"]:
        if needed not in command_output:
            problems.append(f"Missing expected output: {needed}")
    if problems:
        print("\n==== Problems found ====", file=sys.stderr)
        for p in problems[:200]:
            print(p, file=sys.stderr)
        sys.exit(1)
    print("\nBoot test passed.")


if __name__ == "__main__":
    main()
