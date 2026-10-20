// Une shared/fechas.json y shared/numeros.json en un único banco de preguntas y lo copia
// a la web (js/preguntas.js) y a la app Android (assets/preguntas.json).
// Uso: node tools/build-preguntas.js
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const read = (f) => JSON.parse(fs.readFileSync(path.join(root, 'shared', f), 'utf8'));

const pad = (i) => String(i + 1).padStart(3, '0');
const preguntas = [
  ...read('fechas.json').map((p, i) => ({ id: 'f' + pad(i), tipo: 'fecha', q: p.q, a: p.a, u: '', n: p.n || '' })),
  ...read('numeros.json').map((p, i) => ({ id: 'n' + pad(i), tipo: 'numero', q: p.q, a: p.a, u: p.u || '', n: p.n || '' })),
];

const json = JSON.stringify(preguntas, null, 1);
fs.writeFileSync(path.join(root, 'js', 'preguntas.js'), '// Generado por tools/build-preguntas.js — no editar a mano.\nwindow.PREGUNTAS = ' + json + ';\n');
const assets = path.join(root, 'android', 'app', 'src', 'main', 'assets');
fs.mkdirSync(assets, { recursive: true });
fs.writeFileSync(path.join(assets, 'preguntas.json'), json);
console.log(`${preguntas.length} preguntas (${preguntas.filter((p) => p.tipo === 'fecha').length} fechas)`);
