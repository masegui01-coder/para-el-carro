// Publica los avatares ilustrados de shared/avatares/*.svg:
//  - web:     img/avatares/<id>.svg y js/avatares.js (lista con nombres)
//  - Android: res/drawable/av_<id>.xml (VectorDrawable) y ui/components/AvatarAssets.kt
// Los SVG solo usan circle, ellipse y path (con fill, stroke, stroke-width, linecap, linejoin y opacity),
// que se convierten 1:1 a VectorDrawable. Uso: node tools/build-avatares.js
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const src = path.join(root, 'shared', 'avatares');
const NOMBRES = {
  campesino: 'Campesino', campesina: 'Campesina', abuela: 'Abuela', abuelo: 'Abuelo', raton: 'Ratón', gato: 'Gato',
  loro: 'Loro', perro: 'Perro', cerdo: 'Cerdo', gallina: 'Gallina', oveja: 'Oveja', zorro: 'Zorro', rana: 'Rana',
  pirata: 'Pirata', bruja: 'Bruja', bandolero: 'Bandolero',
};
const ids = Object.keys(NOMBRES);

const attrs = (s) => Object.fromEntries([...s.matchAll(/([\w-]+)="([^"]*)"/g)].map((m) => [m[1], m[2]]));
const n = (x) => +(+x).toFixed(3);

function toPathData(tag, a) {
  if (tag === 'path') return a.d;
  const cx = +a.cx, cy = +a.cy;
  const rx = tag === 'circle' ? +a.r : +a.rx, ry = tag === 'circle' ? +a.r : +a.ry;
  return `M${n(cx - rx)},${n(cy)} a${n(rx)},${n(ry)} 0 1,0 ${n(2 * rx)},0 a${n(rx)},${n(ry)} 0 1,0 ${n(-2 * rx)},0 Z`;
}

function toVector(svg, id) {
  const out = [];
  for (const m of svg.matchAll(/<(circle|ellipse|path)\s([^>]*?)\/>/g)) {
    const a = attrs(m[2]);
    const p = [`android:pathData="${toPathData(m[1], a)}"`];
    if (a.fill && a.fill !== 'none') p.push(`android:fillColor="${a.fill}"`);
    if (a.stroke && a.stroke !== 'none') {
      p.push(`android:strokeColor="${a.stroke}"`, `android:strokeWidth="${a['stroke-width'] || 1}"`);
      if (a['stroke-linecap']) p.push(`android:strokeLineCap="${a['stroke-linecap']}"`);
      if (a['stroke-linejoin']) p.push(`android:strokeLineJoin="${a['stroke-linejoin']}"`);
    }
    if (a.opacity) p.push(`android:fillAlpha="${a.opacity}"`, `android:strokeAlpha="${a.opacity}"`);
    out.push(`    <path\n        ${p.join('\n        ')} />`);
  }
  if (!out.length) throw new Error(`Sin formas: ${id}`);
  return `<?xml version="1.0" encoding="utf-8"?>\n<!-- Generado por tools/build-avatares.js desde shared/avatares/${id}.svg -->\n` +
    `<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="100dp"\n    android:height="100dp"\n` +
    `    android:viewportWidth="100"\n    android:viewportHeight="100">\n${out.join('\n')}\n</vector>\n`;
}

const web = path.join(root, 'img', 'avatares');
const drawable = path.join(root, 'android', 'app', 'src', 'main', 'res', 'drawable');
fs.mkdirSync(web, { recursive: true });
fs.mkdirSync(drawable, { recursive: true });
for (const id of ids) {
  const svg = fs.readFileSync(path.join(src, `${id}.svg`), 'utf8');
  fs.writeFileSync(path.join(web, `${id}.svg`), svg);
  fs.writeFileSync(path.join(drawable, `av_${id}.xml`), toVector(svg, id));
}

fs.writeFileSync(path.join(root, 'js', 'avatares.js'),
  '// Generado por tools/build-avatares.js — no editar a mano.\n' +
  `window.AVATARES = ${JSON.stringify(ids.map((id) => ({ id: 'av:' + id, nombre: NOMBRES[id] })), null, 1)};\n`);

fs.writeFileSync(path.join(root, 'android', 'app', 'src', 'main', 'java', 'com', 'paraelcarro', 'ui', 'components', 'AvatarAssets.kt'),
  `package com.paraelcarro.ui.components

import com.paraelcarro.R

// Generado por tools/build-avatares.js — no editar a mano.
object AvatarAssets {
    /** Avatar ("av:id") -> dibujo vectorial. Los avatares antiguos con emoji se siguen mostrando como texto. */
    val drawables: Map<String, Int> = mapOf(
${ids.map((id) => `        "av:${id}" to R.drawable.av_${id},`).join('\n')}
    )

    val names: Map<String, String> = mapOf(
${ids.map((id) => `        "av:${id}" to "${NOMBRES[id]}",`).join('\n')}
    )
}
`);
console.log(`${ids.length} avatares publicados`);
