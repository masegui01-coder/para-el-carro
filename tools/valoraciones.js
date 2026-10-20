// Muestra cómo valora la gente las preguntas (👍 😐 👎) y cuáles está apartando el juego.
// Uso: node tools/valoraciones.js
const preguntas = require('../android/app/src/main/assets/preguntas.json');
const URL = 'https://para-el-carro-91ke5-default-rtdb.europe-west1.firebasedatabase.app/ratings.json';
const MIN_VOTES = 5; // igual que Ratings.MIN_VOTES en js/game.js y RatingPolicy en Android

(async () => {
  const map = (await (await fetch(URL)).json()) || {};
  const filas = Object.entries(map).map(([id, c]) => {
    const up = c.up || 0, meh = c.meh || 0, down = c.down || 0, votos = up + meh + down;
    const p = preguntas.find((x) => x.id === id);
    return { id, up, meh, down, votos, nota: votos ? (up - down) / votos : 0, q: p ? p.q : '(pregunta borrada)' };
  }).sort((a, b) => a.nota - b.nota);
  const valoradas = filas.filter((f) => f.votos >= MIN_VOTES);
  const p20 = valoradas.length ? valoradas.map((f) => f.nota)[Math.floor(valoradas.length * 0.2)] : 0;
  console.log(`${filas.length} preguntas con votos (${valoradas.length} con ${MIN_VOTES} o más)\n`);
  const linea = (f) => `${f.id}  👍${f.up} 😐${f.meh} 👎${f.down}  nota ${f.nota.toFixed(2)}  ${f.q.slice(0, 80)}`;
  console.log('APARTADAS por el juego (nota negativa y en el 20 % peor):');
  valoradas.filter((f) => f.nota < 0 && f.nota <= p20).forEach((f) => console.log('  ' + linea(f)));
  console.log('\nLas 15 mejor valoradas:');
  valoradas.slice().reverse().slice(0, 15).forEach((f) => console.log('  ' + linea(f)));
})();
