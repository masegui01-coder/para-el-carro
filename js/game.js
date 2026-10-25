// Para el Carro — versión web de prueba (pass-and-play, 3-8 jugadores en un mismo dispositivo).
// La lógica de ronda (Logic) es la misma que implementa la app Android en GameEngine.kt.
(function () {
  'use strict';

  const OPEN_SECONDS = 50;   // el primer jugador tiene 50 s para decir una cifra
  const TURN_SECONDS = 15;   // cada jugador tiene 15 s para subir, quedarse o bajarse
  const MIN_PLAYERS = 3, MAX_PLAYERS = 8;
  const AD_EVERY_ROUNDS = 2; // intersticial cada 2 rondas y antes del podio
  // Avatares ilustrados (js/avatares.js, generado desde shared/avatares/*.svg)
  const AVATARS = (window.AVATARES || []).map((a) => a.id);

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

    /**
     * ¿Es una barbaridad? Devuelve 'alto' (se ha pasado), 'bajo' (se ha quedado muy corto) o null.
     * - Números: 10 veces o más por encima o por debajo, con al menos 10 de diferencia.
     * - Fechas: más de 100 años de error, o más de 1/4 del tiempo transcurrido si es muy antigua.
     */
    barbaridad(q, v, anioActual = new Date().getFullYear()) {
      const a = q.a;
      if (typeof v !== 'number' || !isFinite(v)) return null;
      if (q.tipo === 'fecha') {
        const margen = Math.max(100, 0.25 * (anioActual - a));
        if (Math.abs(v - a) <= margen) return null;
        return v > a ? 'alto' : 'bajo';
      }
      if (Math.abs(v - a) < 10) return null;
      if (v >= a * 10) return 'alto';
      if (v * 10 <= a) return 'bajo';
      return null;
    },

    /** Lo lejos que está una cifra de la respuesta (para quedarse con la peor de cada jugador). */
    distancia(q, v) {
      if (q.tipo === 'fecha') return Math.abs(v - q.a);
      return Math.abs(Math.log(Math.max(v, 1e-9) / Math.max(q.a, 1e-9)));
    },

    /**
     * Puntos de la ronda. La burra aguanta si la respuesta real es IGUAL O MAYOR que la cifra.
     * Con `barbaridades`, cada jugador que haya dicho algún disparate en la ronda pierde 1 punto (una vez).
     */
    score(players, R, barbaridades = false) {
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
      const barb = barbaridades ? Logic.barbaridades(R) : [];
      barb.forEach((b) => { delta[b.id] -= 1; });
      return { success, bullseye, delta, passengers, off, barbaridades: barb };
    },

    /**
     * Quién se lleva el −1 de las barbaridades: el que dice el disparate y también el que se queda
     * en el carro con él (lo da por bueno). Una vez por jugador, con su peor cifra.
     * Devuelve [{ id, v, tipo, de }] — `de` es el conductor de la cifra apoyada (null si la dijo él).
     */
    barbaridades(R) {
      const apoyos = new Map();
      const apunta = (id, v, de) => apoyos.set(id, [...(apoyos.get(id) || []), { v, de }]);
      let bid = null, driver = null;
      (R.log || []).forEach((l) => {
        if (l.action === 'abre' || l.action === 'subir') {
          bid = l.value; driver = l.id;
          apunta(l.id, l.value, null);
        } else if (l.action === 'quedar' && bid !== null) {
          apunta(l.id, bid, driver);
        }
      });
      const out = [];
      apoyos.forEach((cifras, id) => {
        // Antes lo que dijo él mismo; dentro de eso, la más disparatada
        const propia = (c) => (c.de === null ? 0 : 1);
        const peor = cifras.filter((c) => Logic.barbaridad(R.q, c.v))
          .sort((x, y) => propia(x) - propia(y) || Logic.distancia(R.q, y.v) - Logic.distancia(R.q, x.v))[0];
        if (peor) out.push({ id, v: peor.v, tipo: Logic.barbaridad(R.q, peor.v), de: peor.de });
      });
      return out;
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
  /**
   * Lee lo que escribe el jugador. Nunca lanza errores: devuelve null si no es una cifra válida
   * (letras, varias comas, más de 18 cifras, números infinitos…).
   */
  function parseCifra(str, tipo, antesDeCristo) {
    const s = String(str == null ? '' : str).trim().replace(/\s/g, '').replace(/\./g, '').replace(',', '.');
    if (s.length > 22 || !/^\d{1,18}(\.\d{1,6})?$/.test(s)) return null;
    let v = Number(s);
    if (!Number.isFinite(v)) return null;
    if (tipo === 'fecha') { v = Math.round(v); if (antesDeCristo) v = -v; }
    return v;
  }

  const store = {
    get(k, def) { try { const v = localStorage.getItem('pec.' + k); return v ? JSON.parse(v) : def; } catch (e) { return def; } },
    set(k, v) { try { localStorage.setItem('pec.' + k, JSON.stringify(v)); } catch (e) { /* sin almacenamiento */ } },
  };

  // ---------------------------------------------------------------- Ajustes: sonido, vibración y tema

  const Ajustes = {
    sonido: store.get('sonido', true),
    vibracion: store.get('vibracion', true),
    tema: store.get('tema', 'oscuro'), // 'oscuro' | 'claro' | 'sistema'
    imagenes: store.get('imagenes', true), // ilustraciones de Wikipedia en "¿Sabías que…?" (usan datos)
    set(k, v) { Ajustes[k] = v; store.set(k, v); if (k === 'tema') aplicarTema(); pintarBarra(); },
  };

  function aplicarTema() {
    const claro = Ajustes.tema === 'claro' || (Ajustes.tema === 'sistema' && window.matchMedia && matchMedia('(prefers-color-scheme: light)').matches);
    document.documentElement.dataset.theme = claro ? 'light' : 'dark';
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.content = claro ? '#f3e9d6' : '#14161c';
  }
  aplicarTema();
  try { matchMedia('(prefers-color-scheme: light)').addEventListener('change', aplicarTema); } catch (e) { /* navegador antiguo */ }

  /** Estadísticas de uso (js/metricas.js); nunca rompen el juego. */
  const evento = (nombre, params) => window.Metricas && window.Metricas.evento(nombre, params);

  /** Iconos ilustrados de las opciones (img/iconos). */
  const ico = (nombre) => `<img class="ico" src="img/iconos/${nombre}.svg" alt="">`;

  // Sonido sintetizado (sin ficheros) y vibración
  let audio = null;
  /** Una nota. `to` hace un deslizamiento de frecuencia (para rebuznos, saltos…). */
  function tone(freq, dur, type = 'sine', vol = .15, delay = 0, to = null) {
    if (!Ajustes.sonido) return null;
    try {
      audio = audio || new (window.AudioContext || window.webkitAudioContext)();
      if (audio.state === 'suspended') audio.resume();
      const o = audio.createOscillator(), g = audio.createGain();
      const t0 = audio.currentTime + delay;
      o.type = type; o.frequency.setValueAtTime(freq, t0);
      if (to) o.frequency.exponentialRampToValueAtTime(to, t0 + dur);
      g.gain.setValueAtTime(vol, t0); g.gain.exponentialRampToValueAtTime(.001, t0 + dur);
      o.connect(g).connect(audio.destination); o.start(t0); o.stop(t0 + dur);
      return o;
    } catch (e) { return null; }
  }
  const sfx = {
    tick() { tone(880, .08, 'square', .05); },
    click() { tone(520, .05, 'triangle', .08); },
    creak() { tone(90, 1.2, 'sawtooth', .05, 0, 140); },
    /** Abrir la ronda: un "¡arre!" corto. */
    abre() { tone(330, .12, 'triangle', .14); tone(495, .18, 'triangle', .14, .1); },
    /** Subir la cifra: rebuzno de la burra ("¡hiii-aaa!"). */
    subir() {
      tone(620, .22, 'sawtooth', .09, 0, 900); tone(380, .3, 'sawtooth', .09, .22, 260);
      tone(640, .2, 'sawtooth', .08, .55, 880); tone(360, .32, 'sawtooth', .08, .75, 240);
    },
    /** Quedarse en el carro: crujido de madera ("toc-toc"). */
    quedar() { tone(180, .07, 'square', .12); tone(140, .09, 'square', .12, .11); tone(90, .25, 'sawtooth', .04, .2, 120); },
    /** Bajarse: silbido de salto y golpe al caer. */
    bajar() { tone(900, .3, 'sine', .12, 0, 180); tone(70, .25, 'triangle', .3, .3); },
    win() { [523, 659, 784, 1046].forEach((f, i) => tone(f, .25, 'triangle', .14, i * .12)); },
    lose() { tone(400, .9, 'sawtooth', .1, 0, 60); tone(140, .5, 'sine', .2, .85); },
  };
  const vibrate = (p) => {
    if (!Ajustes.vibracion) return;
    try { navigator.vibrate && navigator.vibrate(p); } catch (e) { /* no soportado */ }
  };
  /** Sonido + vibración propios de cada opción. */
  const VIB = { abre: 40, subir: [30, 40, 30, 40, 80], quedar: [20, 60, 20], bajar: [120, 40, 200] };
  function feedback(opcion) { sfx[opcion](); vibrate(VIB[opcion]); }

  // ---------------------------------------------------------------- Barra de ajustes (silencio y ⚙️)

  const $barra = document.createElement('div');
  $barra.className = 'barra-ajustes';
  document.body.appendChild($barra);
  function pintarBarra() {
    $barra.innerHTML = `
      <button class="btn-redondo" id="silencio" aria-label="${Ajustes.sonido ? 'Silenciar' : 'Activar sonido'}" title="${Ajustes.sonido ? 'Silenciar' : 'Activar sonido'}">${Ajustes.sonido ? '🔊' : '🔇'}</button>
      <button class="btn-redondo" id="ajustes" aria-label="Ajustes" title="Ajustes">⚙️</button>`;
    $barra.querySelector('#silencio').onclick = () => { Ajustes.set('sonido', !Ajustes.sonido); sfx.click(); };
    $barra.querySelector('#ajustes').onclick = abrirAjustes;
  }
  // Correo del estudio: el alias "+carro" hace que Gmail lo etiquete como Para el Carro.
  const SUPPORT_EMAIL = 'tizaestudiogames+carro@gmail.com';
  function enlaceSoporte() {
    const asunto = '[Para el Carro] Sugerencia o error (web)';
    const cuerpo = '\n\n—\n' + navigator.userAgent;
    return `mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent(asunto)}&body=${encodeURIComponent(cuerpo)}`;
  }
  function abrirAjustes() {
    const m = document.createElement('div');
    m.className = 'modal';
    const pintar = () => {
      m.innerHTML = `<div class="panel col" role="dialog" aria-label="Ajustes">
        <h2>Ajustes</h2>
        <label class="fila"><span class="crece">🔊 Sonido</span><input type="checkbox" id="aj-sonido" ${Ajustes.sonido ? 'checked' : ''}></label>
        <label class="fila"><span class="crece">📳 Vibración</span><input type="checkbox" id="aj-vibracion" ${Ajustes.vibracion ? 'checked' : ''}></label>
        <label class="fila"><span class="crece">🖼️ Ilustraciones<br><span class="suave">En «¿Sabías que…?», desde Wikipedia (usan datos)</span></span><input type="checkbox" id="aj-imagenes" ${Ajustes.imagenes ? 'checked' : ''}></label>
        <div>🌗 Tema</div>
        <div class="segmentado">${[['oscuro', '🌙 Oscuro'], ['claro', '☀️ Claro'], ['sistema', '📱 Sistema']].map(([k, l]) => `<button data-tema="${k}" class="${Ajustes.tema === k ? 'sel' : ''}">${l}</button>`).join('')}</div>
        <a class="btn fantasma bloque" href="${enlaceSoporte()}">✉️ Enviar sugerencia o error</a>
        <button class="btn ambar bloque" id="aj-cerrar">Listo</button>
      </div>`;
      m.querySelector('#aj-sonido').onchange = (e) => { Ajustes.set('sonido', e.target.checked); sfx.click(); };
      m.querySelector('#aj-vibracion').onchange = (e) => { Ajustes.set('vibracion', e.target.checked); vibrate(40); };
      m.querySelector('#aj-imagenes').onchange = (e) => { Ajustes.set('imagenes', e.target.checked); };
      m.querySelectorAll('[data-tema]').forEach((b) => { b.onclick = () => { Ajustes.set('tema', b.dataset.tema); pintar(); }; });
      m.querySelector('#aj-cerrar').onclick = () => m.remove();
    };
    m.addEventListener('click', (e) => { if (e.target === m) m.remove(); });
    pintar();
    document.body.appendChild(m);
  }
  pintarBarra();

  // ---------------------------------------------------------------- Control de fallos
  // Si algo falla (un dato raro de la red, un error de programación…), el juego no se queda en blanco:
  // avisa y ofrece seguir o volver al inicio.

  let avisoFallo = null;
  function mostrarFallo(err) {
    try { console.error('[Para el Carro]', err); } catch (e) { /* nada */ }
    evento('error_web', { mensaje: String((err && err.message) || err).slice(0, 100) });
    if (avisoFallo) return;
    avisoFallo = document.createElement('div');
    avisoFallo.className = 'aviso-fallo';
    avisoFallo.setAttribute('role', 'alert');
    avisoFallo.innerHTML = '<span>⚠️ Ups, algo ha fallado. Puedes seguir jugando.</span><span class="fila"><button class="btn peq" id="fallo-seguir">Seguir</button><button class="btn fantasma peq" id="fallo-inicio">Inicio</button></span>';
    document.body.appendChild(avisoFallo);
    const cerrar = () => { if (avisoFallo) avisoFallo.remove(); avisoFallo = null; };
    avisoFallo.querySelector('#fallo-seguir').onclick = () => {
      cerrar();
      if (!$app.innerHTML.trim()) screenHome(); // pantalla vacía: volver al inicio
    };
    avisoFallo.querySelector('#fallo-inicio').onclick = () => { cerrar(); stopTimer(); history.replaceState(null, '', location.pathname); screenHome(); };
    setTimeout(cerrar, 8000);
  }
  window.addEventListener('error', (e) => mostrarFallo(e.error || e.message));
  window.addEventListener('unhandledrejection', (e) => mostrarFallo(e.reason));

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
  /** Avatar ilustrado ("av:gato") como imagen; los antiguos con emoji se muestran tal cual. */
  function avatarHTML(a, cls = '') {
    if (typeof a === 'string' && a.startsWith('av:')) {
      const info = (window.AVATARES || []).find((x) => x.id === a);
      return `<img class="av ${cls}" src="img/avatares/${esc(a.slice(3))}.svg" alt="${esc(info ? info.nombre : '')}">`;
    }
    return `<span class="av-emoji ${cls}">${esc(a || '🙂')}</span>`;
  }
  const tag = (p) => `${avatarHTML(p.avatar)} ${esc(p.name)}`;

  // ---------------------------------------------------------------- Estado de la partida

  const G = {
    players: store.get('players', null) || [
      { id: 1, name: 'Jugador 1', avatar: AVATARS[0] },
      { id: 2, name: 'Jugador 2', avatar: AVATARS[1] },
      { id: 3, name: 'Jugador 3', avatar: AVATARS[2] },
    ],
    rounds: store.get('rounds', 6),
    mode: store.get('mode', 'mezcla'), // 'fecha' | 'numero' | 'mezcla'
    barbaridades: store.get('barbaridades', true), // −1 a quien diga un disparate
    round: 0,
    R: null,
    openAttempts: 0,
  };

  // ---------------------------------------------------------------- ¿Sabías que…? (aprender con cada pregunta)

  /**
   * Tras cada ronda, una explicación de la respuesta (js/aprende.json: id -> [texto, artículo de Wikipedia]).
   * El fichero se descarga una sola vez, cuando hace falta, y la ilustración se pide a Wikipedia al momento
   * (no ocupa nada en la app y se puede desactivar en Ajustes).
   */
  const Aprende = {
    datos: null,
    carga: null,
    fotos: new Map(),
    cargar() {
      if (!this.carga) {
        this.carga = fetch('js/aprende.json').then((r) => (r.ok ? r.json() : {})).catch(() => ({}))
          .then((d) => { this.datos = d || {}; return this.datos; });
      }
      return this.carga;
    },
    /** Hueco en la pantalla de puntos; se rellena con bind() cuando están los datos. */
    hueco(qid) {
      this.cargar();
      return `<div class="aprende" data-aprende="${esc(qid)}" hidden></div>`;
    },
    async bind() {
      const el = document.querySelector('[data-aprende]');
      if (!el) return;
      const datos = await this.cargar();
      const entrada = datos[el.dataset.aprende];
      if (!entrada || !el.isConnected) return;
      const [texto, articulo] = entrada;
      el.innerHTML = `<div class="aprende-titulo">📚 ¿Sabías que…?</div><p>${esc(texto)}</p>`;
      el.hidden = false;
      if (Ajustes.imagenes && articulo) this.ilustrar(el, articulo);
    },
    /** Miniatura del artículo de Wikipedia (si no hay o no hay conexión, simplemente no sale). */
    async ilustrar(el, articulo) {
      try {
        if (!this.fotos.has(articulo)) {
          const r = await fetch(`https://en.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(articulo.replace(/ /g, '_'))}`);
          const j = r.ok ? await r.json() : null;
          this.fotos.set(articulo, (j && j.thumbnail && j.thumbnail.source) || '');
        }
        const src = this.fotos.get(articulo);
        if (!src || !el.isConnected) return;
        const fig = document.createElement('figure');
        fig.className = 'aprende-img';
        fig.innerHTML = `<img alt="" loading="lazy"><figcaption>Imagen: Wikipedia</figcaption>`;
        const img = fig.querySelector('img');
        img.onerror = () => fig.remove();
        img.src = src;
        el.insertBefore(fig, el.children[1]);
      } catch (e) { /* sin conexión: solo texto */ }
    },
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
        evento('pregunta_valorada', { valor: b.dataset.r });
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
      ${AVATARS.map((a) => `<button data-a="${a}" class="${a === current ? 'sel' : ''}" ${used.includes(a) ? 'disabled' : ''} title="${esc(((window.AVATARES || []).find((x) => x.id === a) || {}).nombre || '')}">${avatarHTML(a, 'grande')}</button>`).join('')}
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
            <br>${ico('subir')} <b>Subir la cifra</b> (pasa a ser el conductor y el anterior se sube al carro),
            <br>${ico('carreta')} <b>Quedarse en el carro</b>, o
            <br>${ico('bajar')} <b>Bajarse</b> (ya no puede volver a subir ni pujar en esta ronda).</li>
          <li>Se sigue dando vueltas hasta que nadie sube la cifra.</li>
          <li>El carro cruza el puente si la <b>respuesta real es igual o mayor</b> que la cifra del conductor. Si no… ¡al río!</li>
        </ol>
      </div>
      <h2>Puntos</h2>
      <div class="panel">
        <table class="puntos">
          <tr><td></td><td class="num">Cruza</td><td class="num">Al río</td></tr>
          <tr><td>${ico('burra')} Conductor</td><td class="num mas">+3 +1 por pasajero</td><td class="num menos">−3 −1 por cada bajado</td></tr>
          <tr><td>${ico('carreta')} En el carro</td><td class="num mas">+3</td><td class="num menos">−2</td></tr>
          <tr><td>${ico('bajar')} Bajados</td><td class="num">0</td><td class="num mas">+3</td></tr>
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
        <button class="avatar-btn" data-avatar="${p.id}" aria-label="Cambiar avatar">${avatarHTML(p.avatar, 'grande')}</button>
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
        <label class="fila"><span class="crece">😱 Modo barbaridades<br><span class="suave">−1 a quien diga un disparate (10 veces más o menos, o más de 100 años)</span></span>
          <input type="checkbox" id="barbaridades" ${G.barbaridades ? 'checked' : ''}></label>
      </div>
      <button class="btn ambar bloque" id="empezar">¡Arre, burra! Empezar</button>`, true);

    const save = () => { store.set('players', G.players); store.set('rounds', G.rounds); store.set('mode', G.mode); store.set('barbaridades', G.barbaridades); };
    on('#barbaridades', 'change', (e) => { G.barbaridades = e.target.checked; save(); });
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
      evento('partida_inicio', { modo: 'local', jugadores: G.players.length, rondas: G.rounds, barbaridades: G.barbaridades ? 1 : 0 });
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
        <div class="gigante">${avatarHTML(player.avatar, 'gigante')}</div>
        <p class="suave">${esc(subtitle || 'Pasa el móvil a')}</p>
        <h1>${esc(player.name)}</h1>
        <button class="btn ambar bloque" id="listo" style="margin-top:20px">¡Soy yo, empezar!</button>
      </div>`);
    on('#listo', 'click', () => { sfx.click(); next(player); });
  }

  /** Teclado de cifra con vista previa formateada y botones rápidos. */
  function cifraInput(q, placeholder) {
    return `
      <input type="text" id="cifra" inputmode="decimal" autocomplete="off" maxlength="24" placeholder="${esc(placeholder)}">
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
        <button class="btn ambar bloque" id="ok" disabled>${ico('burra')} ¡Me subo a la burra!</button>
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
      feedback('abre');
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
      const icon = ico(st === 'conductor' ? 'burra' : st === 'bajado' ? 'bajar' : 'carreta');
      const off = p.online === false ? ' 📴' : '';
      return `<span class="chip ${cls}${p.id === turnId ? ' turno' : ''}">${icon} ${avatarHTML(p.avatar)} ${esc(p.name)}${off}</span>`;
    }).join('');
    const log = (R.log || []).slice(-6).reverse().map((l) => {
      const p = players.find((x) => x.id === l.id);
      if (!p) return '';
      const txt = { abre: `abre con ${fmtQ(l.value, R.q)}`, subir: `sube a ${fmtQ(l.value, R.q)}`, quedar: 'se queda en el carro', bajar: 'se baja del carro', tiempo: 'se queda (tiempo)' }[l.action];
      return `<li>${avatarHTML(p.avatar)} ${esc(p.name)} ${txt}</li>`;
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
      <div id="rig"></div>
      <div class="col">
        <button class="btn ambar bloque" id="subir">${ico('subir')} Subir la cifra</button>
        <div class="panel col" id="panel-subir" hidden>
          <div class="botones-rapidos">${stepBtns.map(([l, s]) => `<button class="btn peq" data-step="${s}">${l}</button>`).join('')}</div>
          ${cifraInput(q, 'Nueva cifra (mayor)')}
          <button class="btn ambar bloque" id="ok-subir" disabled>${ico('burra')} Me monto en la burra</button>
        </div>
        <button class="btn musgo bloque" id="quedar">${ico('carreta')} Me quedo en el carro</button>
        <button class="btn sangre bloque" id="bajar">${ico('bajar')} Me bajo del carro</button>
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
    const rigEl = $app.querySelector('#rig');
    window.BridgeScene.rig(rigEl, G.players, R);
    rigEl.querySelector(`.rig-av[data-id="${player.id}"]`).classList.add('yo');
    let done = false;
    const act = (action, value, timedOut = false) => {
      if (done) return;
      done = true;
      stopTimer();
      $app.querySelectorAll('.col button').forEach((b) => { b.disabled = true; });
      const before = JSON.parse(JSON.stringify({ status: G.R.status }));
      Logic.act(G.players, G.R, player.id, action, value);
      if (timedOut) G.R.log[G.R.log.length - 1].action = 'tiempo';
      feedback(action);
      // El muñeco se sube a la burra, se baja o se alegra en el carro; luego sigue el juego
      const anim = action === 'quedar'
        ? window.BridgeScene.cheer(rigEl, player.id)
        : window.BridgeScene.rig(rigEl, G.players, before, G.R);
      anim.then(afterAction);
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
    const result = Logic.score(G.players, R, G.barbaridades);
    evento('ronda_fin', { modo: 'local', exito: result.success ? 1 : 0, barbaridades: result.barbaridades.length, tipo: R.q.tipo });
    const driver = playerById(R.driver);
    const passengers = result.passengers.map((id) => playerById(id).avatar);
    render(`
      ${header()}
      <div class="panel"><div class="pregunta" style="font-size:1.05rem">${esc(q.q)}</div></div>
      <div class="escena">
        <canvas id="puente" width="400" height="260"></canvas>
        <div class="revelacion" id="rev">${avatarHTML(driver.avatar)} apuesta por <span style="color:var(--ambar-claro)">${esc(fmtQ(R.bid, q))}</span></div>
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
      showBarbaridades(result.barbaridades, G.players, R.q, G.round).then(() => screenRoundScore(result));
    });
  }

  function screenRoundScore(result) {
    const R = G.R, q = R.q;
    const driver = playerById(R.driver);
    const rows = G.players.slice().sort((a, b) => b.score - a.score).map((p) => {
      const d = result.delta[p.id];
      const st = R.status[p.id];
      const icon = ico(st === 'conductor' ? 'burra' : st === 'bajado' ? 'bajar' : 'carreta');
      return `<tr><td>${icon} ${tag(p)}</td><td class="num ${d > 0 ? 'mas' : d < 0 ? 'menos' : ''}">${d > 0 ? '+' : ''}${d}</td><td class="num">${p.score}</td></tr>`;
    }).join('');
    const last = G.round >= G.rounds;
    render(`
      ${header()}
      <div class="resultado-titulo ${result.success ? 'ok' : 'ko'}">${result.success ? '¡El carro cruzó el puente!' : '¡Al río con la burra!'}</div>
      ${result.bullseye ? '<p class="centro" style="color:var(--ambar-claro);font-weight:800">🎯 ¡Clavada! +2 para el conductor</p>' : ''}
      ${barbaridadesHTML(result.barbaridades, G.players, q)}
      <div class="panel">
        <div class="pregunta" style="font-size:1rem">${esc(q.q)}</div>
        <p>Respuesta: <b style="color:var(--ambar-claro)">${esc(fmtQ(q.a, q))}</b> · ${tag(driver)} dijo <b>${esc(fmtQ(R.bid, q))}</b></p>
        ${q.n ? `<p class="suave" style="margin:0">💡 ${esc(q.n)}</p>` : ''}
        ${Aprende.hueco(q.id)}
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
    Aprende.bind();
  }

  // ---------------------------------------------------------------- ¡Qué barbaridad! (abuela)

  /**
   * Las grabaciones y lo que dicen, palabra a palabra con el segundo en que suena cada una
   * (medido sobre el audio), para que el bocadillo se ilumine al ritmo de la voz.
   * `ms` es lo que dura el audio. Mismos datos en Android (ui/components/Abuela.kt).
   */
  const VOCES_ABUELA = {
    corto: {
      src: ['audio/abuela-te-pasas.ogg', 'audio/abuela-te-pasas.wav'], ms: 2450,
      tramos: [[0.6, '¿No te'], [0.9, 'habrás'], [1.1, 'quedado'], [1.35, 'corto,'], [1.7, 'no?']],
    },
    santo: {
      src: ['audio/abuela-espiritu-santo.ogg', 'audio/abuela-espiritu-santo.wav'], ms: 2800,
      tramos: [[0.4, '¡Esto no lo'], [0.9, 'arregla'], [1.3, 'ni el'], [1.55, 'Espíritu'], [2.0, 'Santo!']],
    },
    trillones: {
      src: ['audio/abuela-trillones.wav'], ms: 11000,
      tramos: [[1.0, '¿Cuatrocientos'], [1.8, 'trillones?'], [7.4, '¡¡Cuatrocientos'], [8.1, 'trillones!!']],
    },
  };
  const textoVoz = (voz) => voz.tramos.map((t) => t[1]).join(' ');

  /**
   * Qué grabación toca: si alguien se ha pasado, "¿No te habrás quedado corto?" o la de los
   * cuatrocientos trillones, alternando según la ronda y la pregunta (así todos los móviles de la sala
   * oyen la misma); si solo se han quedado cortos, la del Espíritu Santo.
   */
  function vozBarbaridad(lista, q, ronda) {
    if (!lista || !lista.some((b) => b.tipo === 'alto')) return 'santo';
    const numero = parseInt(String((q && q.id) || '').replace(/\D/g, ''), 10) || 0;
    return ((ronda || 0) + numero) % 2 === 0 ? 'trillones' : 'corto';
  }

  /** Cuánto dura la escena de la abuela (0 si no hay barbaridades). Lo usa también el jefe de sala online. */
  function duracionBarbaridades(lista, q, ronda) {
    if (!lista || !lista.length) return 0;
    return Math.max(4500, VOCES_ABUELA[vozBarbaridad(lista, q, ronda)].ms + 1500);
  }

  /** Reproduce la grabación y llama a `empieza` cuando suena (o enseguida, si no hay sonido). */
  function vozAbuela(voz, empieza) {
    let arrancado = false;
    const go = () => { if (!arrancado) { arrancado = true; empieza(); } };
    if (!Ajustes.sonido) return go();
    const decir = () => {
      // Si el navegador no puede reproducir la grabación, una voz sintética dice la frase
      try {
        const u = new SpeechSynthesisUtterance(textoVoz(voz));
        u.lang = 'es-ES'; u.rate = .85; u.pitch = .8;
        speechSynthesis.speak(u);
      } catch (e) { /* sin voz */ }
      go();
    };
    // Primero la grabación en Ogg (ligera); si el navegador no la reproduce (algunos iPhone), el WAV; si no, voz sintética
    const probar = (i) => {
      if (i >= voz.src.length) return decir();
      try {
        const a = new Audio(voz.src[i]);
        a.addEventListener('playing', go, { once: true });
        a.play().catch(() => probar(i + 1));
      } catch (e) { probar(i + 1); }
    };
    probar(0);
    setTimeout(go, 1500); // por si el audio tarda en cargar: el texto no se queda esperando
  }

  /**
   * Tras el puente: la abuela señala a quien ha dicho un disparate (o se ha subido al carro con él)
   * y le cae −1. El bocadillo se va encendiendo palabra a palabra al ritmo de la grabación.
   */
  function showBarbaridades(lista, players, q, ronda) {
    if (!lista || !lista.length) return Promise.resolve();
    const voz = VOCES_ABUELA[vozBarbaridad(lista, q, ronda)];
    const quien = (id) => players.find((p) => p.id === id) || { name: '¿?', avatar: '🙂' };
    const el = document.createElement('div');
    el.className = 'abuela-escena';
    el.setAttribute('role', 'alert');
    el.innerHTML = `
      <div class="abuela-fila">
        <div class="abuela-bocadillo" aria-label="${esc(textoVoz(voz))}">${voz.tramos.map((t) => `<span class="palabra">${esc(t[1])}</span>`).join(' ')}</div>
        <div class="abuela-cuerpo"><img src="img/avatares/abuela.svg" alt="La abuela"><span class="dedo" aria-hidden="true">👉</span></div>
      </div>
      <div class="culpables">${lista.map((b) => {
        const p = quien(b.id);
        return `<div class="culpable">
          <div class="culpable-av">${avatarHTML(p.avatar, 'grande')}</div>
          <b>${esc(p.name)}</b>
          <span>${esc(queHizo(b, players, q))}</span>
          <span class="menos-uno">−1</span>
        </div>`;
      }).join('')}</div>
      <p class="suave centro">La respuesta era ${esc(fmtQ(q.a, q))}</p>
      <button class="btn fantasma peq" id="abuela-seguir">Seguir ⏩</button>`;
    document.body.appendChild(el);
    const timers = [];
    vozAbuela(voz, () => {
      el.querySelectorAll('.palabra').forEach((w, i) => {
        timers.push(setTimeout(() => w.classList.add('dicha'), voz.tramos[i][0] * 1000));
      });
    });
    vibrate([60, 40, 60, 40, 120]);
    return new Promise((resolve) => {
      const fin = () => { timers.forEach(clearTimeout); if (el.isConnected) { el.remove(); resolve(); } };
      el.querySelector('#abuela-seguir').onclick = fin;
      setTimeout(fin, duracionBarbaridades(lista, q, ronda));
    });
  }

  /** "dijo 30.000" o "se subió con los 30.000 de Luis". */
  function queHizo(b, players, q) {
    if (b.de === null || b.de === undefined) return `dijo ${fmtQ(b.v, q)}`;
    const conductor = players.find((p) => p.id === b.de);
    return `se subió con ${fmtQ(b.v, q)}${conductor ? ` de ${conductor.name}` : ''}`;
  }

  /** Línea de la tabla de puntos con los disparates de la ronda. */
  function barbaridadesHTML(lista, players, q) {
    if (!lista || !lista.length) return '';
    return `<div class="panel barbaridades">😱 <b>¡Qué barbaridad!</b> (−1)<br>${lista.map((b) => {
      const p = players.find((x) => x.id === b.id) || { name: '¿?', avatar: '🙂' };
      return `${avatarHTML(p.avatar)} ${esc(p.name)} ${esc(queHizo(b, players, q))} ${b.tipo === 'alto' ? '(¡se pasó!)' : '(¡se quedó cortísimo!)'}`;
    }).join('<br>')}</div>`;
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
    evento('partida_fin', { modo: 'local', rondas: G.round });
    const ranking = G.players.slice().sort((a, b) => b.score - a.score);
    const [p1, p2, p3] = ranking;
    const peana = (p, h, pos) => p ? `<div><div class="podio-av">${avatarHTML(p.avatar, 'grande')}</div><div>${esc(p.name)}</div><div class="peana" style="height:${h}px">${pos}<br>${p.score}</div></div>` : '<div></div>';
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
    esc, fmtQ, parseCifra, avatarHTML, ico, feedback, store, sfx, vibrate, render, on, startTimer, stopTimer,
    pickQuestion, avatarPicker, cifraInput, bindCifra, cartPanel, showInterstitial, screenHome, Ratings, Aprende,
    showBarbaridades, barbaridadesHTML, duracionBarbaridades,
  };
  Ratings.refresh();

  // Enlace de invitación: ?sala=CODIGO
  const invite = new URLSearchParams(location.search).get('sala');
  if (invite && window.Online) window.Online.screenJoin(invite.toUpperCase());
  else screenHome();
})();
