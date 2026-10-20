// Une los ficheros de shared/ en un único banco de preguntas y lo copia
// a la web (js/preguntas.js) y a la app Android (assets/preguntas.json).
// Los ficheros nuevos se añaden AL FINAL de su lista: así los ids de las preguntas ya existentes
// (f001…, n001…) no cambian y los dispositivos siguen sabiendo cuáles han salido.
// Uso: node tools/estimaciones.js && node tools/build-preguntas.js
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const read = (f) => JSON.parse(fs.readFileSync(path.join(root, 'shared', f), 'utf8'));
const FECHAS = ['fechas.json', 'fechas2.json'];
const NUMEROS = ['numeros.json', 'numeros2.json', 'estimaciones.json'];

const pad = (i) => String(i + 1).padStart(3, '0');
const preguntas = [
  ...FECHAS.flatMap(read).map((p, i) => ({ id: 'f' + pad(i), tipo: 'fecha', q: p.q, a: p.a, u: '', n: p.n || '' })),
  ...NUMEROS.flatMap(read).map((p, i) => ({ id: 'n' + pad(i), tipo: 'numero', q: p.q, a: p.a, u: p.u || '', n: p.n || '' })),
];

// Comprobaciones básicas
const vistos = new Map();
for (const p of preguntas) {
  if (typeof p.a !== 'number' || !isFinite(p.a)) throw new Error(`Respuesta no numérica: ${p.q}`);
  if (p.tipo === 'fecha' && !Number.isInteger(p.a)) throw new Error(`Año no entero: ${p.q}`);
  const clave = p.q.toLowerCase().normalize('NFD').replace(/[^a-z0-9]/g, '');
  if (vistos.has(clave)) throw new Error(`Pregunta repetida: ${p.q}`);
  vistos.set(clave, p.id);
}

const json = JSON.stringify(preguntas, null, 1);
fs.writeFileSync(path.join(root, 'js', 'preguntas.js'), '// Generado por tools/build-preguntas.js — no editar a mano.\nwindow.PREGUNTAS = ' + json + ';\n');
const assets = path.join(root, 'android', 'app', 'src', 'main', 'assets');
fs.mkdirSync(assets, { recursive: true });
fs.writeFileSync(path.join(assets, 'preguntas.json'), json);
console.log(`${preguntas.length} preguntas (${preguntas.filter((p) => p.tipo === 'fecha').length} fechas, ${preguntas.filter((p) => p.tipo === 'numero').length} números)`);
