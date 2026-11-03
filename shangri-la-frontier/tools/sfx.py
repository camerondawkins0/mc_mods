"""The Frontier's own sound effects, synthesised from scratch (no samples).

  python3 tools/sfx.py           # writes assets/slfrontier/sounds/**.ogg and sounds.json
  python3 tools/sfx.py preview   # also writes build/sfx_preview.png (waveforms + spectrograms)

Each sound is a recipe of oscillators, filtered noise and metal partials (tools/sfx_synth.py).
Several have variants (the game picks one at random) so repeated hits don't sound identical.
Sounds are mono so Minecraft places them in the world; levels are set per sound in VOLUME.
Swap any .ogg for your own recording whenever you like (keep the file name).
Needs numpy and ffmpeg (with libvorbis).
"""
import json
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lang  # noqa: E402
import sfx_synth as S  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "src/main/resources/assets/slfrontier/sounds")
SOUNDS_JSON = os.path.join(ROOT, "src/main/resources/assets/slfrontier/sounds.json")
MOD_SOUNDS = os.path.join(ROOT, "src/main/java/com/slfrontier/registry/ModSounds.java")


# ---- combat ------------------------------------------------------------------------

def swish(v, dur=0.26, lo=500, hi=2600, metal_f=None):
    """A blade cutting air: band-passed noise sweeping up then down, with an optional ring."""
    S.seed(100 + v)
    up = 0.45
    sweep = np.concatenate([S.expramp(dur * up, lo, hi), S.expramp(dur * (1 - up), hi, lo * 1.4)])
    n = S.length(dur)
    sweep = sweep[:n] if len(sweep) >= n else np.pad(sweep, (0, n - len(sweep)), mode="edge")
    air = S.svf(S.noise(dur), sweep * (1 + 0.08 * v), q=1.6) * S.env(dur, attack=dur * 0.35, curve=2.2)
    parts = [(air, 1.0, 0)]
    if metal_f:
        parts.append((S.metal(metal_f * (1 + 0.03 * v), 0.35, detune=0.004) * S.env(0.35, 0.002, curve=3), 0.16, dur * 0.3))
    return S.normalize(S.mix(*parts))


def slash(v):
    return swish(v, 0.24, 700, 3200, metal_f=2300)


def heavy_slash(v):
    """Bigger, lower swing with a body thump: finishers, cleaves."""
    S.seed(200 + v)
    a = swish(v, 0.36, 300, 1800, metal_f=1500)
    thump = S.sine(S.expramp(0.18, 120, 45)) * S.env(0.18, 0.002, curve=3)
    return S.normalize(S.mix((a, 1.0, 0), (thump, 0.55, 0.12)))


def cross_slash(v):
    """Two blades crossing: two quick swishes and a bright scissor shing."""
    a, b = swish(v, 0.2, 800, 3600), swish(v + 3, 0.2, 700, 3000)
    S.seed(300 + v)
    shing = S.metal(3100, 0.5, ratios=(1, 1.51, 2.37, 3.9), decays=(0.8, 0.5, 0.35, 0.2), detune=0.003) * S.env(0.5, 0.001, curve=2.5)
    return S.normalize(S.mix((a, 0.9, 0), (b, 0.9, 0.07), (shing, 0.35, 0.12)))


def pierce(v):
    """Drill Piercer: a sharp rising whoosh ending in a tight 'tsk' and a short ring."""
    S.seed(400 + v)
    dur = 0.3
    air = S.svf(S.noise(dur), S.expramp(dur, 600, 5200), q=2.2) * S.env(dur, attack=0.22, curve=1.5)
    tick = S.svf(S.noise(0.04), 4500, q=1.2, mode="hp") * S.env(0.04, 0.0005, curve=4)
    ring = S.metal(2600, 0.3, detune=0.004) * S.env(0.3, 0.001, curve=3)
    return S.normalize(S.mix((air, 1, 0), (tick, 0.6, 0.24), (ring, 0.18, 0.24)))


def cyclone(v):
    """Cyclone Slash: a whirling whoosh, its brightness wobbling as the blades come round."""
    S.seed(500 + v)
    dur = 0.62
    x = S.t(dur)
    cut = 1100 + 900 * np.sin(2 * np.pi * (5.5 + v * 0.3) * x) + 1500 * x / dur
    air = S.svf(S.noise(dur), cut, q=1.8) * S.env(dur, attack=0.12, curve=1.8)
    trem = 0.75 + 0.25 * np.sin(2 * np.pi * 11 * x)
    return S.normalize(air * trem)


def wave_crescent(v):
    """Moon Reflection: a whoosh carrying a pale shimmering chord."""
    S.seed(600 + v)
    dur = 0.7
    air = swish(v, 0.5, 400, 2400)
    shimmer = sum(S.sine(f * (1 + 0.004 * np.sin(2 * np.pi * 6 * S.t(dur)))) / (i + 1)
                  for i, f in enumerate((880, 1320, 1760, 2640)))
    shimmer = S.svf(shimmer, 3000, q=0.8, mode="lp") * S.env(dur, attack=0.05, curve=2.5)
    return S.normalize(S.reverb(S.mix((air, 0.9, 0), (shimmer, 0.28, 0.06)), mix=0.25, tail=0.35))


def sky_clear(v):
    """Sky Clear: a huge sweep round, a low boom, then a bright wind of open sky."""
    S.seed(700 + v)
    dur = 1.1
    x = S.t(dur)
    whirl = S.svf(S.noise(dur), 500 + 2500 * (x / dur) ** 0.6, q=1.4) * S.env(dur, attack=0.35, curve=2.0)
    boom = S.onepole_lp(S.noise(0.6), 220) * S.env(0.6, 0.004, curve=3) * 3
    boom += S.sine(S.expramp(0.6, 90, 32)) * S.env(0.6, 0.003, curve=2.5)
    sparkle = S.svf(S.noise(0.8), 7000, q=3, mode="bp") * S.env(0.8, 0.2, curve=2)
    return S.normalize(S.reverb(S.mix((whirl, 1, 0), (boom, 0.8, 0.32), (sparkle, 0.25, 0.4)), mix=0.3, size=1.3, tail=0.8))


def crescent_slam(v):
    """Crescent Vorpal landing: a sub drop and a cracked-earth burst."""
    S.seed(800 + v)
    sub = S.sine(S.expramp(0.7, 110, 28)) * S.env(0.7, 0.002, curve=2)
    crack = S.svf(S.noise(0.5), S.expramp(0.5, 2500, 300), q=0.9, mode="lp") * S.env(0.5, 0.001, curve=3)
    debris = S.svf(S.noise(0.6), 1800, q=0.7, mode="bp") * S.env(0.6, 0.05, curve=3) * (S.noise(0.6) > 0.6)
    return S.normalize(S.drive(S.mix((sub, 1, 0), (crack, 0.9, 0), (debris, 0.35, 0.05)), 1.6))


def dash(v):
    """Tap/Slide Step: a short, soft rush of air."""
    S.seed(900 + v)
    dur = 0.22
    air = S.svf(S.pink(dur), S.expramp(dur, 2400, 700), q=0.9) * S.env(dur, attack=0.03, curve=2.5)
    return S.normalize(air) * 0.9


def parry(v):
    """Perfect Parry: a bright clang of crossed steel with a glassy ping on top."""
    S.seed(1000 + v)
    clang = S.metal(1240 * (1 + 0.04 * v), 0.9, ratios=(1, 2.32, 4.25, 6.63, 9.38), decays=(1, 0.7, 0.45, 0.3, 0.2), detune=0.003)
    clang *= S.env(0.9, 0.0008, curve=3)
    transient = S.svf(S.noise(0.03), 5000, q=0.8, mode="hp") * S.env(0.03, 0.0003, curve=3)
    ping = S.sine(3520, 0.6) * S.env(0.6, 0.002, curve=4)
    return S.normalize(S.reverb(S.mix((clang, 1, 0), (transient, 0.7, 0), (ping, 0.25, 0.01)), mix=0.2, tail=0.4))


def parry_fail(v):
    """Parry window closed with nothing caught: a dull, short scrape."""
    S.seed(1100 + v)
    dur = 0.18
    scrape = S.svf(S.noise(dur), 900, q=1.3) * S.env(dur, 0.002, curve=3)
    return S.normalize(scrape) * 0.7


def stun(v):
    """An attacker staggered: a woozy wobble of ringing tones."""
    S.seed(1200 + v)
    dur = 0.6
    x = S.t(dur)
    wob = sum(S.sine(f * (1 + 0.03 * np.sin(2 * np.pi * 7 * x + i))) for i, f in enumerate((1568, 1976, 2349)))
    return S.normalize(wob * S.env(dur, 0.01, curve=2.5))


def impact(v, weight=1.0):
    """Blade meeting flesh: a damped body thump and a short noisy smack."""
    S.seed(1300 + v + int(weight * 10))
    dur = 0.22 + 0.1 * weight
    body = S.sine(S.expramp(dur, 180 / (0.6 + weight * 0.5), 50)) * S.env(dur, 0.001, curve=3)
    smack = S.svf(S.noise(0.08), 1400 + 400 * v, q=0.8, mode="bp") * S.env(0.08, 0.0005, curve=3)
    return S.normalize(S.drive(S.mix((body, weight, 0), (smack, 0.9, 0)), 1.5))


def crit(v):
    """Critical / weak-point hit: the impact plus a high, sharp tick."""
    S.seed(1400 + v)
    tick = S.metal(4200, 0.18, ratios=(1, 1.7, 2.9), decays=(0.6, 0.4, 0.3)) * S.env(0.18, 0.0005, curve=3)
    return S.normalize(S.mix((impact(v, 0.8), 1, 0), (tick, 0.45, 0)))


def finisher(v):
    """Kill or combo finisher: heavy impact with a sub boom and a short metallic flare."""
    S.seed(1500 + v)
    boom = S.sine(S.expramp(0.5, 75, 30)) * S.env(0.5, 0.002, curve=2.2)
    flare = S.metal(1900, 0.5, detune=0.004) * S.env(0.5, 0.001, curve=3)
    return S.normalize(S.reverb(S.mix((impact(v, 1.4), 1, 0), (boom, 0.8, 0), (flare, 0.2, 0.01)), mix=0.18, tail=0.4))


# ---- player state ----------------------------------------------------------------

def chime(notes, step=0.09, dur=0.9, bright=1.0, rev=0.3):
    """Bell-like notes struck in sequence."""
    parts = []
    for i, f in enumerate(notes):
        bell = S.metal(f, dur, ratios=(1, 2.0, 3.0, 4.2, 5.4), decays=(1, 0.6, 0.4, 0.3, 0.2),
                       amps=(1, 0.5 * bright, 0.3 * bright, 0.15 * bright, 0.08 * bright))
        parts.append((bell * S.env(dur, 0.003, curve=2.5), 1.0, i * step))
    return S.normalize(S.reverb(S.mix(*parts), mix=rev, tail=0.5))


def skill_ready(v):
    """A cooldown finished: one soft, high glint."""
    return chime([2093], dur=0.45, bright=0.6, rev=0.2) * 0.8


def stamina_empty(v):
    """Out of stamina: a muffled double heartbeat-like thud."""
    S.seed(1600)
    thud = S.sine(S.expramp(0.14, 95, 55)) * S.env(0.14, 0.003, curve=3)
    return S.normalize(S.onepole_lp(S.mix((thud, 1, 0), (thud, 0.7, 0.17)), 400)) * 0.8


def level_up(v):
    """Level up: a rising major arpeggio over a bright shimmer."""
    S.seed(1700)
    arp = chime([523.25, 659.25, 783.99, 1046.5, 1318.5], step=0.075, dur=1.1, rev=0.35)
    dur = len(arp) / S.RATE
    shimmer = S.svf(S.noise(dur), 8000, q=4) * S.env(dur, attack=0.3, curve=2.5)
    return S.normalize(S.mix((arp, 1, 0), (shimmer, 0.12, 0)))


def skill_learned(v):
    """A new skill: two notes up a fifth with a magical sparkle tail."""
    S.seed(1800)
    a = chime([783.99, 1174.7, 1567.98], step=0.11, dur=1.0, rev=0.4)
    return a


def quest_step(v):
    """A quest step done: two light notes."""
    return chime([1046.5, 1396.9], step=0.1, dur=0.6, bright=0.7, rev=0.25) * 0.9


def quest_complete(v):
    """A chapter or side quest complete: a short fanfare of bells."""
    return chime([659.25, 783.99, 987.77, 1318.5, 1567.98, 1975.5], step=0.085, dur=1.3, rev=0.4)


def waypoint(v):
    """Attuning to a waypoint crystal: a glassy, ringing chord that blooms."""
    S.seed(1900)
    dur = 1.6
    tones = sum(S.metal(f, dur, ratios=(1, 2.01, 3.03), decays=(1, 0.6, 0.4)) for f in (659.25, 987.77, 1318.5))
    swell = S.env(dur, attack=0.25, curve=2)
    return S.normalize(S.reverb(tones * swell, mix=0.45, size=1.4, tail=0.9))


def discover(v):
    """Entering a town or region for the first time: a soft open-air pad with a bell."""
    S.seed(2000)
    dur = 1.8
    x = S.t(dur)
    pad = sum(S.tri(f * (1 + 0.003 * np.sin(2 * np.pi * 0.7 * x + i))) for i, f in enumerate((261.63, 392.0, 523.25, 659.25)))
    pad = S.svf(pad, 1400, q=0.7, mode="lp") * S.env(dur, attack=0.5, curve=1.6)
    bell = chime([1046.5], dur=1.2, rev=0.4)
    return S.normalize(S.mix((pad, 0.6, 0), (bell, 0.5, 0.35)))


def full_dive(v):
    """Logging in or out of the Frontier: a rising digital sweep and a soft chord landing."""
    S.seed(2100 + v)
    dur = 1.4
    x = S.t(dur)
    rise = S.svf(S.saw(S.expramp(dur, 110, 880)) + 0.4 * S.noise(dur), S.expramp(dur, 400, 6000), q=2.5) * S.env(dur, attack=1.0, curve=1.5)
    arps = sum(S.sine(f, dur) * ((x * 16 % 1) < 0.5) for f in (880, 1108.7, 1318.5)) * S.env(dur, attack=0.6, curve=2) * 0.3
    land = chime([523.25, 783.99, 1046.5] if v == 0 else [1046.5, 783.99, 523.25], step=0.03, dur=1.0, rev=0.4)
    return S.normalize(S.mix((rise, 0.6, 0), (S.onepole_lp(arps, 4000), 0.4, 0), (land, 0.8, dur * 0.8)))


def heartbeat(v):
    """Low health: a deep lub-dub, felt more than heard (the HUD repeats it faster as HP drops)."""
    S.seed(1650)
    beat = lambda f, d: S.onepole_lp(S.sine(S.expramp(d, f, f * 0.6)) * S.env(d, 0.004, curve=3), 260)
    return S.normalize(S.mix((beat(70, 0.16), 1.0, 0), (beat(58, 0.2), 0.75, 0.2)))


def parry_glint(v):
    """A parryable attack is coming: a quick bright glint, like light catching a blade."""
    S.seed(2550 + v)
    dur = 0.35
    ping = S.metal(2960, dur, ratios=(1, 2.0, 3.01), decays=(0.7, 0.4, 0.25)) * S.env(dur, 0.001, curve=3)
    sweep = S.svf(S.noise(0.12), S.expramp(0.12, 3000, 9000), q=3) * S.env(0.12, 0.08, curve=2)
    return S.normalize(S.mix((sweep, 0.5, 0), (ping, 1.0, 0.08)))


# ---- UI --------------------------------------------------------------------------

def ui_click(v):
    S.seed(2200 + v)
    dur = 0.06
    blip = S.sine(S.expramp(dur, 2400 + 300 * v, 1500)) * S.env(dur, 0.001, curve=4)
    return S.normalize(blip) * 0.7


def ui_hover(v):
    dur = 0.04
    return S.normalize(S.sine(3200, dur) * S.env(dur, 0.001, curve=5)) * 0.35


def ui_open(v):
    """A holographic panel opening (v=0) or closing (v=1)."""
    S.seed(2300 + v)
    dur = 0.22
    f = S.expramp(dur, 500, 1600) if v == 0 else S.expramp(dur, 1600, 500)
    tone = (S.sine(f) + 0.3 * S.sine(f * 2.01)) * S.env(dur, 0.01, curve=2.5)
    air = S.svf(S.noise(dur), f * 2, q=2) * S.env(dur, 0.02, curve=2)
    return S.normalize(S.mix((tone, 0.6, 0), (air, 0.4, 0))) * 0.6


def ui_type(v):
    """Dialogue text appearing: a tiny soft tick (pitch varies per variant)."""
    dur = 0.03
    return S.normalize(S.sine(1300 + 120 * v, dur) * S.env(dur, 0.0008, curve=6)) * 0.3


def ui_deny(v):
    """Can't do that (on cooldown, missing materials): two low, short buzzes."""
    S.seed(2400)
    b = S.svf(S.saw(196, 0.07) * S.env(0.07, 0.002, curve=2), 900, q=0.8, mode="lp")
    return S.normalize(S.mix((b, 1, 0), (b, 0.8, 0.1))) * 0.6


# ---- bosses and the world ----------------------------------------------------------

def telegraph(v):
    """A boss attack is coming: a rising, tense double tone (heard by everyone near)."""
    S.seed(2500 + v)
    dur = 0.55
    f = S.expramp(dur, 330, 660)
    tone = (S.saw(f) + S.saw(f * 1.007)) * 0.5
    tone = S.svf(tone, S.expramp(dur, 600, 3000), q=1.5, mode="lp") * S.env(dur, attack=0.4, curve=1.2)
    return S.normalize(S.drive(tone, 1.5)) * 0.8


def boss_phase(v):
    """A boss enters a new phase: a reverse swell into a deep boom with a dark ring."""
    S.seed(2600)
    swell = S.svf(S.noise(0.8), S.expramp(0.8, 200, 4000), q=1.2) * np.linspace(0, 1, S.length(0.8)) ** 3
    boom = S.sine(S.expramp(1.2, 70, 28)) * S.env(1.2, 0.003, curve=1.8)
    ring = S.metal(220, 1.4, ratios=(1, 2.4, 3.8, 5.9), decays=(1, 0.6, 0.4, 0.3), detune=0.01) * S.env(1.4, 0.004, curve=2)
    return S.normalize(S.reverb(S.drive(S.mix((swell, 0.6, 0), (boom, 1, 0.8), (ring, 0.4, 0.8)), 1.3), mix=0.3, size=1.5, tail=1.0))


def boss_defeated(v):
    """A boss falls: low boom, then a slow, bright victory chord."""
    S.seed(2700)
    boom = S.sine(S.expramp(1.0, 60, 25)) * S.env(1.0, 0.003, curve=2)
    chord = chime([392.0, 493.88, 587.33, 783.99, 987.77], step=0.12, dur=2.0, rev=0.5)
    return S.normalize(S.mix((boom, 0.9, 0), (chord, 0.8, 0.35)))


def craft_done(v):
    """Something finished at a Frontier station: a bright two-tone ping."""
    return chime([1318.5, 1975.5], step=0.07, dur=0.7, bright=0.9, rev=0.25) * 0.85


# ---- the kit ---------------------------------------------------------------------

# event name -> (recipe, variants, subtitle (unused: see lang.SUBTITLES), volume)
SOUNDS = {
    "combat.slash": (slash, 4, "Blade swishes", 0.9),
    "combat.heavy_slash": (heavy_slash, 3, "Heavy swing", 1.0),
    "combat.cross_slash": (cross_slash, 2, "Blades cross", 1.0),
    "combat.impact": (lambda v: impact(v, 1.0), 4, "Blade hits", 0.9),
    "combat.crit": (crit, 3, "Critical hit", 1.0),
    "combat.finisher": (finisher, 2, "Finishing blow", 1.0),
    "combat.parry": (parry, 3, "Perfect parry", 1.0),
    "combat.parry_fail": (parry_fail, 2, "Parry misses", 0.6),
    "combat.stun": (stun, 2, "Attacker staggered", 0.7),
    "skill.dash": (dash, 3, "Dash", 0.7),
    "skill.pierce": (pierce, 2, "Drill Piercer", 1.0),
    "skill.cyclone": (cyclone, 2, "Cyclone Slash", 1.0),
    "skill.moon_wave": (wave_crescent, 2, "Crescent wave", 1.0),
    "skill.sky_clear": (sky_clear, 1, "Sky Clear", 1.0),
    "skill.crescent_slam": (crescent_slam, 2, "Crescent Vorpal lands", 1.0),
    "player.skill_ready": (skill_ready, 1, "Skill ready", 0.5),
    "player.heartbeat": (heartbeat, 1, "Heartbeat", 0.7),
    "player.stamina_empty": (stamina_empty, 1, "Out of stamina", 0.6),
    "player.level_up": (level_up, 1, "Level up", 0.9),
    "player.skill_learned": (skill_learned, 1, "Skill learned", 0.9),
    "player.full_dive_in": (lambda v: full_dive(0), 1, "Full-dive link", 0.8),
    "player.full_dive_out": (lambda v: full_dive(1), 1, "Link closed", 0.8),
    "quest.step": (quest_step, 1, "Quest updated", 0.7),
    "quest.complete": (quest_complete, 1, "Quest complete", 0.9),
    "world.waypoint": (waypoint, 1, "Waypoint attuned", 0.9),
    "world.discover": (discover, 1, "New place found", 0.8),
    "world.craft_done": (craft_done, 1, "Crafting done", 0.8),
    "boss.telegraph": (telegraph, 2, "Boss winds up", 0.8),
    "boss.phase": (boss_phase, 1, "Boss grows stronger", 1.0),
    "boss.parry_glint": (parry_glint, 2, "Blade glints", 0.9),
    "boss.defeated": (boss_defeated, 1, "Boss defeated", 1.0),
    "ui.click": (ui_click, 2, None, 0.5),
    "ui.hover": (ui_hover, 1, None, 0.25),
    "ui.open": (lambda v: ui_open(0), 1, None, 0.5),
    "ui.close": (lambda v: ui_open(1), 1, None, 0.45),
    "ui.deny": (ui_deny, 1, "Not possible", 0.5),
    "ui.type": (ui_type, 3, None, 0.25),
}


def write_ogg(path, sig):
    sig = S.fade(S.trim_silence(sig), 0.001, 0.015)
    pcm = (np.clip(sig, -1, 1) * 32767).astype("<i2")
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav = tmp.name
    with wave.open(wav, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(S.RATE)
        w.writeframes(pcm.tobytes())
    os.makedirs(os.path.dirname(path), exist_ok=True)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "5", path], check=True)
    os.unlink(wav)


def build(preview=False):
    rendered = {}
    sounds_json = {}
    for name, (recipe, variants, subtitle, volume) in SOUNDS.items():
        files = []
        for v in range(variants):
            sig = recipe(v)
            rel = name.replace(".", "/") + (f"_{v + 1}" if variants > 1 else "")
            write_ogg(os.path.join(OUT, rel + ".ogg"), sig)
            files.append({"name": f"slfrontier:{rel}", "volume": volume})
            rendered[f"{name}#{v + 1}"] = sig
        entry = {"sounds": files}
        if name in lang.SUBTITLES:
            entry["subtitle"] = f"subtitles.slfrontier.{name}"
        sounds_json[name] = entry
        print(f"{name}: {variants} variant(s)")
    with open(SOUNDS_JSON, "w") as f:
        json.dump(sounds_json, f, indent=2)
    write_registry()
    if preview:
        write_preview(rendered)
    return sounds_json


def write_registry():
    """ModSounds.java: one SoundEvent per entry in SOUNDS, named after it (combat.parry -> COMBAT_PARRY)."""
    lines = []
    for name in SOUNDS:
        const = name.replace(".", "_").upper()
        lines.append(f'    public static final DeferredHolder<SoundEvent, SoundEvent> {const} = sound("{name}");')
    src = f"""package com.slfrontier.registry;

import com.slfrontier.SLFrontier;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Frontier's own sounds, synthesised by tools/sfx.py (which also writes this file and
 * assets/slfrontier/sounds.json). Replace any .ogg under assets/slfrontier/sounds to change one.
 */
public final class ModSounds {{
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SLFrontier.MODID);

{chr(10).join(lines)}

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {{
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(SLFrontier.id(name)));
    }}

    private ModSounds() {{
    }}
}}
"""
    with open(MOD_SOUNDS, "w") as f:
        f.write(src)


def write_preview(rendered):
    """Waveform + spectrogram strip per sound, for checking levels and brightness by eye."""
    from PIL import Image, ImageDraw
    rows = list(rendered.items())
    w, h = 900, 70
    img = Image.new("RGB", (w, h * len(rows)), (12, 14, 20))
    d = ImageDraw.Draw(img)
    for r, (name, sig) in enumerate(rows):
        y0 = r * h
        n = len(sig)
        # spectrogram (right 600 px)
        win = 1024
        hop = max(1, (n - win) // 600) if n > win else 1
        cols = []
        for i in range(0, max(1, n - win), hop):
            seg = sig[i:i + win] * np.hanning(win) if i + win <= n else np.zeros(win)
            cols.append(np.abs(np.fft.rfft(seg))[:400])
        if cols:
            spec = 20 * np.log10(np.array(cols).T + 1e-6)
            spec = np.clip((spec + 30) / 70, 0, 1)[::-1]
            sp = Image.fromarray((spec * 255).astype("uint8")).resize((600, h - 4)).convert("RGB")
            img.paste(sp, (300, y0 + 2))
        # waveform (left 290 px)
        pts = np.abs(sig[:: max(1, n // 290)])[:290]
        for x, a in enumerate(pts):
            d.line([(x, y0 + h // 2 - a * (h // 2 - 4)), (x, y0 + h // 2 + a * (h // 2 - 4))], fill=(90, 200, 255))
        d.text((4, y0 + 2), f"{name}  {n / S.RATE:.2f}s  rms {np.sqrt(np.mean(sig ** 2)):.2f}", fill=(255, 255, 255))
    path = os.path.join(ROOT, "build/sfx_preview.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("wrote", os.path.relpath(path, ROOT))


if __name__ == "__main__":
    build(preview=len(sys.argv) > 1 and sys.argv[1] == "preview")
