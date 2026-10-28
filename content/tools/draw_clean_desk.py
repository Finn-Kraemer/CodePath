"""Zeichnet das Bild für it-sicherheit/s15 (Clean Desk) und gibt die Zielbereiche in Prozent aus.

  python content/tools/draw_clean_desk.py out.png
Danach mit content/tools/add_media.py ins Modul übernehmen.
"""
import json
import sys

from PIL import Image, ImageDraw, ImageFont

W, H = 1200, 800
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def font(size, bold=False):
    return ImageFont.truetype(BOLD if bold else FONT, size)


img = Image.new("RGB", (W, H), "#e8edf3")
d = ImageDraw.Draw(img)
targets = []


def target(tid, label, box):
    x0, y0, x1, y1 = box
    targets.append({"id": tid, "label": label,
                    "x": round(x0 / W * 100, 1), "y": round(y0 / H * 100, 1),
                    "w": round((x1 - x0) / W * 100, 1), "h": round((y1 - y0) / H * 100, 1)})


# Wand mit Uhr und Bild
d.rectangle([0, 0, W, 470], fill="#dfe6ee")
d.ellipse([1010, 50, 1110, 150], fill="white", outline="#475569", width=5)
d.line([1060, 100, 1060, 66], fill="#1e293b", width=5)
d.line([1060, 100, 1088, 112], fill="#1e293b", width=4)
d.rectangle([80, 60, 260, 190], fill="#fef3c7", outline="#92400e", width=6)
d.polygon([(100, 170), (160, 100), (210, 150), (240, 120), (245, 170)], fill="#65a30d")

# Schreibtisch
d.rectangle([0, 470, W, H], fill="#a16207")
d.rectangle([0, 470, W, 490], fill="#854d0e")

# Monitor mit entsperrtem Bildschirm
d.rectangle([380, 110, 820, 400], fill="#1f2937", outline="#0f172a", width=8)
d.rectangle([400, 130, 800, 380], fill="#f8fafc")
d.rectangle([400, 130, 800, 160], fill="#1d4ed8")
d.text((412, 136), "Kundendaten.xlsx", font=font(18, True), fill="white")
for row in range(6):
    y = 175 + row * 32
    for col, x in enumerate([410, 540, 670]):
        d.rectangle([x, y, x + 120, y + 26], outline="#94a3b8", fill="#e2e8f0" if row == 0 else "white")
d.text((416, 179), "Name", font=font(15, True), fill="#0f172a")
d.text((546, 179), "IBAN", font=font(15, True), fill="#0f172a")
d.text((676, 179), "Geburtstag", font=font(15, True), fill="#0f172a")
for row, (n, i, g) in enumerate([("Meier", "DE12 …", "03.05."), ("Yilmaz", "DE88 …", "17.11."),
                                  ("Schulz", "DE41 …", "29.02."), ("Nowak", "DE07 …", "08.08."),
                                  ("Berg", "DE63 …", "12.12.")], start=1):
    y = 179 + row * 32
    d.text((416, y), n, font=font(15), fill="#334155")
    d.text((546, y), i, font=font(15), fill="#334155")
    d.text((676, y), g, font=font(15), fill="#334155")
target("screen", "Bildschirm nicht gesperrt – Kundendaten offen sichtbar", (400, 130, 800, 380))
d.rectangle([570, 400, 630, 450], fill="#334155")
d.rectangle([500, 450, 700, 470], fill="#334155")

# Post-it mit Passwort am Monitorrand
d.polygon([(800, 250), (905, 240), (915, 345), (810, 355)], fill="#fde047", outline="#ca8a04")
d.text((815, 262), "PW:", font=font(20, True), fill="#1e293b")
d.text((815, 292), "Sommer", font=font(19), fill="#1e293b")
d.text((815, 318), "2026!", font=font(19), fill="#1e293b")
target("postit", "Passwort auf einem Zettel am Monitor", (795, 235, 920, 360))

# Tastatur und Maus
d.rounded_rectangle([400, 560, 800, 640], radius=10, fill="#cbd5e1", outline="#64748b", width=3)
for r in range(3):
    for c in range(14):
        x = 412 + c * 27
        y = 570 + r * 22
        d.rectangle([x, y, x + 22, y + 17], fill="#f1f5f9", outline="#94a3b8")
d.ellipse([850, 575, 900, 640], fill="#e2e8f0", outline="#64748b", width=3)

# Unbekannter USB-Stick
d.rounded_rectangle([960, 650, 1120, 700], radius=8, fill="#dc2626", outline="#7f1d1d", width=3)
d.rectangle([1120, 660, 1150, 690], fill="#9ca3af", outline="#4b5563", width=2)
d.text((972, 662), "GEFUNDEN?", font=font(18, True), fill="white")
target("usb", "Unbekannter USB-Stick – nie einstecken", (955, 640, 1160, 710))

# Vertrauliches Dokument
d.polygon([(90, 560), (330, 545), (345, 760), (105, 775)], fill="white", outline="#94a3b8")
d.text((110, 570), "VERTRAULICH", font=font(22, True), fill="#b91c1c")
d.text((112, 605), "Personalakte", font=font(18), fill="#1e293b")
for i in range(6):
    d.line([115, 645 + i * 18, 315, 640 + i * 18], fill="#94a3b8", width=3)
target("paper", "Vertrauliches Dokument offen auf dem Tisch", (85, 540, 350, 780))

# Kaffeetasse und Pflanze (keine Probleme)
d.rounded_rectangle([860, 470, 930, 545], radius=8, fill="white", outline="#475569", width=4)
d.arc([915, 485, 955, 525], start=-90, end=90, fill="#475569", width=5)
d.ellipse([868, 474, 922, 490], fill="#78350f")
d.rectangle([1040, 420, 1110, 500], fill="#b45309", outline="#78350f", width=3)
for dx, dy in [(-30, -90), (0, -110), (30, -85), (-10, -70), (20, -60)]:
    d.ellipse([1075 + dx - 18, 420 + dy - 30, 1075 + dx + 18, 420 + dy + 30], fill="#16a34a")

img.save(sys.argv[1], optimize=True)
print(json.dumps(targets, ensure_ascii=False, indent=2))
