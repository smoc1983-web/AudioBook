"""Optimize only known decorative resources in a disposable Test CI checkout.

Keep resource names, dimensions, alpha and original repository PNGs. Android API 23+
supports WebP. No changes to book covers, downloaded audio, launcher or text atlas.
"""
from pathlib import Path
from PIL import Image

root = Path("current/app/src/main/res/drawable-nodpi")
names = ("amber", "amethyst", "emerald", "ice", "lilac", "night", "obsidian", "onyx", "pearl")
before = after = 0
for name in names:
    source = root / f"ab_glass_{name}.png"
    target = source.with_suffix(".webp")
    assert source.is_file() and not target.exists(), source
    old_size = source.stat().st_size
    with Image.open(source) as original:
        original.load()
        size = original.size
        has_alpha = "A" in original.getbands() or "transparency" in original.info
        image = original.convert("RGBA" if has_alpha else "RGB")
        image.save(target, format="WEBP", quality=88, method=6, exact=True)
    with Image.open(target) as converted:
        converted.load()
        assert converted.size == size and converted.format == "WEBP"
        if has_alpha:
            assert converted.getchannel("A").tobytes() == image.getchannel("A").tobytes()
    new_size = target.stat().st_size
    before += old_size
    if new_size < old_size:
        source.unlink()
        after += new_size
        print(f"{name}: {old_size:,} -> {new_size:,} bytes")
    else:
        target.unlink()
        after += old_size
        print(f"{name}: original retained")
assert after < before, "No decorative resource size reduction"
print(f"Decorative resources: {before:,} -> {after:,} bytes; saved {before-after:,}")
