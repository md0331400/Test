#!/usr/bin/env python3
"""Build a single self-contained index.html from the sources."""
import pathlib

root = pathlib.Path(__file__).parent
three = (root / 'three.min.js').read_text(encoding='utf-8')
css = (root / 'style.css').read_text(encoding='utf-8')
app = (root / 'app.js').read_text(encoding='utf-8')
tpl = (root / 'template.html').read_text(encoding='utf-8')

assert '</script>' not in three, 'three.min.js contains a closing script tag!'

out = tpl.replace('/*__THREE__*/', three).replace('/*__CSS__*/', css).replace('/*__APP__*/', app)
(root / 'index.html').write_text(out, encoding='utf-8')
print(f'index.html written: {len(out):,} bytes')
