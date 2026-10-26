"""Prints CI screenshots as downscaled base64 JPEGs so they can be read from the job log.

Usage: python3 tools/ci_screens.py run/screenshots
"""
import base64
import io
import os
import sys

from PIL import Image

folder = sys.argv[1]
for name in sorted(os.listdir(folder)):
    if not name.endswith(".png"):
        continue
    img = Image.open(os.path.join(folder, name)).convert("RGB")
    img.thumbnail((640, 360))
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=72)
    data = base64.b64encode(buf.getvalue()).decode()
    print(f"SCREENSHOT-BEGIN {name}")
    for i in range(0, len(data), 4000):
        print("B64 " + data[i:i + 4000])
    print(f"SCREENSHOT-END {name}")
