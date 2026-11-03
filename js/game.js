// Para el Carro — versión web de prueba (pass-and-play, 3-8 jugadores en un mismo dispositivo).
// La lógica de ronda (Logic) es la misma que implementa la app Android en GameEngine.kt.
(function () {
  'use strict';

  const OPEN_SECONDS = 50;   // el primer jugador tiene 50 s para decir una cifra
  const TURN_SECONDS = 15;   // cada jugador tiene 15 s para subir, quedarse o bajarse
  const MIN_PLAYERS = 3, MAX_PLAYERS = 8;
  const AD_EVERY_ROUNDS = 2; // intersticial cada 2 rondas y antes del podio
  // Para que las rondas no se eternicen (p. ej. "¿cuánto mide el Everest?" subiendo de 1 en 1):
  const MAX_SUBIDAS = 3;       // cada jugador puede subir la cifra como mucho 3 veces por ronda
  const SUBIDA_MIN = 0.10;     // en los números hay que subir al menos un 10 % (en las fechas, 1 año)
  const BARRA_NUMEROS = 10;    // la barra para subir llega hasta ×10 la subida mínima…
  const BARRA_FECHAS = 200;    // …o hasta 200 años más en las fechas
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

    /**
     * La cifra mínima para subir. Fechas: un año más. Números: al menos un 10 % más, redondeado
     * hacia arriba a 3 cifras significativas (8.848 → 9.740) y, si la cifra es entera, al menos +1.
     */
    minSubida(q, bid) {
      if (q.tipo === 'fecha') return bid + 1;
      if (!(bid > 0)) return bid + Math.max(1, Math.abs(bid) * SUBIDA_MIN);
      const x = bid * (1 + SUBIDA_MIN);
      const paso = Math.pow(10, Math.floor(Math.log10(x)) - 2);
      let min = Math.ceil(x / paso - 1e-9) * paso;
      if (paso < 1) min = Number(min.toFixed(12)); // 15.100000000000001 → 15.1
      if (Number.isInteger(bid)) min = Math.max(min, bid + 1);
      return min;
    },

    /** ¿Vale esta cifra para subir? (con un margen mínimo por los redondeos entre web y app) */
    subidaValida(q, bid, v) {
      if (typeof v !== 'number' || !isFinite(v) || !(v > bid)) return false;
      const min = Logic.minSubida(q, bid);
      return v >= min - Math.abs(min) * 1e-9;
    },

    /** Cuántas veces ha subido ya la cifra este jugador en la ronda (abrir no cuenta). */
    subidas(R, playerId) {
      return (R.log || []).filter((l) => l.id === playerId && l.action === 'subir').length;
    },

    puedeSubir(R, playerId) {
      return Logic.subidas(R, playerId) < MAX_SUBIDAS;
    },

    /** action: 'subir' (cifra válida y le quedan subidas), 'quedar' o 'bajar'. Devuelve true si la ronda ha terminado. */
    act(players, R, playerId, action, value) {
      if (R.queue[0] !== playerId) throw new Error('No es su turno');
      if (action === 'subir') {
        if (!Logic.puedeSubir(R, playerId)) throw new Error('Ya ha subido ' + MAX_SUBIDAS + ' veces');
        if (!Logic.subidaValida(R.q, R.bid, value)) throw new Error('Hay que subir al menos hasta ' + Logic.minSubida(R.q, R.bid));
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
     * en el carro con él (lo da por bueno). Cuenta la ÚLTIMA cifra que dijo o apoyó cada jugador:
     * si empezó con una barbaridad pero luego la corrigió subiendo, ya no se le castiga.
     * Devuelve [{ id, v, tipo, de }] — `de` es el conductor de la cifra apoyada (null si la dijo él).
     */
    barbaridades(R) {
      const ultima = new Map();
      const apunta = (id, v, de) => ultima.set(id, { v, de });
      let bid = null, driver = null;
      (R.log || []).forEach((l) => {
        if (l.action === 'abre' || l.action === 'subir') {
          bid = l.value; driver = l.id;
          apunta(l.id, l.value, null);
        } else if ((l.action === 'quedar' || l.action === 'tiempo') && bid !== null) {
          apunta(l.id, bid, driver);
        }
      });
      const out = [];
      ultima.forEach((c, id) => {
        const tipo = Logic.barbaridad(R.q, c.v);
        if (tipo) out.push({ id, v: c.v, tipo, de: c.de });
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
    tema: store.get('tema', 'claro'), // 'oscuro' | 'claro' | 'sistema'
    imagenes: store.get('imagenes', true), // ilustraciones de Wikipedia en "¿Sabías que…?" (usan datos)
    set(k, v) { Ajustes[k] = v; store.set(k, v); if (k === 'tema') aplicarTema(); pintarBarra(); },
  };

  function aplicarTema() {
    const claro = Ajustes.tema === 'claro' || (Ajustes.tema === 'sistema' && window.matchMedia && matchMedia('(prefers-color-scheme: light)').matches);
    document.documentElement.dataset.theme = claro ? 'light' : 'dark';
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.content = claro ? '#FFF4D6' : '#1E2033';
  }
  aplicarTema();
  try { matchMedia('(prefers-color-scheme: light)').addEventListener('change', aplicarTema); } catch (e) { /* navegador antiguo */ }

  /** Estadísticas de uso (js/metricas.js); nunca rompen el juego. */
  const evento = (nombre, params) => window.Metricas && window.Metricas.evento(nombre, params);

  /** Iconos ilustrados de las opciones (img/iconos). */
  const ico = (nombre) => `<img class="ico" src="img/iconos/${nombre}.svg" alt="">`;

  /** Iconos de interfaz dibujados a trazo, en vez de emoji: se ven igual en todos los móviles. */
  const ICONOS = {
    amigos: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M19 8v6M16 11h6"/>',
    llave: '<circle cx="7.5" cy="15.5" r="4.5"/><path d="M10.7 12.3 21 2M17 6l3 3M15 8l2 2"/>',
    movil: '<rect x="7" y="2" width="10" height="20" rx="2"/><path d="M11 18h2"/>',
    libro: '<path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v17H6.5A2.5 2.5 0 0 0 4 21.5z"/><path d="M4 21.5A2.5 2.5 0 0 1 6.5 19H20v3H6.5"/>',
    sonido: '<path d="M11 5 6 9H3v6h3l5 4z"/><path d="M15.5 8.5a5 5 0 0 1 0 7"/><path d="M19 5a10 10 0 0 1 0 14"/>',
    silencio: '<path d="M11 5 6 9H3v6h3l5 4z"/><path d="m22 9-6 6M16 9l6 6"/>',
    ajustes: '<path d="M4 6h16M4 12h16M4 18h16"/><circle cx="9" cy="6" r="2.2" fill="currentColor"/><circle cx="15" cy="12" r="2.2" fill="currentColor"/><circle cx="8" cy="18" r="2.2" fill="currentColor"/>',
    dado: '<rect x="3" y="3" width="18" height="18" rx="4"/><circle cx="8.5" cy="8.5" r="1.3" fill="currentColor"/><circle cx="15.5" cy="15.5" r="1.3" fill="currentColor"/><circle cx="12" cy="12" r="1.3" fill="currentColor"/>',
    corona: '<path d="M3 18h18M4 18 3 7l5 4 4-6 4 6 5-4-1 11"/>',
    calendario: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/>',
    numero: '<path d="M5 9h14M5 15h14M10 3 8 21M16 3l-2 18"/>',
    mezcla: '<path d="M16 3h5v5M4 20 21 3M21 16v5h-5M15 15l6 6M4 4l5 5"/>',
    alerta: '<circle cx="12" cy="12" r="9"/><path d="M12 7v6M12 16.5v.5"/>',
    diana: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1.5" fill="currentColor"/>',
    cerrar: '<path d="M6 6l12 12M18 6 6 18"/>',
    atras: '<path d="M15 18l-6-6 6-6"/>',
    mas: '<path d="M12 5v14M5 12h14"/>',
    menos: '<path d="M5 12h14"/>',
    flecha: '<path d="M5 12h14M13 6l6 6-6 6"/>',
    trofeo: '<path d="M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0z"/><path d="M17 5h3a3 3 0 0 1-3 4M7 5H4a3 3 0 0 0 3 4"/>',
    bombilla: '<path d="M9 18h6M10 21h4M12 3a6 6 0 0 0-4 10.5c.8.8 1 1.5 1 2.5h6c0-1 .2-1.7 1-2.5A6 6 0 0 0 12 3z"/>',
    desconectado: '<path d="M2 2l20 20M8.5 16.5a5 5 0 0 1 7 0M5 13a10 10 0 0 1 5-2.7M19 13a10 10 0 0 0-2.3-1.7M12 20h.01"/>',
  };
  const icono = (n, etiqueta) => `<svg class="icono" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" ${etiqueta ? `role="img" aria-label="${etiqueta}"` : 'aria-hidden="true"'}>${ICONOS[n] || ''}</svg>`;

  /** Nombre, frase y color de cada personaje (js/avatares.js, generado desde shared/personajes.json). */
  const personaje = (a) => (window.AVATARES || []).find((x) => x.id === a) || { id: a, nombre: '', frase: '', color: '#FFF0C2' };

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

  // ---------------------------------------------------------------- Barra de ajustes (silencio y ajustes)

  const $barra = document.createElement('div');
  $barra.className = 'barra-ajustes';
  document.body.appendChild($barra);
  function pintarBarra() {
    $barra.innerHTML = `
      <button class="btn-redondo" id="silencio" aria-label="${Ajustes.sonido ? 'Silenciar' : 'Activar sonido'}" title="${Ajustes.sonido ? 'Silenciar' : 'Activar sonido'}">${icono(Ajustes.sonido ? 'sonido' : 'silencio')}</button>
      <button class="btn-redondo" id="ajustes" aria-label="Ajustes" title="Ajustes">${icono('ajustes')}</button>`;
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
  /**
   * Modal accesible: Escape cierra, Tab no se escapa del diálogo y al cerrar el foco vuelve a donde estaba.
   * Devuelve la función que deshace todo (llamarla al cerrar).
   */
  function modalAccesible(capa, alCerrar) {
    const origen = document.activeElement;
    const enfocables = () => [...capa.querySelectorAll('button:not([disabled]), [href], input, select, textarea, [tabindex]:not([tabindex="-1"])')]
      .filter((el) => el.offsetParent !== null);
    const tecla = (e) => {
      if (e.key === 'Escape') { e.preventDefault(); alCerrar(); return; }
      if (e.key !== 'Tab') return;
      const f = enfocables();
      if (!f.length) return;
      const pri = f[0], ult = f[f.length - 1];
      if (!capa.contains(document.activeElement)) { e.preventDefault(); pri.focus(); }
      else if (e.shiftKey && document.activeElement === pri) { e.preventDefault(); ult.focus(); }
      else if (!e.shiftKey && document.activeElement === ult) { e.preventDefault(); pri.focus(); }
    };
    document.addEventListener('keydown', tecla);
    return () => {
      document.removeEventListener('keydown', tecla);
      if (origen && origen.isConnected && typeof origen.focus === 'function') origen.focus();
    };
  }

  function abrirAjustes() {
    const m = document.createElement('div');
    m.className = 'modal';
    let soltar = () => {};
    const cerrar = () => { m.remove(); soltar(); };
    const pintar = (foco) => {
      m.innerHTML = `<div class="panel col" role="dialog" aria-modal="true" aria-labelledby="aj-titulo">
        <h2 id="aj-titulo">Ajustes</h2>
        <label class="fila"><span class="crece">${icono('sonido')} Sonido</span><input type="checkbox" role="switch" id="aj-sonido" ${Ajustes.sonido ? 'checked' : ''}></label>
        <label class="fila"><span class="crece">${icono('movil')} Vibración</span><input type="checkbox" role="switch" id="aj-vibracion" ${Ajustes.vibracion ? 'checked' : ''}></label>
        <label class="fila"><span class="crece">${icono('libro')} Ilustraciones<br><span class="suave">En «¿Sabías que…?», desde Wikipedia (usan datos)</span></span><input type="checkbox" role="switch" id="aj-imagenes" ${Ajustes.imagenes ? 'checked' : ''}></label>
        <div id="aj-tema-t">Tema</div>
        <div class="segmentado" role="group" aria-labelledby="aj-tema-t">${[['oscuro', 'Oscuro'], ['claro', 'Claro'], ['sistema', 'Sistema']].map(([k, l]) => `<button data-tema="${k}" class="${Ajustes.tema === k ? 'sel' : ''}" aria-pressed="${Ajustes.tema === k}">${l}</button>`).join('')}</div>
        <a class="btn fantasma bloque" href="${enlaceSoporte()}">Enviar sugerencia o error</a>
        <button class="btn ambar bloque" id="aj-cerrar">Listo</button>
      </div>`;
      m.querySelector('#aj-sonido').onchange = (e) => { Ajustes.set('sonido', e.target.checked); sfx.click(); };
      m.querySelector('#aj-vibracion').onchange = (e) => { Ajustes.set('vibracion', e.target.checked); vibrate(40); };
      m.querySelector('#aj-imagenes').onchange = (e) => { Ajustes.set('imagenes', e.target.checked); };
      m.querySelectorAll('[data-tema]').forEach((b) => { b.onclick = () => { Ajustes.set('tema', b.dataset.tema); pintar(`[data-tema="${b.dataset.tema}"]`); }; });
      m.querySelector('#aj-cerrar').onclick = cerrar;
      const f = m.querySelector(foco || '#aj-cerrar');
      if (f && m.isConnected) f.focus();
    };
    m.addEventListener('click', (e) => { if (e.target === m) cerrar(); });
    soltar = modalAccesible(m, cerrar);
    pintar();
    document.body.appendChild(m);
    m.querySelector('#aj-cerrar').focus();
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
    avisoFallo.innerHTML = '<span>Ups, algo ha fallado. Puedes seguir jugando.</span><span class="fila"><button class="btn peq" id="fallo-seguir">Seguir</button><button class="btn fantasma peq" id="fallo-inicio">Inicio</button></span>';
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
      if (num) { (num.querySelector('.n') || num).textContent = sec; num.classList.toggle('urgente', sec <= 5); }
      if (sec !== lastSec && sec <= 5 && sec > 0) sfx.tick();
      lastSec = sec;
      if (left <= 0) { stopTimer(); onEnd(); }
    };
    tickFn();
    timer = setInterval(tickFn, 100);
  }

  /** La web todavía no sirve anuncios (en Android los sirve AdMob): sin hueco ni intersticial simulados. */
  const ADS_WEB = false;
  function showBanner(show) { $banner.classList.toggle('oculto', !(show && ADS_WEB)); }

  let tituloAnterior = '';
  function render(html, banner = false) {
    stopTimer();
    const teniaFoco = $app.contains(document.activeElement);
    $app.innerHTML = html;
    showBanner(banner);
    window.scrollTo(0, 0);
    // Pantalla nueva (otro título): entrada corta y, si se jugaba con teclado, el foco va al título
    const h = $app.querySelector('h1, h2');
    const titulo = h ? h.textContent : '';
    if (titulo !== tituloAnterior) {
      $app.classList.remove('entra');
      void $app.offsetWidth;
      $app.classList.add('entra');
      if (h && teniaFoco) { h.tabIndex = -1; h.focus({ preventScroll: true }); }
    }
    tituloAnterior = titulo;
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
      el.innerHTML = `<div class="aprende-titulo">${icono('bombilla')} ¿Sabías que…?</div><p>${esc(texto)}</p>`;
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

    /** Botones buena / normal / mala (opcionales). Cada dispositivo vota una vez por pregunta. */
    widget(qid) {
      const done = store.get('rated', []).includes(qid);
      return `<div class="valorar" data-q="${esc(qid)}">${done ? '<span class="suave">Ya has valorado esta pregunta</span>'
        : '<span class="suave">¿Qué te ha parecido la pregunta?</span><span class="fila"><button class="btn musgo peq" data-r="up" aria-label="Pregunta buena">¡Buena!</button><button class="btn fantasma peq" data-r="meh" aria-label="Pregunta normal">Normal</button><button class="btn sangre peq" data-r="down" aria-label="Pregunta mala">Mala</button></span>'}</div>`;
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
        <div class="portada-cielo">
          <img class="portada-img" src="img/portada.jpg" alt="Un campesino salta del carro mientras el caballo frena al borde del barranco"
            onerror="this.remove()">
        </div>
        <h1>¡Para el Carro!</h1>
        <p class="lema">Sube la cifra, carga el carro… y reza para que el puente aguante.</p>
        <div class="col">
          <button class="btn ambar bloque gordo" id="crear">${icono('amigos')} Crear sala con amigos</button>
          <form class="unirse" id="form-unirse">
            <label class="crece" style="flex:1;min-width:0">
              <span class="suave" style="position:absolute;width:1px;height:1px;overflow:hidden">Código de la sala</span>
              <input type="text" id="codigo-portada" maxlength="5" autocomplete="off" autocapitalize="characters" placeholder="CÓDIGO">
            </label>
            <button class="btn azul" id="unirse" type="submit">¡Entrar!</button>
          </form>
          <button class="btn bloque" id="jugar">${icono('movil')} Un solo móvil para todos</button>
          <button class="btn fantasma bloque" id="reglas">${icono('libro')} ¿Cómo se juega?</button>
        </div>
      </section>`, true);
    on('#jugar', 'click', () => { sfx.click(); screenLobby(); });
    on('#crear', 'click', () => { sfx.click(); window.Online.screenCreate(); });
    // Con el código escrito entra directamente; sin código abre la pantalla de unirse
    on('#form-unirse', 'submit', (e) => {
      e.preventDefault(); sfx.click();
      window.Online.screenJoin($app.querySelector('#codigo-portada').value.trim().toUpperCase());
    });
    on('#reglas', 'click', screenRules);
  }

  /**
   * Elegir personaje, a pantalla completa: ficha grande con nombre y frase, rejilla con los 16 y los ya
   * cogidos marcados con quién los tiene (no se pueden repetir), botón al azar y, si se pasa `opts.nombre`,
   * el nombre del jugador. `used`: ids ocupados o [{ avatar, name }].
   * Llama a onPick(avatar | null, nombre) al confirmar (null si se cierra sin elegir).
   */
  function avatarPicker(current, used, onPick, opts = {}) {
    const ocupados = new Map(used.map((u) => (typeof u === 'string' ? [u, ''] : [u.avatar, u.name || ''])));
    const libres = AVATARS.filter((a) => !ocupados.has(a));
    let elegido = current && !ocupados.has(current) ? current : libres[0] || current;
    let nombre = opts.nombre;
    const capa = document.createElement('div');
    capa.className = 'selector';
    capa.setAttribute('role', 'dialog');
    capa.setAttribute('aria-modal', 'true');
    capa.setAttribute('aria-label', 'Elige tu personaje');
    const ficha = () => {
      const pj = personaje(elegido);
      return `<div class="ficha" style="background:${pj.color}" aria-live="polite">
        <div class="ficha-av">${avatarHTML(elegido)}</div>
        <div><div class="ficha-nombre">${esc(pj.nombre)}</div><div class="ficha-frase">${esc(pj.frase)}</div></div>
      </div>`;
    };
    const pintar = () => {
      capa.innerHTML = `<div class="selector-caja">
        <div class="selector-cab">
          <button class="btn-redondo" id="sel-cerrar" aria-label="Volver sin cambiar">${icono('atras')}</button>
          <h2>${esc(opts.titulo || '¡Elige tu personaje!')}</h2>
        </div>
        <div id="sel-ficha">${ficha()}</div>
        ${nombre !== undefined ? `<label><span style="font-family:var(--titulos);font-weight:800;font-size:1.1rem">¿Cómo te llamas?</span>
          <input type="text" id="sel-nombre" maxlength="14" value="${esc(nombre)}" autocomplete="off"></label>` : ''}
        <div class="rejilla-personajes" role="radiogroup" aria-label="Personajes">
          ${AVATARS.map((a) => {
            const pj = personaje(a), de = ocupados.get(a);
            const pillado = ocupados.has(a);
            return `<button class="personaje${a === elegido ? ' sel' : ''}" data-a="${a}" role="radio" aria-checked="${a === elegido}"
              style="background:${pj.color}" ${pillado ? 'disabled' : ''}
              aria-label="${esc(pj.nombre)}${pillado ? `, ya elegido${de ? ' por ' + esc(de) : ''}` : ''}">${avatarHTML(a)}${pillado && de ? `<span class="pillado">${esc(de)}</span>` : ''}</button>`;
          }).join('')}
        </div>
        <div class="selector-pie">
          <button class="btn" id="sel-azar" aria-label="Personaje al azar">${icono('dado')}</button>
          <button class="btn ambar gordo" id="sel-ok">¡Me subo al carro!</button>
        </div>
      </div>`;
      const cerrar = (a) => { capa.remove(); soltar(); onPick(a, nombre); };
      capa.querySelector('#sel-cerrar').onclick = () => cerrar(null);
      capa.querySelector('#sel-ok').onclick = () => { sfx.click(); cerrar(elegido); };
      capa.querySelector('#sel-azar').onclick = () => {
        const otros = libres.filter((a) => a !== elegido);
        if (otros.length) { elegido = otros[Math.floor(Math.random() * otros.length)]; feedback('quedar'); actualizar(); }
      };
      const campo = capa.querySelector('#sel-nombre');
      if (campo) campo.addEventListener('input', () => { nombre = campo.value; });
      capa.querySelectorAll('[data-a]').forEach((b) => b.addEventListener('click', () => { elegido = b.dataset.a; sfx.click(); actualizar(); }));
      // Grupo de radio: solo el elegido está en el orden de Tab; las flechas cambian de personaje
      const rejilla = capa.querySelector('.rejilla-personajes');
      rejilla.addEventListener('keydown', (e) => {
        const pasos = { ArrowRight: 1, ArrowDown: 4, ArrowLeft: -1, ArrowUp: -4 }[e.key];
        if (!pasos) return;
        e.preventDefault();
        const botones = [...rejilla.querySelectorAll('[data-a]:not([disabled])')];
        const i = botones.findIndex((b) => b.dataset.a === elegido);
        const sig = botones[(i + Math.sign(pasos) * (Math.abs(pasos) === 4 ? Math.min(4, botones.length) : 1) + botones.length) % botones.length];
        if (!sig) return;
        elegido = sig.dataset.a; actualizar(); sig.focus();
      });
    };
    // Al cambiar de personaje solo se repintan la ficha y la selección (el nombre no pierde el foco)
    const actualizar = () => {
      capa.querySelector('#sel-ficha').innerHTML = ficha();
      capa.querySelectorAll('[data-a]').forEach((b) => {
        const sel = b.dataset.a === elegido;
        b.classList.toggle('sel', sel);
        b.setAttribute('aria-checked', String(sel));
        b.tabIndex = sel ? 0 : -1;
      });
    };
    const soltar = modalAccesible(capa, () => { capa.remove(); soltar(); onPick(null, nombre); });
    pintar();
    document.body.appendChild(capa);
    actualizar();
    const primero = capa.querySelector('.personaje.sel') || capa.querySelector('#sel-ok');
    if (primero) primero.focus();
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
          <li>Para subir hay que subir de verdad: en los números, <b>al menos un 10 %</b> (en las fechas, un año).
            Y cada jugador puede subir <b>como mucho ${MAX_SUBIDAS} veces</b> por ronda.</li>
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
        <p class="suave">${icono('diana')} <b>¡Clavada!</b> +2 extra al conductor si cruza y su cifra está a menos del 10 % de la respuesta (o a 5 años en las fechas).</p>
      </div>
      <button class="btn bloque" id="volver">Volver</button>`, true);
    on('#volver', 'click', screenHome);
  }

  function screenLobby() {
    const taken = (exceptId) => G.players.filter((p) => p.id !== exceptId).map((p) => p.avatar);
    const rows = G.players.map((p, i) => `
      <div class="jugador">
        <button class="avatar-btn" data-avatar="${p.id}" style="background:${personaje(p.avatar).color}" aria-label="Cambiar personaje de ${esc(p.name)} (ahora ${esc(personaje(p.avatar).nombre)})">${avatarHTML(p.avatar, 'grande')}</button>
        <input type="text" maxlength="14" value="${esc(p.name)}" data-name="${p.id}" aria-label="Nombre del jugador ${i + 1}">
        ${i === 0 ? `<span class="corona">${icono('corona')} Jefe</span>` : `<button class="btn fantasma peq" data-del="${p.id}" aria-label="Quitar a ${esc(p.name)}" ${G.players.length <= MIN_PLAYERS ? 'disabled' : ''}>${icono('cerrar')}</button>`}
      </div>`).join('');
    render(`
      <div class="cabecera"><button class="btn peq" id="atras">${icono('atras')} Inicio</button></div>
      <h2 style="margin-top:14px">¿Quién se sube? (${G.players.length}/${MAX_PLAYERS})</h2>
      <p class="suave" style="margin:0">Toca un personaje para cambiarlo.</p>
      <div class="panel">
        ${rows}
        <button class="btn bloque peq" id="add" ${G.players.length >= MAX_PLAYERS ? 'disabled' : ''}>${icono('mas')} Añadir jugador</button>
      </div>
      <h2>${icono('corona')} El jefe decide</h2>
      <div class="panel col">
        <div class="fila"><span class="crece">Número de rondas</span>
          <div class="contador"><button class="btn peq" id="menos" aria-label="Menos rondas">${icono('menos')}</button><b id="nr" aria-live="polite">${G.rounds}</b><button class="btn peq" id="mas" aria-label="Más rondas">${icono('mas')}</button></div>
        </div>
        <div>Tipo de preguntas</div>
        <div class="segmentado">
          ${[['fecha', 'calendario', 'Fechas'], ['numero', 'numero', 'Números'], ['mezcla', 'mezcla', 'Mezcla']].map(([k, i, l]) => `<button data-mode="${k}" class="${G.mode === k ? 'sel' : ''}" aria-pressed="${G.mode === k}">${icono(i)} ${l}</button>`).join('')}
        </div>
        <label class="fila"><span class="crece"><b>Modo barbaridades</b><br><span class="suave">−1 a quien diga un disparate (10 veces más o menos, o más de 100 años)</span></span>
          <input type="checkbox" role="switch" id="barbaridades" ${G.barbaridades ? 'checked' : ''}></label>
      </div>
      <button class="btn ambar bloque gordo" id="empezar">¡Arre, burra! Empezar</button>`, true);

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
      const ocupados = G.players.filter((x) => x.id !== p.id).map((x) => ({ avatar: x.avatar, name: x.name }));
      avatarPicker(p.avatar, ocupados, (a, nombre) => {
        if (a) p.avatar = a;
        if (typeof nombre === 'string') p.name = nombre.slice(0, 14);
        save(); screenLobby();
      }, { nombre: p.name, titulo: `Jugador ${G.players.indexOf(p) + 1}: ¿quién eres?` });
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
    return `<div class="cabecera"><span class="ronda">RONDA ${G.round}/${G.rounds}</span><span class="etiqueta ${G.R.q.tipo}">${G.R.q.tipo === 'fecha' ? `${icono('calendario')} Fecha` : `${icono('numero')} Número`}</span></div>`;
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
      ${q.tipo === 'fecha' ? '<label class="fila suave" style="gap:6px"><input type="checkbox" role="switch" id="ac"> Antes de Cristo (a.C.)</label>' : ''}
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
      <div class="turno-cab"><h2>¡Abres tú, ${esc(player.name)}!</h2><div class="segundos" role="timer"><span class="n">${OPEN_SECONDS}</span><span class="sr-only"> segundos</span></div></div>
      <div class="temporizador" aria-hidden="true"><div style="width:100%"></div></div>
      <p class="suave" style="margin:4px 0 0">Di una cifra y te montas en la burra como conductor.</p>
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
      const off = p.online === false ? ` ${icono('desconectado', 'desconectado')}` : '';
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

  /** Botones rápidos para subir: el mínimo permitido y saltos grandes. */
  function pasosSubida(q) {
    return q.tipo === 'fecha'
      ? [['+1', '1'], ['+5', '5'], ['+25', '25'], ['+100', '100']]
      : [['Mín.', 'min'], ['+50 %', 'p50'], ['×2', 'x2'], ['×10', 'x10']];
  }

  /** La cifra que propone cada botón rápido (nunca por debajo del mínimo). */
  function cifraSubida(q, bid, step) {
    const min = Logic.minSubida(q, bid);
    if (step === 'min') return min;
    if (/^\d+$/.test(step)) return Math.max(min, bid + Number(step));
    if (!(bid > 0)) return min;
    const v = step === 'p50' ? bid * 1.5 : step === 'x2' ? bid * 2 : bid * 10;
    return Math.max(min, redondea(v));
  }

  /**
   * La barra para subir: f = 0 es la subida mínima y f = 1 el tope (×10 en los números, +200 años en las
   * fechas). En los números avanza en escala logarítmica: los primeros milímetros son subidas pequeñas
   * y al final, saltos grandes. Mismo cálculo que GameEngine.cifraBarra en Android.
   */
  function cifraBarra(q, bid, f) {
    const min = Logic.minSubida(q, bid);
    const t = Math.min(1, Math.max(0, f));
    if (q.tipo === 'fecha') return Math.round(min + t * BARRA_FECHAS);
    if (!(min > 0)) return min;
    return Math.max(min, redondea(min * Math.pow(BARRA_NUMEROS, t)));
  }

  /** La barra mueve la cifra del campo (que se puede seguir escribiendo a mano). */
  function bindBarra(q, bid, field) {
    const barra = document.querySelector('#barra-subir');
    if (!barra) return;
    const decir = () => barra.setAttribute('aria-valuetext', fmtQ(cifraBarra(q, bid, Number(barra.value) / 1000), q));
    decir();
    barra.addEventListener('input', () => { field.set(cifraBarra(q, bid, Number(barra.value) / 1000)); decir(); });
  }

  /** 13.290 → 13.300 (3 cifras significativas), para que los botones den cifras limpias. */
  function redondea(v) {
    if (!(v > 0)) return v;
    const paso = Math.pow(10, Math.floor(Math.log10(v)) - 2);
    const r = Math.round(v / paso) * paso;
    return paso < 1 ? Number(r.toFixed(12)) : r;
  }

  /** Las subidas que quedan, como puntos (amarillo = disponible). */
  function puntosSubida(quedan) {
    return `<span class="puntos-subida" role="img" aria-label="Te quedan ${quedan} de ${MAX_SUBIDAS} subidas">${
      Array.from({ length: MAX_SUBIDAS }, (_, i) => `<i class="${i < quedan ? '' : 'gastado'}"></i>`).join('')}</span>`;
  }

  /** Tarjeta "Paco va en la burra con 2.000 m". */
  function tarjetaPuja(driver, bid, q) {
    return `<div class="tarjeta-puja">
      <div class="jinete" aria-hidden="true">${ico('burra')}${avatarHTML(driver.avatar)}</div>
      <div><div class="quien">${esc(driver.name)} va en la burra con</div><div class="cifra-grande">${esc(fmtQ(bid, q))}</div></div>
    </div>`;
  }

  /** Botón "Subir la cifra" con su panel, o el aviso de que ya no le quedan subidas. */
  function subirHTML(q, quedan, minimo, stepBtns) {
    if (quedan <= 0) {
      return `<button class="btn ambar bloque" id="subir" disabled>${ico('subir')} Ya has subido ${MAX_SUBIDAS} veces</button>`;
    }
    return `
        <button class="btn ambar bloque gordo" id="subir">${ico('subir')} ¡Súbela! ${puntosSubida(quedan)}</button>
        <div class="panel col" id="panel-subir" hidden>
          <div class="subida-cab"><b>¡Súbela!</b>${puntosSubida(quedan)}</div>
          ${cifraInput(q, 'Nueva cifra (mayor)')}
          <input type="range" id="barra-subir" class="barra-subir" min="0" max="1000" value="0" aria-label="Cuánto subir la cifra">
          <div class="barra-etiquetas"><span>mín. ${esc(fmtQ(minimo, q))}</span><span>${q.tipo === 'fecha' ? '+200 años' : '×10'}</span></div>
          <div class="botones-rapidos">${stepBtns.map(([l, s]) => `<button class="btn peq" data-step="${s}">${l}</button>`).join('')}</div>
          <button class="btn ambar bloque gordo" id="ok-subir" disabled>${ico('burra')} ¡Me monto en la burra!</button>
        </div>`;
  }

  function screenTurn(player) {
    const R = G.R, q = R.q;
    const driver = playerById(R.driver);
    const stepBtns = pasosSubida(q);
    const quedan = MAX_SUBIDAS - Logic.subidas(R, player.id);
    const minimo = Logic.minSubida(q, R.bid);
    render(`<div class="turno-rejilla"><div>
      ${header()}
      <div class="turno-cab"><h2>¡Te toca, ${esc(player.name)}!</h2><div class="segundos" role="timer"><span class="n">${TURN_SECONDS}</span><span class="sr-only"> segundos</span></div></div>
      <div class="temporizador" aria-hidden="true"><div style="width:100%"></div></div>
      ${questionPanel()}
      ${tarjetaPuja(driver, R.bid, q)}
      <div id="rig"></div>
      </div><div>
      <div class="col">
        ${subirHTML(q, quedan, minimo, stepBtns)}
        <div class="dos">
          <button class="btn musgo gordo" id="quedar">${ico('carreta')} Me quedo</button>
          <button class="btn sangre gordo" id="bajar">${ico('bajar')} Me bajo</button>
        </div>
      </div>
      ${cartPanel()}</div></div>`);

    const okBtn = $app.querySelector('#ok-subir');
    const valida = (v) => quedan > 0 && v !== null && Logic.subidaValida(q, R.bid, v);
    const field = bindCifra(q, (v) => { okBtn.disabled = !valida(v); });
    const raise = (step) => cifraSubida(q, R.bid, step);
    bindBarra(q, R.bid, field);
    $app.querySelectorAll('[data-step]').forEach((el) => el.addEventListener('click', () => {
      field.set(raise(el.dataset.step));
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
      field.set(raise('min'));
      field.focus();
    });
    okBtn.addEventListener('click', () => { const v = field.read(); if (valida(v)) act('subir', v); });
    on('#quedar', 'click', () => act('quedar'));
    on('#bajar', 'click', () => act('bajar'));
    startTimer(TURN_SECONDS, () => {
      const v = field.read();
      const panel = $app.querySelector('#panel-subir');
      const raising = panel && !panel.hidden;
      if (raising && valida(v)) return act('subir', v);
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
        <div class="revelacion" id="rev" aria-live="polite">${avatarHTML(driver.avatar)} apuesta por <span style="color:#FFC93C">${esc(fmtQ(R.bid, q))}</span></div>
      </div>
      <div id="fin"></div>
      <button class="btn bloque peq" id="saltar" style="margin-top:12px">Saltar ${icono('flecha')}</button>`);
    const scene = window.BridgeScene.play($app.querySelector('#puente'), {
      driver: driver.avatar,
      passengers,
      success: result.success,
      onSuspense() {
        sfx.creak();
        vibrate(80);
        $app.querySelector('#rev').innerHTML = `Respuesta real: <span style="color:${result.success ? '#7BE0A8' : '#FF8A80'}">${esc(fmtQ(q.a, q))}</span>`;
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
      <h2 class="resultado-titulo ${result.success ? 'ok' : 'ko'}">${result.success ? '¡El carro cruzó el puente!' : '¡Al río con la burra!'}</h2>
      ${result.bullseye ? `<p class="centro" style="color:var(--ambar-claro);font-weight:800">${icono('diana')} ¡Clavada! +2 para el conductor</p>` : ''}
      ${barbaridadesHTML(result.barbaridades, G.players, q)}
      <div class="panel">
        <div class="pregunta" style="font-size:1rem">${esc(q.q)}</div>
        <p>Respuesta: <b style="color:var(--ambar-claro)">${esc(fmtQ(q.a, q))}</b> · ${tag(driver)} dijo <b>${esc(fmtQ(R.bid, q))}</b></p>
        ${q.n ? `<p class="suave" style="margin:0">${icono('bombilla')} ${esc(q.n)}</p>` : ''}
        ${Aprende.hueco(q.id)}
        ${Ratings.widget(q.id)}
      </div>
      <div class="panel"><table class="puntos">
        <tr class="suave"><td>Jugador</td><td class="num">Ronda</td><td class="num">Total</td></tr>${rows}
      </table></div>
      <button class="btn ambar bloque" id="seguir">${last ? `${icono('trofeo')} Ver el podio` : `Siguiente ronda ${icono('flecha')}`}</button>`, true);
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
    el.setAttribute('role', 'alertdialog');
    el.setAttribute('aria-modal', 'true');
    el.setAttribute('aria-label', '¡Qué barbaridad!');
    el.innerHTML = `<div class="abuela-tarjeta">
      <div class="abuela-fila">
        <div class="abuela-bocadillo" aria-label="${esc(textoVoz(voz))}">${voz.tramos.map((t) => `<span class="palabra">${esc(t[1])}</span>`).join(' ')}</div>
        <div class="abuela-cuerpo"><img src="img/avatares/abuela.svg" alt="La abuela"><span class="dedo" aria-hidden="true">${icono('flecha')}</span></div>
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
      <button class="btn ambar" id="abuela-seguir">Seguir ${icono('flecha')}</button></div>`;
    document.body.appendChild(el);
    const timers = [];
    vozAbuela(voz, () => {
      el.querySelectorAll('.palabra').forEach((w, i) => {
        timers.push(setTimeout(() => w.classList.add('dicha'), voz.tramos[i][0] * 1000));
      });
    });
    vibrate([60, 40, 60, 40, 120]);
    return new Promise((resolve) => {
      let soltar = () => {};
      const fin = () => { timers.forEach(clearTimeout); if (el.isConnected) { el.remove(); soltar(); resolve(); } };
      soltar = modalAccesible(el, fin);
      el.querySelector('#abuela-seguir').onclick = fin;
      el.querySelector('#abuela-seguir').focus();
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
    return `<div class="panel barbaridades"><b>¡Qué barbaridad!</b> (−1)<br>${lista.map((b) => {
      const p = players.find((x) => x.id === b.id) || { name: '¿?', avatar: '🙂' };
      return `${avatarHTML(p.avatar)} ${esc(p.name)} ${esc(queHizo(b, players, q))} ${b.tipo === 'alto' ? '(¡se pasó!)' : '(¡se quedó cortísimo!)'}`;
    }).join('<br>')}</div>`;
  }

  /** Anuncio intersticial: en Android lo sirve AdMob; en la web, de momento, ninguno. */
  function showInterstitial(onClose) {
    stopTimer();
    if (!ADS_WEB) { onClose(); return; }
    const el = document.createElement('div');
    el.className = 'intersticial';
    el.setAttribute('role', 'dialog'); el.setAttribute('aria-modal', 'true'); el.setAttribute('aria-label', 'Anuncio');
    el.innerHTML = '<div class="suave">Anuncio</div><div class="caja">Aquí irá un anuncio a pantalla completa</div><button class="btn fantasma" id="cerrar-ad" disabled>Cerrar en 5</button>';
    document.body.appendChild(el);
    const btn = el.querySelector('#cerrar-ad');
    let n = 5;
    const iv = setInterval(() => {
      n--;
      if (n > 0) { btn.textContent = `Cerrar en ${n}`; return; }
      clearInterval(iv); btn.disabled = false; btn.textContent = 'Cerrar';
    }, 1000);
    btn.addEventListener('click', () => { el.remove(); onClose(); });
  }

  function screenFinal() {
    evento('partida_fin', { modo: 'local', rondas: G.round });
    const ranking = G.players.slice().sort((a, b) => b.score - a.score);
    const [p1, p2, p3] = ranking;
    const peana = (p, h, pos) => p ? `<div><div class="podio-av">${avatarHTML(p.avatar, 'grande')}</div><div>${esc(p.name)}</div><div class="peana" style="height:${h}px">${pos}<br>${p.score}</div></div>` : '<div></div>';
    render(`
      <h1 class="centro">¡${esc(p1.name)} gana!</h1>
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
    Logic, AVATARS, OPEN_SECONDS, TURN_SECONDS, MIN_PLAYERS, MAX_PLAYERS, AD_EVERY_ROUNDS, MAX_SUBIDAS,
    pasosSubida, cifraSubida, subirHTML, cifraBarra, bindBarra, tarjetaPuja, icono, personaje,
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
