// Para el Carro — versión web de prueba (pass-and-play, 3-8 jugadores en un mismo dispositivo).
// La lógica de ronda (Logic) es la misma que implementa la app Android en GameEngine.kt.
(function () {
  'use strict';

  const OPEN_SECONDS = 50;   // el primer jugador tiene 50 s para decir una cifra
  const TURN_SECONDS = 15;   // cada jugador tiene 15 s para subir, quedarse o bajarse
  const MIN_PLAYERS = 3, MAX_PLAYERS = 8;
  const AD_EVERY_ROUNDS = 2; // intersticial cada 2 rondas y antes del podio
  const AVATARS = ['🧔', '👩‍🦰', '🧙‍♂️', '🧝‍♀️', '🤠', '🏴‍☠️', '👸', '🤴', '🧛', '🧟', '🧑‍🌾', '👵',
    '👴', '🥷', '🧜‍♀️', '🦸', '🐸', '🐷', '🦊', '🐻', '🐵', '🐔', '👻', '🤡'];

  // ---------------------------------------------------------------- Lógica pura

  const Logic = {
    /** Una ronda recién creada: nadie ha dicho cifra todavía. */
    newRound(players, openerIdx, question) {
      return { q: question, openerIdx, bid: null, driver: null, status: {}, queue: [], log: [] };
    },

    /** Orden de turnos a partir del jugador siguiente a `fromId`, solo los que siguen en el carro. */
    queueAfter(players, R, fromId) {
      const i = players.findIndex((p) => p.id === fromId);
      const out = [];
      for (let k = 1; k < players.length; k++) {
        const p = players[(i + k) % players.length];
        if (R.status[p.id] === 'carro') out.push(p.id);
      }
      return out;
    },

    open(players, R, playerId, value) {
      R.bid = value; R.driver = playerId;
      players.forEach((p) => { R.status[p.id] = p.id === playerId ? 'conductor' : 'carro'; });
      R.queue = Logic.queueAfter(players, R, playerId);
      R.log.push({ id: playerId, action: 'abre', value });
    },

    /** action: 'subir' (con value > bid), 'quedar' o 'bajar'. Devuelve true si la ronda ha terminado. */
    act(players, R, playerId, action, value) {
      if (R.queue[0] !== playerId) throw new Error('No es su turno');
      if (action === 'subir') {
        if (!(value > R.bid)) throw new Error('La cifra debe ser mayor');
        R.status[R.driver] = 'carro';
        R.status[playerId] = 'conductor';
        R.driver = playerId; R.bid = value;
        R.queue = Logic.queueAfter(players, R, playerId);
      } else {
        if (action === 'bajar') R.status[playerId] = 'bajado';
        R.queue.shift();
      }
      R.log.push({ id: playerId, action, value });
      return R.queue.length === 0;
    },

    /** ¿La puja quedó cerca de la respuesta real? (bonus "¡Clavada!") */
    isBullseye(q, bid) {
      if (bid > q.a) return false;
      return q.tipo === 'fecha' ? q.a - bid <= 5 : bid >= q.a * 0.9;
    },

    /** Puntos de la ronda. La burra aguanta si la respuesta real es IGUAL O MAYOR que la cifra. */
    score(players, R) {
      const success = R.q.a >= R.bid;
      const passengers = players.filter((p) => R.status[p.id] === 'carro').map((p) => p.id);
      const off = players.filter((p) => R.status[p.id] === 'bajado').map((p) => p.id);
      const bullseye = success && Logic.isBullseye(R.q, R.bid);
      const delta = {};
      players.forEach((p) => { delta[p.id] = 0; });
      if (success) {
        delta[R.driver] = 3 + passengers.length + (bullseye ? 2 : 0);
        passengers.forEach((id) => { delta[id] = 3; });
      } else {
        delta[R.driver] = -3 - off.length;          // paga 1 punto a cada uno que se bajó
        passengers.forEach((id) => { delta[id] = -2; });
        off.forEach((id) => { delta[id] = 2 + 1; });
      }
      return { success, bullseye, delta, passengers, off };
    },
  };

  // ---------------------------------------------------------------- Utilidades

  const $app = document.getElementById('app');
  const $banner = document.getElementById('ad-banner');
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const nf = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 2 });

  function fmt(v, tipo) {
    if (v === null || v === undefined || Number.isNaN(v)) return '—';
    if (tipo === 'fecha') return v < 0 ? `${-v} a.C.` : String(v);
    return nf.format(v);
  }
  const fmtQ = (v, q) => fmt(v, q.tipo) + (q.tipo === 'numero' && q.u ? ' ' + q.u : '');

  /** "7.400.000" -> 7400000 ; "13,7" -> 13.7 (formato español). */
  function parseCifra(str, tipo, antesDeCristo) {
    const s = String(str).trim().replace(/\s/g, '').replace(/\./g, '').replace(',', '.');
    if (!/^\d+(\.\d+)?$/.test(s)) return null;
    let v = Number(s);
    if (tipo === 'fecha') { v = Math.round(v); if (antesDeCristo) v = -v; }
    return v;
  }

  const store = {
    get(k, def) { try { const v = localStorage.getItem('pec.' + k); return v ? JSON.parse(v) : def; } catch (e) { return def; } },
    set(k, v) { try { localStorage.setItem('pec.' + k, JSON.stringify(v)); } catch (e) { /* sin almacenamiento */ } },
  };

  // Sonido sintetizado (sin ficheros) y vibración
  let audio = null;
  function tone(freq, dur, type = 'sine', vol = .15, delay = 0) {
    try {
      audio = audio || new (window.AudioContext || window.webkitAudioContext)();
      const o = audio.createOscillator(), g = audio.createGain();
      const t0 = audio.currentTime + delay;
      o.type = type; o.frequency.setValueAtTime(freq, t0);
      g.gain.setValueAtTime(vol, t0); g.gain.exponentialRampToValueAtTime(.001, t0 + dur);
      o.connect(g).connect(audio.destination); o.start(t0); o.stop(t0 + dur);
      return o;
    } catch (e) { return null; }
  }
  const sfx = {
    tick() { tone(880, .08, 'square', .05); },
    click() { tone(520, .05, 'triangle', .08); },
    creak() { const o = tone(90, 1.2, 'sawtooth', .05); if (o) o.frequency.linearRampToValueAtTime(140, audio.currentTime + 1.2); },
    win() { [523, 659, 784, 1046].forEach((f, i) => tone(f, .25, 'triangle', .14, i * .12)); },
    lose() {
      const o = tone(400, .9, 'sawtooth', .1); if (o) o.frequency.exponentialRampToValueAtTime(60, audio.currentTime + .9);
      tone(140, .5, 'sine', .2, .85);
    },
  };
  const vibrate = (p) => { try { navigator.vibrate && navigator.vibrate(p); } catch (e) { /* no soportado */ } };

  let timer = null;
  function stopTimer() { if (timer) { clearInterval(timer); timer = null; } }
  /**
   * Cuenta atrás que actualiza la barra; llama a onEnd al llegar a cero.
   * En las salas online, `endAt` es la hora de fin compartida y `now` el reloj del servidor.
   */
  function startTimer(seconds, onEnd, endAt, now = Date.now) {
    stopTimer();
    const end = endAt || now() + seconds * 1000;
    const clock = endAt ? now : Date.now;
    let lastSec = seconds + 1;
    const tickFn = () => {
      const left = Math.min(seconds * 1000, Math.max(0, end - clock()));
      const sec = Math.ceil(left / 1000);
      const bar = document.querySelector('.temporizador > div');
      const num = document.querySelector('.segundos');
      if (bar) bar.style.width = (left / (seconds * 1000)) * 100 + '%';
      if (num) { num.textContent = sec; num.classList.toggle('urgente', sec <= 5); }
      if (sec !== lastSec && sec <= 5 && sec > 0) sfx.tick();
      lastSec = sec;
      if (left <= 0) { stopTimer(); onEnd(); }
    };
    tickFn();
    timer = setInterval(tickFn, 100);
  }

  function showBanner(show) { $banner.classList.toggle('oculto', !show); }

  function render(html, banner = false) {
    stopTimer();
    $app.innerHTML = html;
    showBanner(banner);
    window.scrollTo(0, 0);
  }

  const on = (sel, ev, fn) => { const el = $app.querySelector(sel); if (el) el.addEventListener(ev, fn); return el; };
  const playerById = (id) => G.players.find((p) => p.id === id);
  const tag = (p) => `${p.avatar} ${esc(p.name)}`;

  // ---------------------------------------------------------------- Estado de la partida

  const G = {
    players: store.get('players', null) || [
      { id: 1, name: 'Jugador 1', avatar: AVATARS[0] },
      { id: 2, name: 'Jugador 2', avatar: AVATARS[1] },
      { id: 3, name: 'Jugador 3', avatar: AVATARS[2] },
    ],
    rounds: store.get('rounds', 6),
    mode: store.get('mode', 'mezcla'), // 'fecha' | 'numero' | 'mezcla'
    round: 0,
    R: null,
    openAttempts: 0,
  };

  // ---------------------------------------------------------------- Valoraciones de preguntas

  const Ratings = {
    MIN_VOTES: 5,
    excluded: new Set(),

    /**
     * Preguntas apartadas: con al menos MIN_VOTES votos, nota negativa (más 👎 que 👍)
     * y en el 20 % peor de las preguntas valoradas.
     */
    computeExcluded(map) {
      const scored = Object.entries(map || {}).map(([id, c]) => {
        const up = c.up || 0, meh = c.meh || 0, down = c.down || 0;
        const votes = up + meh + down;
        return { id, votes, score: votes ? (up - down) / votes : 0 };
      }).filter((x) => x.votes >= Ratings.MIN_VOTES);
      if (!scored.length) return new Set();
      const sorted = scored.map((x) => x.score).sort((a, b) => a - b);
      const p20 = sorted[Math.floor(sorted.length * 0.2)];
      return new Set(scored.filter((x) => x.score < 0 && x.score <= p20).map((x) => x.id));
    },

    refresh() {
      if (!window.Online) return;
      window.Online.loadRatings().then((map) => { Ratings.excluded = Ratings.computeExcluded(map); });
    },

    /** Botones 👍 😐 👎 (opcionales). Cada dispositivo vota una vez por pregunta. */
    widget(qid) {
      const done = store.get('rated', []).includes(qid);
      return `<div class="valorar" data-q="${esc(qid)}">${done ? '<span class="suave">✅ Ya has valorado esta pregunta</span>'
        : '<span class="suave">¿Qué te ha parecido la pregunta?</span><span class="fila"><button class="btn fantasma peq" data-r="up" aria-label="Me ha gustado">👍</button><button class="btn fantasma peq" data-r="meh" aria-label="Sin más">😐</button><button class="btn fantasma peq" data-r="down" aria-label="No me ha gustado">👎</button></span>'}</div>`;
    },

    bind() {
      $app.querySelectorAll('.valorar [data-r]').forEach((b) => b.addEventListener('click', () => {
        const box = b.closest('.valorar');
        const qid = box.dataset.q;
        if (window.Online) window.Online.rate(qid, b.dataset.r);
        store.set('rated', [...store.get('rated', []), qid].slice(-3000));
        box.innerHTML = '<span class="suave">¡Gracias! Tu voto ayuda a elegir las mejores preguntas.</span>';
      }));
    },
  };

  function pickQuestion(mode = G.mode) {
    const all = window.PREGUNTAS.filter((q) => (mode === 'mezcla' || q.tipo === mode) && !Ratings.excluded.has(q.id));
    let used = store.get('used', []);
    let pool = all.filter((q) => !used.includes(q.id));
    if (pool.length === 0) { used = used.filter((id) => !all.some((q) => q.id === id)); pool = all; }
    const q = pool[Math.floor(Math.random() * pool.length)];
    used.push(q.id);
    store.set('used', used);
    return q;
  }

  // ---------------------------------------------------------------- Pantallas

  function screenHome() {
    render(`
      <section class="portada">
        <img class="portada-img" src="img/portada.jpg" alt="Un campesino salta del carro mientras el caballo frena al borde del barranco"
          onerror="this.outerHTML='&lt;div class=&quot;burra&quot;&gt;🐴🛒&lt;/div&gt;'">
        <h1>Para el Carro</h1>
        <p class="suave">Sube la cifra, carga el carro… y reza para que el puente aguante.</p>
        <div class="col" style="margin-top:24px">
          <button class="btn ambar bloque" id="crear">🌐 Crear sala privada</button>
          <button class="btn musgo bloque" id="unirse">🔑 Unirme con un código</button>
          <button class="btn bloque" id="jugar">📱 Jugar en un solo móvil</button>
          <button class="btn fantasma bloque" id="reglas">Cómo se juega</button>
        </div>
      </section>`, true);
    on('#jugar', 'click', () => { sfx.click(); screenLobby(); });
    on('#crear', 'click', () => { sfx.click(); window.Online.screenCreate(); });
    on('#unirse', 'click', () => { sfx.click(); window.Online.screenJoin(''); });
    on('#reglas', 'click', screenRules);
  }

  /** Rejilla de avatares en un modal; los ocupados salen desactivados. */
  function avatarPicker(current, used, onPick) {
    const modal = document.createElement('div');
    modal.className = 'modal';
    modal.innerHTML = `<div class="panel"><h2>Elige avatar</h2><div class="rejilla-avatares">
      ${AVATARS.map((a) => `<button data-a="${a}" class="${a === current ? 'sel' : ''}" ${used.includes(a) ? 'disabled' : ''}>${a}</button>`).join('')}
      </div></div>`;
    modal.addEventListener('click', (e) => {
      const a = e.target.closest('[data-a]');
      if (a || e.target === modal) { modal.remove(); onPick(a ? a.dataset.a : null); }
    });
    document.body.appendChild(modal);
  }

  function screenRules() {
    render(`
      <h2>Cómo se juega</h2>
      <div class="panel">
        <ol class="reglas">
          <li>Sale una pregunta cuya respuesta es <b>un año o un número</b>.</li>
          <li>El primer jugador tiene <b>${OPEN_SECONDS} s</b> para decir una cifra. Se monta en la burra: es el <b>conductor</b>.</li>
          <li>Por turnos, cada jugador tiene <b>${TURN_SECONDS} s</b> para:
            <br>⬆️ <b>Subir la cifra</b> (pasa a ser el conductor y el anterior se sube al carro),
            <br>🛒 <b>Quedarse en el carro</b>, o
            <br>🚶 <b>Bajarse</b> (ya no puede volver a subir ni pujar en esta ronda).</li>
          <li>Se sigue dando vueltas hasta que nadie sube la cifra.</li>
          <li>El carro cruza el puente si la <b>respuesta real es igual o mayor</b> que la cifra del conductor. Si no… ¡al río!</li>
        </ol>
      </div>
      <h2>Puntos</h2>
      <div class="panel">
        <table class="puntos">
          <tr><td></td><td class="num">Cruza</td><td class="num">Al río</td></tr>
          <tr><td>🐴 Conductor</td><td class="num mas">+3 +1 por pasajero</td><td class="num menos">−3 −1 por cada bajado</td></tr>
          <tr><td>🛒 En el carro</td><td class="num mas">+3</td><td class="num menos">−2</td></tr>
          <tr><td>🚶 Bajados</td><td class="num">0</td><td class="num mas">+3</td></tr>
        </table>
        <p class="suave">🎯 <b>¡Clavada!</b> +2 extra al conductor si cruza y su cifra está a menos del 10 % de la respuesta (o a 5 años en las fechas).</p>
      </div>
      <button class="btn bloque" id="volver">Volver</button>`, true);
    on('#volver', 'click', screenHome);
  }

  function screenLobby() {
    const taken = (exceptId) => G.players.filter((p) => p.id !== exceptId).map((p) => p.avatar);
    const rows = G.players.map((p, i) => `
      <div class="jugador">
        <button class="avatar-btn" data-avatar="${p.id}" aria-label="Cambiar avatar">${p.avatar}</button>
        <input type="text" maxlength="14" value="${esc(p.name)}" data-name="${p.id}" aria-label="Nombre">
        ${i === 0 ? '<span class="corona">👑 Jefe</span>' : `<button class="btn fantasma peq" data-del="${p.id}" ${G.players.length <= MIN_PLAYERS ? 'disabled' : ''}>✕</button>`}
      </div>`).join('');
    render(`
      <div class="cabecera"><button class="btn fantasma peq" id="atras">← Inicio</button><span>Sala</span></div>
      <h2 style="margin-top:12px">Jugadores (${G.players.length}/${MAX_PLAYERS})</h2>
      <div class="panel">
        ${rows}
        <button class="btn fantasma bloque peq" id="add" ${G.players.length >= MAX_PLAYERS ? 'disabled' : ''}>+ Añadir jugador</button>
      </div>
      <h2>👑 El jefe de sala decide</h2>
      <div class="panel col">
        <div class="fila"><span class="crece">Número de rondas</span>
          <div class="contador"><button class="btn peq" id="menos">−</button><b id="nr">${G.rounds}</b><button class="btn peq" id="mas">+</button></div>
        </div>
        <div>Tipo de preguntas</div>
        <div class="segmentado">
          ${[['fecha', '📅 Fechas'], ['numero', '🔢 Números'], ['mezcla', '🎲 Mezcla']].map(([k, l]) => `<button data-mode="${k}" class="${G.mode === k ? 'sel' : ''}">${l}</button>`).join('')}
        </div>
      </div>
      <button class="btn ambar bloque" id="empezar">¡Arre, burra! Empezar</button>`, true);

    const save = () => { store.set('players', G.players); store.set('rounds', G.rounds); store.set('mode', G.mode); };
    on('#atras', 'click', screenHome);
    $app.querySelectorAll('[data-name]').forEach((el) => el.addEventListener('input', () => {
      playerById(+el.dataset.name).name = el.value; save();
    }));
    $app.querySelectorAll('[data-del]').forEach((el) => el.addEventListener('click', () => {
      G.players = G.players.filter((p) => p.id !== +el.dataset.del); save(); screenLobby();
    }));
    $app.querySelectorAll('[data-mode]').forEach((el) => el.addEventListener('click', () => {
      G.mode = el.dataset.mode; save(); screenLobby();
    }));
    $app.querySelectorAll('[data-avatar]').forEach((el) => el.addEventListener('click', () => {
      const p = playerById(+el.dataset.avatar);
      avatarPicker(p.avatar, taken(p.id), (a) => {
        if (a) { p.avatar = a; save(); }
        screenLobby();
      });
    }));
    on('#add', 'click', () => {
      const id = Math.max(...G.players.map((p) => p.id)) + 1;
      const avatar = AVATARS.find((a) => !G.players.some((p) => p.avatar === a));
      G.players.push({ id, name: `Jugador ${G.players.length + 1}`, avatar });
      save(); screenLobby();
    });
    on('#menos', 'click', () => { G.rounds = Math.max(1, G.rounds - 1); save(); $app.querySelector('#nr').textContent = G.rounds; });
    on('#mas', 'click', () => { G.rounds = Math.min(30, G.rounds + 1); save(); $app.querySelector('#nr').textContent = G.rounds; });
    on('#empezar', 'click', () => {
      G.players.forEach((p, i) => { p.name = p.name.trim() || `Jugador ${i + 1}`; p.score = 0; });
      save();
      G.round = 0;
      nextRound();
    });
  }

  function nextRound() {
    G.round++;
    const opener = (G.round - 1) % G.players.length;
    G.R = Logic.newRound(G.players, opener, pickQuestion());
    G.openAttempts = 0;
    screenHandoff(G.players[opener], screenOpen, `Abre la ronda ${G.round} de ${G.rounds}`);
  }

  function header() {
    return `<div class="cabecera"><span>Ronda ${G.round}/${G.rounds}</span><span class="etiqueta ${G.R.q.tipo}">${G.R.q.tipo === 'fecha' ? '📅 Fecha' : '🔢 Número'}</span></div>`;
  }

  function questionPanel() {
    return `<div class="panel"><div class="pregunta">${esc(G.R.q.q)}</div></div>`;
  }

  /** "Pasa el móvil a…": el temporizador no empieza hasta que el jugador lo coge. */
  function screenHandoff(player, next, subtitle) {
    render(`
      ${G.R ? header() : ''}
      <div class="pasa-movil">
        <div class="gigante">${player.avatar}</div>
        <p class="suave">${esc(subtitle || 'Pasa el móvil a')}</p>
        <h1>${esc(player.name)}</h1>
        <button class="btn ambar bloque" id="listo" style="margin-top:20px">¡Soy yo, empezar!</button>
      </div>`);
    on('#listo', 'click', () => { sfx.click(); next(player); });
  }

  /** Teclado de cifra con vista previa formateada y botones rápidos. */
  function cifraInput(q, placeholder) {
    return `
      <input type="text" id="cifra" inputmode="decimal" autocomplete="off" placeholder="${esc(placeholder)}">
      ${q.tipo === 'fecha' ? '<label class="fila suave" style="gap:6px"><input type="checkbox" id="ac"> Antes de Cristo (a.C.)</label>' : ''}
      <div class="vista-previa" id="preview"></div>`;
  }

  function bindCifra(q, onChange) {
    const input = $app.querySelector('#cifra');
    const ac = $app.querySelector('#ac');
    const read = () => parseCifra(input.value, q.tipo, ac && ac.checked);
    const update = () => {
      const v = read();
      $app.querySelector('#preview').textContent = v === null ? '' : '= ' + fmtQ(v, q);
      onChange && onChange(v);
    };
    input.addEventListener('input', update);
    if (ac) ac.addEventListener('change', update);
    return {
      read,
      set(v) {
        if (ac) { ac.checked = v < 0; input.value = String(Math.abs(v)); } else input.value = String(v).replace('.', ',');
        update();
      },
      focus() { input.focus(); },
    };
  }

  function screenOpen(player) {
    const q = G.R.q;
    render(`
      ${header()}
      ${questionPanel()}
      <p class="centro">${tag(player)}, di una cifra para arrancar.<br><span class="suave">Te montarás en la burra como conductor.</span></p>
      <div class="segundos">${OPEN_SECONDS}</div>
      <div class="temporizador"><div style="width:100%"></div></div>
      <div class="panel col">
        ${cifraInput(q, q.tipo === 'fecha' ? 'Año, p. ej. 1890' : 'Tu cifra')}
        ${q.tipo === 'numero' ? '<div class="botones-rapidos"><button class="btn peq" data-mul="1000">×1.000</button><button class="btn peq" data-mul="1000000">×1 millón</button></div>' : ''}
        <button class="btn ambar bloque" id="ok" disabled>🐴 ¡Me subo a la burra!</button>
      </div>`);
    const okBtn = $app.querySelector('#ok');
    const field = bindCifra(q, (v) => { okBtn.disabled = v === null; });
    $app.querySelectorAll('[data-mul]').forEach((b) => b.addEventListener('click', () => {
      const v = field.read();
      if (v !== null) field.set(v * Number(b.dataset.mul));
    }));
    field.focus();
    const commit = (v) => {
      Logic.open(G.players, G.R, player.id, v);
      vibrate(30);
      afterAction();
    };
    okBtn.addEventListener('click', () => { const v = field.read(); if (v !== null) commit(v); });
    startTimer(OPEN_SECONDS, () => {
      const v = field.read();
      if (v !== null) return commit(v);
      // Sin cifra: abre el siguiente jugador.
      G.openAttempts++;
      const nextP = G.players[(G.R.openerIdx + G.openAttempts) % G.players.length];
      screenHandoff(nextP, screenOpen, `¡Tiempo! ${player.name} no dijo nada. Abre`);
    });
  }

  /** Quién va en la burra, en el carro o bajado, y las últimas jugadas. `turnId` resalta a quien le toca. */
  function cartPanel(players = G.players, R = G.R, turnId = null) {
    const chips = players.map((p) => {
      const st = R.status[p.id];
      const cls = st === 'conductor' ? 'conductor' : st === 'bajado' ? 'bajado' : '';
      const ico = st === 'conductor' ? '🐴' : st === 'bajado' ? '🚶' : '🛒';
      const off = p.online === false ? ' 📴' : '';
      return `<span class="chip ${cls}${p.id === turnId ? ' turno' : ''}">${ico} ${p.avatar} ${esc(p.name)}${off}</span>`;
    }).join('');
    const log = (R.log || []).slice(-6).reverse().map((l) => {
      const p = players.find((x) => x.id === l.id);
      if (!p) return '';
      const txt = { abre: `abre con ${fmtQ(l.value, R.q)}`, subir: `sube a ${fmtQ(l.value, R.q)}`, quedar: 'se queda en el carro', bajar: 'se baja del carro', tiempo: 'se queda (tiempo)' }[l.action];
      return `<li>${p.avatar} ${esc(p.name)} ${txt}</li>`;
    }).join('');
    return `<div class="panel"><div class="pasajeros">${chips}</div>${log ? `<ul class="registro" style="margin-top:8px">${log}</ul>` : ''}</div>`;
  }

  function afterAction() {
    const R = G.R;
    if (R.queue.length === 0) return screenBridge();
    screenHandoff(playerById(R.queue[0]), screenTurn);
  }

  function screenTurn(player) {
    const R = G.R, q = R.q;
    const driver = playerById(R.driver);
    const stepBtns = q.tipo === 'fecha'
      ? [['+1', 1], ['+5', 5], ['+25', 25], ['+100', 100]]
      : [['+1', 'a1'], ['+10 %', 'p10'], ['×2', 'x2'], ['×10', 'x10']];
    render(`
      ${header()}
      ${questionPanel()}
      <div class="cifra-sub">${tag(driver)} va en la burra con</div>
      <div class="cifra-grande">${esc(fmtQ(R.bid, q))}</div>
      <div class="segundos" style="margin-top:8px">${TURN_SECONDS}</div>
      <div class="temporizador"><div style="width:100%"></div></div>
      <p class="centro">${tag(player)}, ¿qué haces?</p>
      <div class="col">
        <button class="btn ambar bloque" id="subir">⬆️ Subir la cifra</button>
        <div class="panel col" id="panel-subir" hidden>
          <div class="botones-rapidos">${stepBtns.map(([l, s]) => `<button class="btn peq" data-step="${s}">${l}</button>`).join('')}</div>
          ${cifraInput(q, 'Nueva cifra (mayor)')}
          <button class="btn ambar bloque" id="ok-subir" disabled>🐴 Me monto en la burra</button>
        </div>
        <button class="btn musgo bloque" id="quedar">🛒 Me quedo en el carro</button>
        <button class="btn sangre bloque" id="bajar">🚶 Me bajo del carro</button>
      </div>
      ${cartPanel()}`);

    const okBtn = $app.querySelector('#ok-subir');
    const field = bindCifra(q, (v) => { okBtn.disabled = v === null || !(v > R.bid); });
    const raise = (step) => {
      const b = R.bid;
      if (typeof step === 'number') return b + step;
      if (step === 'a1') return b + 1;
      if (step === 'p10') return Math.max(b + 1, Math.ceil(b * 1.1));
      if (step === 'x2') return b > 0 ? b * 2 : b + 1;
      return b > 0 ? b * 10 : b + 1;
    };
    $app.querySelectorAll('[data-step]').forEach((el) => el.addEventListener('click', () => {
      const s = el.dataset.step;
      field.set(raise(/^\d+$/.test(s) ? Number(s) : s));
    }));
    let done = false;
    const act = (action, value, timedOut = false) => {
      if (done) return;
      done = true;
      Logic.act(G.players, G.R, player.id, action, value);
      if (timedOut) G.R.log[G.R.log.length - 1].action = 'tiempo';
      if (action === 'subir') vibrate([20, 40, 20]);
      afterAction();
    };
    on('#subir', 'click', () => {
      $app.querySelector('#panel-subir').hidden = false;
      $app.querySelector('#subir').hidden = true;
      field.set(raise(q.tipo === 'fecha' ? 1 : 'a1'));
      field.focus();
    });
    okBtn.addEventListener('click', () => { const v = field.read(); if (v !== null && v > R.bid) act('subir', v); });
    on('#quedar', 'click', () => act('quedar'));
    on('#bajar', 'click', () => act('bajar'));
    startTimer(TURN_SECONDS, () => {
      const v = field.read();
      const raising = !$app.querySelector('#panel-subir').hidden;
      if (raising && v !== null && v > R.bid) return act('subir', v);
      // Si se acaba el tiempo, se queda en el carro.
      act('quedar', undefined, true);
    });
  }

  function screenBridge() {
    const R = G.R, q = R.q;
    const result = Logic.score(G.players, R);
    const driver = playerById(R.driver);
    const passengers = result.passengers.map((id) => playerById(id).avatar);
    render(`
      ${header()}
      <div class="panel"><div class="pregunta" style="font-size:1.05rem">${esc(q.q)}</div></div>
      <div class="escena">
        <canvas id="puente" width="400" height="260"></canvas>
        <div class="revelacion" id="rev">${driver.avatar} apuesta por <span style="color:var(--ambar-claro)">${esc(fmtQ(R.bid, q))}</span></div>
      </div>
      <div id="fin"></div>
      <button class="btn fantasma bloque peq" id="saltar" style="margin-top:10px">Saltar ⏩</button>`);
    const scene = window.BridgeScene.play($app.querySelector('#puente'), {
      driver: driver.avatar,
      passengers,
      success: result.success,
      onSuspense() {
        sfx.creak();
        vibrate(80);
        $app.querySelector('#rev').innerHTML = `Respuesta real: <span style="color:${result.success ? '#8fd18f' : '#ff8a73'}">${esc(fmtQ(q.a, q))}</span>`;
      },
      onImpact(ok) {
        if (ok) { sfx.win(); vibrate([40, 60, 40, 60, 120]); } else { sfx.lose(); vibrate([300, 80, 500]); }
      },
    });
    on('#saltar', 'click', () => scene.skip());
    scene.done.then(() => {
      G.players.forEach((p) => { p.score += result.delta[p.id]; });
      screenRoundScore(result);
    });
  }

  function screenRoundScore(result) {
    const R = G.R, q = R.q;
    const driver = playerById(R.driver);
    const rows = G.players.slice().sort((a, b) => b.score - a.score).map((p) => {
      const d = result.delta[p.id];
      const st = R.status[p.id];
      const ico = st === 'conductor' ? '🐴' : st === 'bajado' ? '🚶' : '🛒';
      return `<tr><td>${ico} ${tag(p)}</td><td class="num ${d > 0 ? 'mas' : d < 0 ? 'menos' : ''}">${d > 0 ? '+' : ''}${d}</td><td class="num">${p.score}</td></tr>`;
    }).join('');
    const last = G.round >= G.rounds;
    render(`
      ${header()}
      <div class="resultado-titulo ${result.success ? 'ok' : 'ko'}">${result.success ? '¡El carro cruzó el puente!' : '¡Al río con la burra!'}</div>
      ${result.bullseye ? '<p class="centro" style="color:var(--ambar-claro);font-weight:800">🎯 ¡Clavada! +2 para el conductor</p>' : ''}
      <div class="panel">
        <div class="pregunta" style="font-size:1rem">${esc(q.q)}</div>
        <p>Respuesta: <b style="color:var(--ambar-claro)">${esc(fmtQ(q.a, q))}</b> · ${tag(driver)} dijo <b>${esc(fmtQ(R.bid, q))}</b></p>
        ${q.n ? `<p class="suave" style="margin:0">💡 ${esc(q.n)}</p>` : ''}
        ${Ratings.widget(q.id)}
      </div>
      <div class="panel"><table class="puntos">
        <tr class="suave"><td>Jugador</td><td class="num">Ronda</td><td class="num">Total</td></tr>${rows}
      </table></div>
      <button class="btn ambar bloque" id="seguir">${last ? '🏆 Ver el podio' : 'Siguiente ronda ➜'}</button>`, true);
    on('#seguir', 'click', () => {
      const next = last ? screenFinal : nextRound;
      if (last || G.round % AD_EVERY_ROUNDS === 0) showInterstitial(next); else next();
    });
    Ratings.bind();
  }

  /** Simulación del anuncio intersticial (en Android lo sirve AdMob). */
  function showInterstitial(onClose) {
    stopTimer();
    const el = document.createElement('div');
    el.className = 'intersticial';
    el.innerHTML = '<div class="suave">Anuncio</div><div class="caja">Aquí irá un anuncio a pantalla completa</div><button class="btn fantasma" id="cerrar-ad" disabled>Cerrar en 5</button>';
    document.body.appendChild(el);
    const btn = el.querySelector('#cerrar-ad');
    let n = 5;
    const iv = setInterval(() => {
      n--;
      if (n > 0) { btn.textContent = `Cerrar en ${n}`; return; }
      clearInterval(iv); btn.disabled = false; btn.textContent = 'Cerrar ✕';
    }, 1000);
    btn.addEventListener('click', () => { el.remove(); onClose(); });
  }

  function screenFinal() {
    const ranking = G.players.slice().sort((a, b) => b.score - a.score);
    const [p1, p2, p3] = ranking;
    const peana = (p, h, pos) => p ? `<div><div class="av">${p.avatar}</div><div>${esc(p.name)}</div><div class="peana" style="height:${h}px">${pos}<br>${p.score}</div></div>` : '<div></div>';
    render(`
      <h1 class="centro">🏆 ${esc(p1.name)} gana</h1>
      <div class="podio">${peana(p2, 70, '2º')}${peana(p1, 100, '1º')}${peana(p3, 50, '3º')}</div>
      <div class="panel"><table class="puntos">
        ${ranking.map((p, i) => `<tr><td>${i + 1}. ${tag(p)}</td><td class="num">${p.score}</td></tr>`).join('')}
      </table></div>
      <div class="col">
        <button class="btn ambar bloque" id="revancha">Revancha (mismos jugadores)</button>
        <button class="btn fantasma bloque" id="sala">Cambiar jugadores o rondas</button>
      </div>`, true);
    sfx.win();
    on('#revancha', 'click', () => { G.players.forEach((p) => { p.score = 0; }); G.round = 0; nextRound(); });
    on('#sala', 'click', screenLobby);
  }

  // Piezas compartidas con el modo online (js/online.js)
  window.ParaElCarro = {
    Logic, AVATARS, OPEN_SECONDS, TURN_SECONDS, MIN_PLAYERS, MAX_PLAYERS, AD_EVERY_ROUNDS,
    esc, fmtQ, parseCifra, store, sfx, vibrate, render, on, startTimer, stopTimer,
    pickQuestion, avatarPicker, cifraInput, bindCifra, cartPanel, showInterstitial, screenHome, Ratings,
  };
  Ratings.refresh();

  // Enlace de invitación: ?sala=CODIGO
  const invite = new URLSearchParams(location.search).get('sala');
  if (invite && window.Online) window.Online.screenJoin(invite.toUpperCase());
  else screenHome();
})();
