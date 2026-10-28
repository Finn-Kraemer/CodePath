"""Legt ein Bild unter seinem Schlüssel (SHA-256 + Endung) in content/<modul>/media/ ab.

  python content/tools/add_media.py bild.png it-sicherheit
Gibt den Schlüssel aus; in Aufgaben als "image": "<schlüssel>" oder im Markdown als
![Beschreibung](/api/media/<schlüssel>) verwenden.
"""
import hashlib
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
EXTENSIONS = {b"\x89PNG": "png", b"\xff\xd8\xff": "jpg", b"GIF8": "gif"}


def extension(data: bytes) -> str:
    for magic, ext in EXTENSIONS.items():
        if data.startswith(magic):
            return ext
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "webp"
    sys.exit("Nur PNG, JPEG, GIF oder WebP.")


def main():
    source, module = Path(sys.argv[1]), sys.argv[2]
    data = source.read_bytes()
    if len(data) > 2 * 1024 * 1024:
        sys.exit("Höchstens 2 MB.")
    key = f"{hashlib.sha256(data).hexdigest()}.{extension(data)}"
    target = ROOT / module / "media" / key
    target.parent.mkdir(exist_ok=True)
    shutil.copyfile(source, target)
    print(key)


if __name__ == "__main__":
    main()
