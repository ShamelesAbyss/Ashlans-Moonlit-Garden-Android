#!/usr/bin/env python3
"""Generate tiny, original PCM ambience/effect loops for the native garden.

These are procedural sound effects, not replacement soundtrack songs. No third-party
recordings, samples, network services, or generative music models are used.
"""
from __future__ import annotations

import math
import random
import struct
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets" / "soundscape"
RATE = 22_050


def write_wav(name: str, samples: list[float]) -> None:
    ROOT.mkdir(parents=True, exist_ok=True)
    with wave.open(str(ROOT / name), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, sample)) * 32767)) for sample in samples))


def chime(name: str, notes: tuple[tuple[float, float, float], ...]) -> None:
    total = max(start + duration for start, duration, _ in notes) + 0.08
    count = int(total * RATE)
    values = [0.0] * count
    for start, duration, frequency in notes:
        begin = int(start * RATE)
        length = int(duration * RATE)
        for i in range(length):
            t = i / RATE
            attack = min(1.0, t / 0.018)
            decay = math.exp(-4.1 * t / duration)
            values[begin + i] += 0.25 * attack * decay * (
                math.sin(2 * math.pi * frequency * t) + 0.22 * math.sin(2 * math.pi * frequency * 2.01 * t)
            )
    write_wav(name, values)


def ambience(rain: bool) -> None:
    seconds = 6.0
    count = int(seconds * RATE)
    rng = random.Random(72 if rain else 19)
    noise = [rng.uniform(-1.0, 1.0) for _ in range(count)]
    # Light one-pole smoothing keeps the rain gentle rather than hissy.
    low = 0.0
    values = []
    for i in range(count):
        low = low * 0.91 + noise[i] * 0.09
        t = i / RATE
        drone = 0.24 * math.sin(2 * math.pi * 110 * t) + 0.12 * math.sin(2 * math.pi * 165 * t)
        airy = 0.025 * math.sin(2 * math.pi * 392 * t) + 0.015 * math.sin(2 * math.pi * 523.25 * t)
        value = (low * 0.09 if rain else 0.0) + drone * (0.55 if rain else 0.45) + airy
        # A short crossfade at the loop seam avoids a click.
        edge = min(1.0, i / (RATE * 0.08), (count - i - 1) / (RATE * 0.08))
        values.append(value * max(0.0, edge))
    write_wav("ambience-rain.wav" if rain else "ambience-garden.wav", values)


def main() -> None:
    ambience(False)
    ambience(True)
    chime("sfx-plant.wav", ((0.00, 0.22, 440.0), (0.09, 0.26, 554.37)))
    chime("sfx-water.wav", ((0.00, 0.22, 659.25), (0.12, 0.27, 783.99)))
    chime("sfx-brew.wav", ((0.00, 0.24, 392.0), (0.17, 0.30, 523.25), (0.35, 0.42, 783.99)))
    chime("sfx-harvest.wav", ((0.00, 0.23, 523.25), (0.11, 0.25, 659.25), (0.23, 0.38, 880.0)))
    chime("sfx-softspark.wav", ((0.00, 0.23, 740.0), (0.11, 0.28, 987.77)))
    print(f"Generated procedural local ambience and effects in {ROOT}")


if __name__ == "__main__":
    main()
