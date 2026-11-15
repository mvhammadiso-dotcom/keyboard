#!/usr/bin/env python3
"""
Microsoft Fluent Emoji (MIT) 3D PNG rasmlarini klaviatura uchun kichik WebP fayllarga aylantiradi.
Ishlatish:  python3 tools/fluent_emoji.py <fluentui-emoji/assets> <chiqish papkasi> [o'lcham]
Fayl nomi: emoji kod nuqtalari (FE0F'siz) pastki chiziq bilan, masalan 1f44b_1f3fd.webp
"""
import glob
import json
import os
import sys

from PIL import Image

TONES = {
    'default': None,
    'light': '1f3fb',
    'medium-light': '1f3fc',
    'medium': '1f3fd',
    'medium-dark': '1f3fe',
    'dark': '1f3ff',
}
MODS = set(v for v in TONES.values() if v)


def clean(seq):
    return [c for c in seq if c and c != 'fe0f']


def main():
    src, dst = sys.argv[1], sys.argv[2]
    size = int(sys.argv[3]) if len(sys.argv) > 3 else 96
    os.makedirs(dst, exist_ok=True)
    done = 0
    skipped = 0
    for name in sorted(os.listdir(src)):
        folder = os.path.join(src, name)
        meta_path = os.path.join(folder, 'metadata.json')
        if not os.path.isfile(meta_path):
            continue
        try:
            with open(meta_path, encoding='utf-8') as f:
                meta = json.load(f)
        except Exception:
            skipped += 1
            continue
        base = clean(str(meta.get('unicode', '')).lower().replace('-', ' ').split())
        tone_seqs = [clean(str(t).lower().replace('-', ' ').split()) for t in meta.get('unicodeSkintones', []) or []]
        if not base:
            skipped += 1
            continue
        for png in glob.glob(os.path.join(folder, '**', '3D', '*.png'), recursive=True):
            rel = os.path.relpath(png, folder).split(os.sep)
            tone_name = rel[0].lower() if len(rel) >= 3 else 'default'
            if tone_name not in TONES:
                continue
            mod = TONES[tone_name]
            if mod is None:
                cps = base
            else:
                match = [t for t in tone_seqs if mod in t and all((m == mod) for m in t if m in MODS)]
                if match:
                    cps = match[0]
                elif len(base) == 1:
                    cps = base + [mod]
                else:
                    continue
            out = os.path.join(dst, '_'.join(cps) + '.webp')
            try:
                im = Image.open(png).convert('RGBA')
                im = im.resize((size, size), Image.LANCZOS)
                im.save(out, 'WEBP', quality=80, method=6)
                done += 1
            except Exception as e:
                print('xato:', png, e)
    print(f'tayyor: {done} ta rasm, o\'tkazib yuborildi: {skipped}')


if __name__ == '__main__':
    main()
