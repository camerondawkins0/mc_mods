"""Boots a NeoForge dedicated server with the mod installed, generates terrain and
structures in the Frontier dimension, and fails if anything in the log looks broken.

Usage: python3 tools/ci_boot_test.py <server-dir>
"""
import os
import re
import subprocess
import sys
import threading
import time

SERVER_DIR = sys.argv[1] if len(sys.argv) > 1 else "server"
BOOT_TIMEOUT = 900

COMMANDS = [
    "execute in slfrontier:frontier run forceload add -48 -48 48 48",
    "execute in slfrontier:frontier run locate biome slfrontier:fastera_plains",
    "execute in slfrontier:frontier run locate biome slfrontier:diremarsh_waste",
    "execute in slfrontier:frontier run locate biome slfrontier:prismatic_forest_grotto",
    "execute in slfrontier:frontier run locate biome slfrontier:hidden_garden",
    "execute in slfrontier:frontier run locate biome slfrontier:nightslayer_woods",
    "execute in slfrontier:frontier run place structure slfrontier:wezaemon_tomb 400 100 400",
    "execute in slfrontier:frontier run place structure slfrontier:nightslayer_den 600 100 400",
    "execute in slfrontier:frontier run place structure slfrontier:empire_bee_hive 800 100 400",
    "execute in slfrontier:frontier run place structure slfrontier:mud_digger_lair 400 100 600",
    "execute in slfrontier:frontier run place structure slfrontier:divinity_ruins 600 100 600",
    "execute in slfrontier:frontier run place template slfrontier:town/fastera 200 100 200",
    "execute in slfrontier:frontier run place template slfrontier:town/rabituza 200 100 500",
    "execute in slfrontier:frontier run summon slfrontier:lycagon 0 120 0",
    "execute in slfrontier:frontier run summon slfrontier:wezaemon 4 120 0",
    "execute in slfrontier:frontier run summon slfrontier:mud_digger 8 120 0",
    "execute in slfrontier:frontier run summon slfrontier:empire_bee_queen 12 120 0",
    "execute in slfrontier:frontier run summon slfrontier:emul 16 120 0",
    "execute in slfrontier:frontier run summon slfrontier:setsuna 20 120 0",
    "slf debug selftest",
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
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
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
            proc.kill()
            print("Server did not finish booting in time", file=sys.stderr)
            sys.exit(1)
        time.sleep(1)

    boot_index = len(lines)
    for cmd in COMMANDS:
        print(f">>> {cmd}", flush=True)
        proc.stdin.write(cmd + "\n")
        proc.stdin.flush()
        time.sleep(8 if "forceload" in cmd or "place" in cmd else 3)
    time.sleep(20)
    proc.stdin.write("stop\n")
    proc.stdin.flush()
    try:
        proc.wait(timeout=180)
    except subprocess.TimeoutExpired:
        proc.kill()
    t.join(timeout=10)

    problems = []
    for line in lines:
        if any(p.search(line) for p in IGNORE_PATTERNS):
            continue
        if any(p.search(line) for p in BAD_PATTERNS):
            problems.append(line.rstrip())
    command_output = "".join(lines[boot_index:])
    for needed in ["Self-test passed"]:
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
