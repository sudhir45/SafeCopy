#!/usr/bin/env python3
"""Generate SYNTHETIC ID-card-like test images with ground truth (spec 15.1).

Every name, number and date here is made up. Aadhaar-style numbers are random digits made
Verhoeff-valid; PAN-style numbers are random and only follow the format. QR codes hold random
text. Never put real ID data in this script or its output.

Usage (from the project root):
    tools/fixtures/.venv/bin/python tools/fixtures/generate_fixtures.py [--seed 7]

Writes JPEGs plus ground_truth.json into app/src/androidTest/assets/fixtures/.
Ground-truth rects are [left, top, right, bottom] in the pixel coordinates of each saved image.
"""

import argparse
import json
import math
import random
from pathlib import Path

import qrcode
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/androidTest/assets/fixtures"
FONT_DIR = Path("/usr/share/fonts")
SANS = FONT_DIR / "liberation/LiberationSans-Regular.ttf"
SANS_BOLD = FONT_DIR / "liberation/LiberationSans-Bold.ttf"
DEVANAGARI = FONT_DIR / "noto/NotoSansDevanagari-Medium.ttf"

CARD_W, CARD_H = 1800, 1130  # ~ ID-1 card aspect ratio
BG_W, BG_H = 2200, 1500  # "table" the card is photographed on
CARD_OFFSET = (200, 185)

# --- Verhoeff (same tables as spec 6.5) -------------------------------------------------
D = [
    [0, 1, 2, 3, 4, 5, 6, 7, 8, 9], [1, 2, 3, 4, 0, 6, 7, 8, 9, 5], [2, 3, 4, 0, 1, 7, 8, 9, 5, 6],
    [3, 4, 0, 1, 2, 8, 9, 5, 6, 7], [4, 0, 1, 2, 3, 9, 5, 6, 7, 8], [5, 9, 8, 7, 6, 0, 4, 3, 2, 1],
    [6, 5, 9, 8, 7, 1, 0, 4, 3, 2], [7, 6, 5, 9, 8, 2, 1, 0, 4, 3], [8, 7, 6, 5, 9, 3, 2, 1, 0, 4],
    [9, 8, 7, 6, 5, 4, 3, 2, 1, 0],
]
P = [
    [0, 1, 2, 3, 4, 5, 6, 7, 8, 9], [1, 5, 7, 6, 2, 8, 3, 0, 9, 4], [5, 8, 0, 3, 7, 9, 6, 1, 4, 2],
    [8, 9, 1, 6, 0, 4, 3, 5, 2, 7], [9, 4, 5, 3, 1, 2, 6, 8, 7, 0], [4, 2, 8, 6, 5, 7, 3, 9, 0, 1],
    [2, 7, 9, 3, 8, 0, 6, 4, 1, 5], [7, 0, 4, 6, 9, 1, 3, 2, 5, 8],
]
INV = [0, 4, 3, 2, 1, 5, 6, 7, 8, 9]


def verhoeff_check_digit(num: str) -> int:
    c = 0
    for i, ch in enumerate(reversed(num)):
        c = D[c][P[(i + 1) % 8][int(ch)]]
    return INV[c]


def verhoeff_valid(num: str) -> bool:
    c = 0
    for i, ch in enumerate(reversed(num)):
        c = D[c][P[i % 8][int(ch)]]
    return c == 0


def fake_aadhaar(rnd: random.Random) -> str:
    body = str(rnd.randint(2, 9)) + "".join(str(rnd.randint(0, 9)) for _ in range(10))
    num = body + str(verhoeff_check_digit(body))
    assert verhoeff_valid(num)
    return num


def fake_vid(rnd: random.Random) -> str:
    return "".join(str(rnd.randint(0, 9)) for _ in range(16))


def fake_pan(rnd: random.Random, surname: str) -> str:
    letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    return (
        "".join(rnd.choice(letters) for _ in range(3)) + "P" + surname[0].upper()
        + "".join(str(rnd.randint(0, 9)) for _ in range(4)) + rnd.choice(letters)
    )


def group4(s: str) -> str:
    return " ".join(s[i:i + 4] for i in range(0, len(s), 4))


FIRST = ["Ravi", "Anita", "Suresh", "Meena", "Arjun", "Kavya", "Imran", "Neha", "Vikram", "Pooja"]
LAST = ["Sharma", "Verma", "Iyer", "Khan", "Patel", "Reddy", "Das", "Nair", "Gupta", "Singh"]
STREETS = ["Gandhi Nagar", "Station Road", "Lake View Colony", "Shivaji Marg", "Temple Street"]
CITIES = [("Lucknow", "Uttar Pradesh", "226001"), ("Pune", "Maharashtra", "411001"),
          ("Kochi", "Kerala", "682001"), ("Indore", "Madhya Pradesh", "452001")]


# --- drawing helpers -------------------------------------------------------------------------
def font(path: Path, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(path), size)


class Card:
    """A card canvas that records ground-truth rects in card coordinates."""

    def __init__(self, bg: tuple[int, int, int]):
        self.img = Image.new("RGB", (CARD_W, CARD_H), bg)
        self.draw = ImageDraw.Draw(self.img)
        self.fields: list[dict] = []

    def text(self, xy, s, f, fill=(20, 20, 20)):
        self.draw.text(xy, s, font=f, fill=fill)
        return self.draw.textbbox(xy, s, font=f)

    def char_span(self, xy, s, f, start: int, end: int):
        """Box covering characters s[start:end] drawn at xy (end exclusive)."""
        x, y = xy
        full = self.draw.textbbox(xy, s, font=f)
        left = x + f.getlength(s[:start])
        right = x + f.getlength(s[:end])
        return [left, full[1], right, full[3]]

    def field(self, kind: str, rect):
        self.fields.append({"type": kind, "rect": [round(v, 1) for v in rect]})

    def qr(self, xy, size: int, rnd: random.Random):
        payload = "SYNTHETIC-TEST-" + "".join(rnd.choice("ABCDEFGHJKLMNPQRSTUVWXYZ23456789") for _ in range(120))
        q = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, border=0, box_size=10)
        q.add_data(payload)
        q.make(fit=True)
        img = q.make_image(fill_color="black", back_color="white").convert("RGB").resize((size, size), Image.NEAREST)
        # White quiet zone, as printed on cards
        pad = size // 12
        self.draw.rectangle([xy[0] - pad, xy[1] - pad, xy[0] + size + pad, xy[1] + size + pad], fill="white")
        self.img.paste(img, xy)
        self.field("QR", [xy[0], xy[1], xy[0] + size, xy[1] + size])

    def photo(self, xy, w, h):
        x, y = xy
        self.draw.rectangle([x, y, x + w, y + h], fill=(205, 210, 215), outline=(150, 150, 150), width=3)
        self.draw.ellipse([x + w * 0.3, y + h * 0.15, x + w * 0.7, y + h * 0.55], fill=(150, 155, 160))
        self.draw.pieslice([x + w * 0.12, y + h * 0.58, x + w * 0.88, y + h * 1.3], 180, 360, fill=(150, 155, 160))


def aadhaar_front(rnd: random.Random) -> tuple[Card, str]:
    c = Card((250, 250, 245))
    first, last = rnd.choice(FIRST), rnd.choice(LAST)
    c.draw.rectangle([0, 0, CARD_W, 150], fill=(255, 153, 51))
    c.draw.rectangle([0, 150, CARD_W, 175], fill=(255, 255, 255))
    c.draw.rectangle([0, 175, CARD_W, 200], fill=(19, 136, 8))
    c.text((60, 30), "भारत सरकार", font(DEVANAGARI, 52))
    c.text((60, 90), "Government of India", font(SANS_BOLD, 46))
    c.photo((80, 280), 330, 400)

    reg, bold = font(SANS, 48), font(SANS_BOLD, 50)
    c.text((480, 290), f"{first} {last}", bold)
    dob = f"{rnd.randint(1, 28):02d}/{rnd.randint(1, 12):02d}/{rnd.randint(1960, 2004)}"
    label = "DOB: "
    c.text((480, 380), label + dob, reg)
    c.field("DOB", c.char_span((480, 380), label + dob, reg, len(label), len(label + dob)))
    c.text((480, 460), rnd.choice(["MALE", "FEMALE"]), reg)

    number = group4(fake_aadhaar(rnd))
    nf = font(SANS_BOLD, 92)
    nxy = (520, 760)
    c.text(nxy, number, nf)
    c.field("AADHAAR_NUMBER", c.char_span(nxy, number, nf, 0, 9))  # digits 1-8 incl. the space

    vid = group4(fake_vid(rnd))
    vlabel = "VID : "
    vf = font(SANS, 40)
    vxy = (520, 890)
    c.text(vxy, vlabel + vid, vf)
    c.field("AADHAAR_VID", c.char_span(vxy, vlabel + vid, vf, len(vlabel), len(vlabel + vid)))

    c.qr((1400, 300), 300, rnd)
    c.draw.line([0, 1010, CARD_W, 1010], fill=(200, 30, 30), width=6)
    c.text((560, 1030), "My Aadhaar, My Identity", font(SANS_BOLD, 50), fill=(200, 30, 30))
    return c, "AADHAAR"


def aadhaar_back(rnd: random.Random) -> tuple[Card, str]:
    c = Card((250, 250, 245))
    c.draw.rectangle([0, 0, CARD_W, 140], fill=(255, 153, 51))
    c.text((60, 40), "Unique Identification Authority of India", font(SANS_BOLD, 56))
    reg = font(SANS, 44)
    street, (city, state, pin) = rnd.choice(STREETS), rnd.choice(CITIES)
    lines = [
        f"Address: S/O {rnd.choice(FIRST)} {rnd.choice(LAST)},",
        f"House No {rnd.randint(1, 250)}, {street},",
        f"{city}, {state} - {pin}",
    ]
    boxes = [c.text((80, 240 + i * 75), line, reg) for i, line in enumerate(lines)]
    c.field("ADDRESS", [min(b[0] for b in boxes), boxes[0][1], max(b[2] for b in boxes), boxes[-1][3]])

    c.qr((1320, 220), 400, rnd)
    number = group4(fake_aadhaar(rnd))
    nf = font(SANS_BOLD, 92)
    nxy = (520, 820)
    c.text(nxy, number, nf)
    c.field("AADHAAR_NUMBER", c.char_span(nxy, number, nf, 0, 9))
    c.text((60, 1010), "help@example.invalid    www.example.invalid", font(SANS, 36), fill=(90, 90, 90))
    return c, "AADHAAR"


def pan_card(rnd: random.Random) -> tuple[Card, str]:
    c = Card((214, 232, 245))
    first, last = rnd.choice(FIRST), rnd.choice(LAST)
    c.text((80, 50), "INCOME TAX DEPARTMENT", font(SANS_BOLD, 60))
    c.text((1180, 60), "GOVT. OF INDIA", font(SANS_BOLD, 52))
    reg, bold = font(SANS, 44), font(SANS_BOLD, 50)

    c.text((80, 200), "Name", font(SANS, 34), fill=(70, 70, 70))
    c.text((80, 240), f"{first} {last}".upper(), bold)
    c.text((80, 330), "Father's Name", font(SANS, 34), fill=(70, 70, 70))
    c.text((80, 370), f"{rnd.choice(FIRST)} {last}".upper(), bold)
    c.text((80, 460), "Date of Birth", font(SANS, 34), fill=(70, 70, 70))
    dob = f"{rnd.randint(1, 28):02d}/{rnd.randint(1, 12):02d}/{rnd.randint(1960, 2004)}"
    dxy = (80, 500)
    c.text(dxy, dob, bold)
    c.field("DOB", c.char_span(dxy, dob, bold, 0, len(dob)))

    c.text((80, 640), "Permanent Account Number Card", font(SANS, 38), fill=(70, 70, 70))
    pan = fake_pan(rnd, last)
    pf = font(SANS_BOLD, 88)
    pxy = (80, 700)
    c.text(pxy, pan, pf)
    c.field("PAN", c.char_span(pxy, pan, pf, 0, 6))

    c.photo((1350, 220), 340, 400)
    c.draw.line([(1360, 760), (1450, 700), (1520, 780), (1640, 710)], fill=(30, 30, 90), width=6)  # signature
    c.qr((1150, 820), 230, rnd)
    return c, "PAN"


# --- placing on a background and making variants ----------------------------------------
def on_table(card: Card, rnd: random.Random) -> tuple[Image.Image, list[dict]]:
    bg = Image.new("RGB", (BG_W, BG_H), (120 + rnd.randint(-20, 20), 95, 70))
    noise = Image.effect_noise((BG_W, BG_H), 18).convert("RGB")
    bg = Image.blend(bg, noise, 0.15)
    shadow = Image.new("RGBA", (CARD_W, CARD_H), (0, 0, 0, 90)).filter(ImageFilter.GaussianBlur(4))
    bg.paste(shadow, (CARD_OFFSET[0] + 14, CARD_OFFSET[1] + 14), shadow)
    bg.paste(card.img, CARD_OFFSET)
    ox, oy = CARD_OFFSET
    fields = [{"type": f["type"], "rect": [f["rect"][0] + ox, f["rect"][1] + oy, f["rect"][2] + ox, f["rect"][3] + oy]}
              for f in card.fields]
    return bg, fields


def map_rects(fields, fn):
    out = []
    for f in fields:
        l, t, r, b = f["rect"]
        pts = [fn(x, y) for x, y in ((l, t), (r, t), (l, b), (r, b))]
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]
        out.append({"type": f["type"], "rect": [round(min(xs), 1), round(min(ys), 1), round(max(xs), 1), round(max(ys), 1)]})
    return out


def rotate_cw(img: Image.Image, fields, quarter_turns: int):
    for _ in range(quarter_turns):
        w, h = img.size
        img = img.transpose(Image.Transpose.ROTATE_270)  # PIL ROTATE_270 = 90° clockwise
        fields = map_rects(fields, lambda x, y, h=h: (h - y, x))
    return img, fields


def tilt(img: Image.Image, fields, degrees_cw: float):
    w, h = img.size
    rotated = img.rotate(-degrees_cw, resample=Image.BICUBIC, expand=True, fillcolor=(110, 90, 70))
    nw, nh = rotated.size
    a = math.radians(degrees_cw)

    def fn(x, y):
        dx, dy = x - w / 2, y - h / 2
        return (nw / 2 + dx * math.cos(a) - dy * math.sin(a), nh / 2 + dx * math.sin(a) + dy * math.cos(a))

    return rotated, map_rects(fields, fn)


def scale(img: Image.Image, fields, long_edge: int):
    w, h = img.size
    s = long_edge / max(w, h)
    img = img.resize((round(w * s), round(h * s)), Image.LANCZOS)
    return img, map_rects(fields, lambda x, y: (x * s, y * s))


def glare(img: Image.Image, rnd: random.Random):
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)
    cx, cy, r = int(w * 0.32), int(h * 0.30), int(min(w, h) * 0.18)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=210)
    mask = mask.filter(ImageFilter.GaussianBlur(r // 2))
    return Image.composite(Image.new("RGB", (w, h), (255, 255, 250)), img, mask)


def variants(base: Image.Image, fields, rnd: random.Random):
    yield "base", base, fields, 0, 92
    for k in (1, 2, 3):
        img, f = rotate_cw(base, fields, k)
        yield f"rot{90 * k}", img, f, 90 * k, 92
    for deg in (8, -8):
        img, f = tilt(base, fields, deg)
        yield f"tilt{'+' if deg > 0 else ''}{deg}", img, f, 0, 92
    yield "jpeg_q20", base, fields, 0, 20
    yield "dark", ImageEnhance.Brightness(base).enhance(0.42), fields, 0, 90
    yield "glare", glare(base, rnd), fields, 0, 92
    img, f = scale(base, fields, 800)
    yield "small800", img, f, 0, 92
    img, f = scale(base, fields, 4000)
    yield "large4000", img, f, 0, 85


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--seed", type=int, default=7)
    args = ap.parse_args()
    rnd = random.Random(args.seed)
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.jpg"):
        old.unlink()

    truth = []
    for name, make in (("aadhaar_front", aadhaar_front), ("aadhaar_back", aadhaar_back), ("pan", pan_card)):
        card, doc_type = make(rnd)
        base, fields = on_table(card, rnd)
        for variant, img, f, rotated_by, quality in variants(base, fields, rnd):
            file = f"{name}_{variant}.jpg"
            img.save(OUT / file, "JPEG", quality=quality)  # no EXIF written
            truth.append({
                "file": file,
                "docType": doc_type,
                "rotatedCw": rotated_by,  # the detector should turn it back by (360 - rotatedCw) % 360
                "width": img.size[0],
                "height": img.size[1],
                "fields": f,
            })
    (OUT / "ground_truth.json").write_text(json.dumps(truth, indent=1) + "\n")
    print(f"wrote {len(truth)} images to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
