"""
Play Store gorselleri. Gercek cihaz ekran goruntulerini marka zemininde
cerceveliyor + baslik ekliyor. Uydurma mockup yok; hepsi calisan uygulamadan.

Kaynak: store/src/*.png  (ham cihaz ekran goruntusu, 1080x2340)
Cikti:  store/  (6 adet 1080x1920 ekran, feature-graphic 1024x500, icon 512x512)

Metinler INGILIZCE: listeleme global/Ingilizce oncelikli (bkz. CLAUDE.md bolum 5).

Yeniden uretmek icin: ekranlari store/src altina yeniden cek, sonra
  python store/make_store_assets.py
"""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = "store"
SRC = os.path.join(OUT, "src")
os.makedirs(OUT, exist_ok=True)

# Marka renkleri — Theme.kt ve colors.xml ile ayni
GREEN_DARK = (17, 34, 24)
GREEN = (46, 125, 82)
GREEN_LIGHT = (123, 228, 149)
GREEN_MID = (79, 180, 119)
ICON_BG = (15, 27, 42)
INK = (16, 20, 24)
WHITE = (240, 245, 240)

# Cihaz cercevesi: durum cubugu ve hareket cubugu magazada ise yaramiyor,
# ustelik gercek bildirim ikonlarini (WhatsApp, pil %80) sizdiriyor.
STATUS_BAR_PX = 100
GESTURE_BAR_PX = 28

FONT_DIR = r"C:\Windows\Fonts"


def font(name, size):
    for candidate in (name, "segoeuib.ttf", "arialbd.ttf", "arial.ttf"):
        path = os.path.join(FONT_DIR, candidate)
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


BOLD = lambda s: font("segoeuib.ttf", s)
REG = lambda s: font("segoeui.ttf", s)


def vertical_gradient(size, top, bottom):
    w, h = size
    base = Image.new("RGB", (1, h))
    d = ImageDraw.Draw(base)
    for y in range(h):
        t = y / max(h - 1, 1)
        d.point((0, y), fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return base.resize((w, h))


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0], img.size[1]], radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def wrap(draw, text, f, max_w):
    words, lines, cur = text.split(), [], ""
    for word in words:
        trial = (cur + " " + word).strip()
        if draw.textlength(trial, font=f) <= max_w:
            cur = trial
        else:
            if cur:
                lines.append(cur)
            cur = word
    if cur:
        lines.append(cur)
    return lines


def load_clean(src):
    """Ham ekran goruntusunden sistem cubuklarini kirp."""
    shot = Image.open(src).convert("RGB")
    return shot.crop((0, STATUS_BAR_PX, shot.width, shot.height - GESTURE_BAR_PX))


def screenshot(src, headline, sub, out_name):
    W, H = 1080, 1920
    canvas = vertical_gradient((W, H), GREEN_DARK, INK).convert("RGBA")
    draw = ImageDraw.Draw(canvas)

    f_head = BOLD(64)
    f_sub = REG(38)
    y = 96
    for line in wrap(draw, headline, f_head, W - 140):
        draw.text((70, y), line, font=f_head, fill=WHITE)
        y += 76
    y += 8
    for line in wrap(draw, sub, f_sub, W - 140):
        draw.text((70, y), line, font=f_sub, fill=GREEN_LIGHT)
        y += 48

    shot = load_clean(src)
    target_h = H - y - 120
    scale = target_h / shot.height
    new_w = int(shot.width * scale)
    if new_w > W - 160:
        scale = (W - 160) / shot.width
        new_w = W - 160
        target_h = int(shot.height * scale)
    shot = shot.resize((new_w, target_h), Image.LANCZOS)
    shot = rounded(shot, 44)

    x = (W - new_w) // 2
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        [x + 6, y + 30 + 6, x + new_w + 6, y + 30 + target_h + 6], 44, fill=(0, 0, 0, 130)
    )
    canvas = Image.alpha_composite(canvas, shadow.filter(ImageFilter.GaussianBlur(18)))
    canvas.paste(shot, (x, y + 30), shot)

    canvas.convert("RGB").save(os.path.join(OUT, out_name), quality=95)
    print("yazildi:", out_name)


def feature_graphic():
    W, H = 1024, 500
    canvas = Image.new("RGB", (W, H), GREEN_DARK)
    for x in range(W):
        t = x / (W - 1)
        c = tuple(int(GREEN_DARK[i] + (GREEN[i] - GREEN_DARK[i]) * t) for i in range(3))
        ImageDraw.Draw(canvas).line([(x, 0), (x, H)], fill=c)

    d = ImageDraw.Draw(canvas)
    d.ellipse([760, -120, 1180, 300], fill=(58, 150, 100))
    d.ellipse([840, 220, 1120, 500], fill=(38, 110, 72))

    d.text((70, 150), "Repzy", font=BOLD(96), fill=GREEN_LIGHT)
    d.text((74, 268), "Your training and your nutrition,", font=REG(38), fill=WHITE)
    d.text((74, 318), "in one coach", font=REG(38), fill=WHITE)
    canvas.save(os.path.join(OUT, "feature-graphic.png"), quality=95)
    print("yazildi: feature-graphic.png")


def app_icon():
    """
    512x512 magaza ikonu — Play zorunlu tutuyor.
    ic_launcher_foreground.xml'deki halter geometrisinin birebir kopyasi
    (108x108 viewport olceklenerek). Ikon degisirse burasi da degismeli.
    """
    SIZE = 512
    k = SIZE / 108.0
    canvas = Image.new("RGB", (SIZE, SIZE), ICON_BG)
    d = ImageDraw.Draw(canvas)

    # (x, y, genislik, yukseklik, renk) — vektordeki pathData ile ayni
    bars = [
        (40, 50, 28, 8, GREEN_LIGHT),   # orta cubuk
        (32, 42, 7, 24, GREEN_LIGHT),   # ic agirlik sol
        (69, 42, 7, 24, GREEN_LIGHT),   # ic agirlik sag
        (25, 46, 6, 16, GREEN_MID),     # dis agirlik sol
        (77, 46, 6, 16, GREEN_MID),     # dis agirlik sag
    ]
    for x, y, w, h, color in bars:
        d.rounded_rectangle(
            [x * k, y * k, (x + w) * k, (y + h) * k],
            radius=max(2, int(2 * k)),
            fill=color,
        )

    # Play ikonu seffaflik kabul etmiyor; kose yuvarlamayi Play kendisi uyguluyor.
    canvas.save(os.path.join(OUT, "icon-512.png"))
    print("yazildi: icon-512.png")


SHOTS = [
    ("01-home-top.png", "A coach that shows up daily",
     "It reads your own data and adapts as you improve", "01-home.png"),
    ("02-progress.png", "Progress, not just targets",
     "Calories, macros and your last 7 days at a glance", "02-progress.png"),
    ("03-nutrition.png", "Snap your meal, skip the typing",
     "AI estimates calories and macros from one photo", "03-nutrition.png"),
    ("04-workout.png", "Today's plan, already built",
     "Matched to your goal, level and equipment", "04-workout.png"),
    ("05-library.png", "Every movement, explained",
     "Filter by gym or home, muscle group and level", "05-library.png"),
    ("06-detail.png", "Proper form, step by step",
     "Common mistakes plus gym and home alternatives", "06-form.png"),
]

for src_name, head, sub, name in SHOTS:
    src = os.path.join(SRC, src_name)
    if os.path.exists(src):
        screenshot(src, head, sub, name)
    else:
        print("atlandi (kaynak yok):", src)

feature_graphic()
app_icon()
