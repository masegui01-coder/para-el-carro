// Salas privadas online: cada jugador con su móvil, unidos por un código de 5 letras o un enlace ?sala=CODIGO.
//
// Firebase Realtime Database (rooms/CODIGO):
//   meta     { host, created, status: 'lobby'|'playing'|'final', rounds, mode }
//   players  { pid: { name, avatar, order, online } }
//   state    lo publica el anfitrión: fase, pregunta (sin la respuesta hasta resolver), puja, plazos, puntos
//   actions  cola de jugadas que envían los jugadores; solo el anfitrión las aplica (Logic de game.js)
// El anfitrión (quien crea la sala, el jefe) es la autoridad: valida turnos, aplica plazos y puntúa.
(function () {
  'use strict';

  const P = () => window.ParaElCarro; // game.js se carga después
  const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // sin I, O, 0, 1 para que no se confundan
  const HOST_GRACE_MS = 1500;  // margen para que llegue una jugada enviada justo al final del plazo
  const OFFLINE_SKIP_MS = 3000; // si le toca a alguien desconectado, se le pasa el turno enseguida
  const BRIDGE_MS = 10500;     // lo que dura la animación del puente antes de ver los puntos
  const BARBARIDAD_MS = 4800;  // y la abuela, si alguien ha dicho una barbaridad

  let db = null;
  let offset = 0;
  const now = () => Date.now() + offset;

  function connect() {
    if (db) return db;
    firebase.initializeApp(window.FIREBASE_CONFIG);
    db = firebase.database();
    db.ref('.info/serverTimeOffset').on('value', (s) => { offset = s.val() || 0; });
    return db;
  }

  const newId = () => Math.random().toString(36).slice(2, 10) + Date.now().toString(36).slice(-4);
  const randomCode = () => Array.from({ length: 5 }, () => CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)]).join('');
  const inviteLink = (code) => `${location.origin}${location.pathname}?sala=${code}`;
  const clean = (o) => JSON.parse(JSON.stringify(o)); // Firebase no admite undefined
  const roomRef = (path = '') => db.ref(`rooms/${S.code}${path ? '/' + path : ''}`);

  // ------------------------------------------------------------ Estado local de la sala

  const S = {
    code: null, me: null, meta: null, players: {}, state: null,
    listeners: [], lastSeq: -1, adShownRound: 0, sent: false,
  };

  const playerList = () => Object.entries(S.players)
    .map(([id, p]) => ({ id, ...p }))
    .sort((a, b) => a.order - b.order);
  const isHost = () => S.meta && S.meta.host === S.me;
  const pl = (id) => (S.state && S.state.players || playerList()).find((p) => p.id === id);
  const live = (p) => ({ ...p, online: S.players[p.id] ? S.players[p.id].online !== false : false });

  function listen(ref, ev, fn) {
    ref.on(ev, fn);
    S.listeners.push(() => ref.off(ev, fn));
  }

  function leaveRoom() {
    S.listeners.forEach((off) => off());
    S.listeners = [];
    H.stop();
    S.code = S.me = S.meta = S.state = null;
    S.players = {};
    S.lastSeq = -1;
    S.prevState = null;
    P().store.set('online', null);
    history.replaceState(null, '', location.pathname);
  }

  // ------------------------------------------------------------ Entrar / crear

  function profileForm(title, intro, codeValue, button) {
    const prof = P().store.get('perfil', { name: '', avatar: P().AVATARS[Math.floor(Math.random() * 12)] });
    P().render(`
      <div class="cabecera"><button class="btn fantasma peq" id="atras">← Inicio</button><span>Sala privada</span></div>
      <h2 style="margin-top:12px">${title}</h2>
      <p class="suave">${intro}</p>
      <div class="panel col">
        ${codeValue !== null ? `<label>Código de la sala<input type="text" id="codigo" maxlength="5" autocomplete="off" value="${P().esc(codeValue)}" class="codigo-input" placeholder="ABCDE"></label>` : ''}
        <div class="jugador">
          <button class="avatar-btn" id="avatar" aria-label="Elegir avatar">${P().avatarHTML(prof.avatar, 'grande')}</button>
          <input type="text" id="nombre" maxlength="14" placeholder="Tu nombre" value="${P().esc(prof.name)}">
        </div>
        <div id="error" class="error"></div>
        <button class="btn ambar bloque" id="ok">${button}</button>
      </div>`, true);
    P().on('#atras', 'click', () => { history.replaceState(null, '', location.pathname); P().screenHome(); });
    P().on('#avatar', 'click', () => P().avatarPicker(prof.avatar, [], (a) => {
      if (a) { prof.avatar = a; document.querySelector('#avatar').innerHTML = P().avatarHTML(a, 'grande'); }
    }));
    const codeInput = document.querySelector('#codigo');
    if (codeInput) codeInput.addEventListener('input', () => { codeInput.value = codeInput.value.toUpperCase().replace(/[^A-Z0-9]/g, ''); });
    return {
      read() {
        prof.name = document.querySelector('#nombre').value.trim().slice(0, 14);
        P().store.set('perfil', prof);
        return { name: prof.name, avatar: prof.avatar, code: codeInput ? codeInput.value.trim().toUpperCase() : null };
      },
      error(msg) { document.querySelector('#error').textContent = msg; },
      busy(b) { document.querySelector('#ok').disabled = b; },
    };
  }

  function screenCreate() {
    const form = profileForm('Crear sala privada', 'Serás el jefe de sala: eliges las rondas y empiezas la partida. Luego invitas a tus amigos con el código o el enlace.', null, '🌐 Crear sala');
    document.querySelector('#ok').addEventListener('click', async () => {
      const f = form.read();
      if (!f.name) return form.error('Escribe tu nombre.');
      form.busy(true);
      try {
        connect();
        const pid = newId();
        let code = null;
        for (let i = 0; i < 6 && !code; i++) {
          const c = randomCode();
          const res = await db.ref(`rooms/${c}/meta`).transaction((cur) => cur ? undefined : {
            host: pid, created: firebase.database.ServerValue.TIMESTAMP, status: 'lobby',
            rounds: P().store.get('rounds', 6), mode: P().store.get('mode', 'mezcla'), barbaridades: P().store.get('barbaridades', true),
          });
          if (res.committed) code = c;
        }
        if (!code) throw new Error('No se pudo crear la sala');
        await db.ref(`rooms/${code}/players/${pid}`).set({ name: f.name, avatar: f.avatar, order: Date.now(), online: true });
        enterRoom(code, pid);
      } catch (e) {
        form.busy(false);
        form.error('No hay conexión con el servidor. Inténtalo de nuevo.');
      }
    });
  }

  async function screenJoin(code) {
    // ¿Ya estaba dentro de esta sala (recarga de la página)? Vuelve a entrar directamente.
    const saved = P().store.get('online', null);
    if (saved && code && saved.code === code) {
      try {
        connect();
        const snap = await db.ref(`rooms/${code}/players/${saved.pid}`).once('value');
        if (snap.exists()) return enterRoom(code, saved.pid);
      } catch (e) { /* sigue al formulario */ }
    }
    const form = profileForm('Unirme a una sala', 'Escribe el código que te han pasado (o abre su enlace), tu nombre y elige avatar.', code || '', '🔑 Entrar en la sala');
    document.querySelector('#ok').addEventListener('click', async () => {
      const f = form.read();
      if (!/^[A-HJ-NP-Z2-9]{5}$/.test(f.code || '')) return form.error('El código tiene 5 letras o números.');
      if (!f.name) return form.error('Escribe tu nombre.');
      form.busy(true);
      try {
        connect();
        const snap = await db.ref(`rooms/${f.code}`).once('value');
        const room = snap.val();
        if (!room || !room.meta) { form.busy(false); return form.error('No existe ninguna sala con ese código.'); }
        if (room.meta.status !== 'lobby') { form.busy(false); return form.error('Esa partida ya ha empezado. Espera a la siguiente.'); }
        const others = Object.values(room.players || {});
        if (others.length >= P().MAX_PLAYERS) { form.busy(false); return form.error(`La sala está llena (${P().MAX_PLAYERS} jugadores).`); }
        let avatar = f.avatar;
        if (others.some((p) => p.avatar === avatar)) avatar = P().AVATARS.find((a) => !others.some((p) => p.avatar === a));
        const pid = newId();
        await db.ref(`rooms/${f.code}/players/${pid}`).set({ name: f.name, avatar, order: Date.now(), online: true });
        enterRoom(f.code, pid);
      } catch (e) {
        form.busy(false);
        form.error('No hay conexión con el servidor. Inténtalo de nuevo.');
      }
    });
  }

  function enterRoom(code, pid) {
    leaveRoom();
    S.code = code;
    S.me = pid;
    P().store.set('online', { code, pid });
    history.replaceState(null, '', `?sala=${code}`);

    // Presencia: "online" se pone a false solo si se cierra la página o se pierde la conexión.
    listen(db.ref('.info/connected'), 'value', (s) => {
      if (!s.val() || !S.code) return;
      const me = roomRef(`players/${S.me}/online`);
      me.onDisconnect().set(false);
      me.set(true).catch(() => {});
    });
    listen(roomRef('meta'), 'value', (s) => {
      S.meta = s.val();
      if (!S.meta) { leaveRoom(); alert('La sala se ha cerrado.'); P().screenHome(); return; }
      if (isHost()) H.start(); else H.stop();
      refresh();
    });
    listen(roomRef('players'), 'value', (s) => {
      S.players = s.val() || {};
      if (S.code && S.meta && !S.players[S.me]) { leaveRoom(); alert('Ya no estás en la sala.'); P().screenHome(); return; }
      if (isHost()) H.onPresence();
      if (!S.meta || S.meta.status === 'lobby') refresh();
      else updatePresenceChips();
    });
    listen(roomRef('state'), 'value', (s) => {
      S.state = s.val();
      if (S.state) normalize(S.state);
      refresh();
    });
  }

  /** Firebase pierde los arrays vacíos y los objetos vacíos: los recuperamos. */
  function normalize(st) {
    st.players = st.players || [];
    st.scores = st.scores || {};
    if (st.R) {
      st.R.queue = st.R.queue || [];
      st.R.log = st.R.log || [];
      st.R.status = st.R.status || {};
      st.R.q = st.q;
    }
    if (st.result) {
      // Si todos se bajan del carro, "passengers" llega vacío y Firebase lo elimina
      st.result.passengers = st.result.passengers || [];
      st.result.off = st.result.off || [];
      st.result.delta = st.result.delta || {};
      st.result.barbaridades = st.result.barbaridades || [];
    }
  }

  function refresh() {
    if (!S.meta) return;
    if (S.meta.status === 'lobby' || !S.state) return screenRoomLobby();
    if (S.state.seq === S.lastSeq) return;
    S.lastSeq = S.state.seq;
    renderGame();
  }

  // ------------------------------------------------------------ Sala de espera

  function screenRoomLobby() {
    S.lastSeq = -1;
    const players = playerList();
    const host = isHost();
    const onlineCount = players.filter((p) => p.online !== false).length;
    const link = inviteLink(S.code);
    const modeName = { fecha: '📅 Fechas', numero: '🔢 Números', mezcla: '🎲 Mezcla' };
    P().render(`
      <div class="cabecera"><button class="btn fantasma peq" id="salir">← Salir</button><span>Sala privada</span></div>
      <div class="panel centro" style="margin-top:12px">
        <div class="suave">Código de la sala</div>
        <div class="codigo">${S.code}</div>
        <div class="fila" style="justify-content:center">
          <button class="btn ambar" id="invitar">📤 Invitar amigos</button>
          <button class="btn fantasma" id="copiar">🔗 Copiar enlace</button>
        </div>
        <div class="enlace suave" id="enlace">${P().esc(link)}</div>
      </div>
      <h2>Jugadores (${players.length}/${P().MAX_PLAYERS})</h2>
      <div class="panel col">
        ${players.map((p) => `
          <div class="fila">
            <button class="avatar-btn" ${p.id === S.me ? 'id="mi-avatar"' : 'disabled'}>${P().avatarHTML(p.avatar, 'grande')}</button>
            <span class="crece">${P().esc(p.name)}${p.id === S.me ? ' <span class="suave">(tú)</span>' : ''}</span>
            ${p.id === S.meta.host ? '<span class="corona">👑 Jefe</span>' : ''}
            ${p.online === false ? '<span class="suave">📴</span>' : ''}
            ${host && p.id !== S.me ? `<button class="btn fantasma peq" data-echar="${p.id}" aria-label="Echar">✕</button>` : ''}
          </div>`).join('')}
      </div>
      <h2>👑 ${host ? 'Tú decides' : 'El jefe de sala decide'}</h2>
      <div class="panel col">
        <div class="fila"><span class="crece">Número de rondas</span>
          ${host ? `<div class="contador"><button class="btn peq" id="menos">−</button><b>${S.meta.rounds}</b><button class="btn peq" id="mas">+</button></div>` : `<b class="ambar">${S.meta.rounds}</b>`}
        </div>
        ${host ? `<div class="segmentado">${Object.entries(modeName).map(([k, l]) => `<button data-mode="${k}" class="${S.meta.mode === k ? 'sel' : ''}">${l}</button>`).join('')}</div>`
          : `<div class="fila"><span class="crece">Preguntas</span><b class="ambar">${modeName[S.meta.mode]}</b></div>`}
        ${host ? `<label class="fila"><span class="crece">😱 Modo barbaridades<br><span class="suave">−1 a quien diga un disparate</span></span><input type="checkbox" id="barbaridades" ${S.meta.barbaridades ? 'checked' : ''}></label>`
          : `<div class="fila"><span class="crece">😱 Modo barbaridades</span><b class="ambar">${S.meta.barbaridades ? 'Sí' : 'No'}</b></div>`}
      </div>
      ${host
        ? `<button class="btn ambar bloque" id="empezar" ${onlineCount < P().MIN_PLAYERS ? 'disabled' : ''}>¡Arre, burra! Empezar</button>
           ${onlineCount < P().MIN_PLAYERS ? `<p class="suave centro">Hacen falta al menos ${P().MIN_PLAYERS} jugadores conectados.</p>` : ''}`
        : '<p class="suave centro">⏳ Esperando a que el jefe empiece la partida…</p>'}`, true);

    P().on('#salir', 'click', exitRoom);
    P().on('#invitar', 'click', () => shareInvite(link));
    P().on('#copiar', 'click', () => copy(link, '#copiar'));
    P().on('#mi-avatar', 'click', () => {
      const used = players.filter((p) => p.id !== S.me).map((p) => p.avatar);
      P().avatarPicker(S.players[S.me].avatar, used, (a) => { if (a) roomRef(`players/${S.me}/avatar`).set(a); });
    });
    if (!host) return;
    document.querySelectorAll('[data-echar]').forEach((b) => b.addEventListener('click', () => roomRef(`players/${b.dataset.echar}`).remove()));
    document.querySelectorAll('[data-mode]').forEach((b) => b.addEventListener('click', () => {
      roomRef('meta/mode').set(b.dataset.mode); P().store.set('mode', b.dataset.mode);
    }));
    P().on('#menos', 'click', () => { const r = Math.max(1, S.meta.rounds - 1); roomRef('meta/rounds').set(r); P().store.set('rounds', r); });
    P().on('#mas', 'click', () => { const r = Math.min(30, S.meta.rounds + 1); roomRef('meta/rounds').set(r); P().store.set('rounds', r); });
    P().on('#barbaridades', 'change', (e) => { roomRef('meta/barbaridades').set(e.target.checked); P().store.set('barbaridades', e.target.checked); });
    P().on('#empezar', 'click', () => H.startGame());
  }

  async function shareInvite(link) {
    const text = `¡Súbete al carro! Únete a mi sala de "Para el Carro" con el código ${S.code}:`;
    if (navigator.share) {
      try { await navigator.share({ title: 'Para el Carro', text, url: link }); return; } catch (e) { if (e.name === 'AbortError') return; }
    }
    // Sin menú de compartir: WhatsApp directamente
    window.open(`https://wa.me/?text=${encodeURIComponent(text + ' ' + link)}`, '_blank', 'noopener');
  }

  async function copy(text, btnSel) {
    try {
      await navigator.clipboard.writeText(text);
    } catch (e) {
      const ta = document.createElement('textarea');
      ta.value = text; document.body.appendChild(ta); ta.select();
      try { document.execCommand('copy'); } catch (e2) { /* nada */ }
      ta.remove();
    }
    const b = document.querySelector(btnSel);
    if (b) { const t = b.textContent; b.textContent = '✅ Copiado'; setTimeout(() => { b.textContent = t; }, 1500); }
  }

  async function exitRoom() {
    if (!confirm(isHost() ? '¿Cerrar la sala? Saldrán todos los jugadores.' : '¿Salir de la sala?')) return;
    const code = S.code, me = S.me, host = isHost();
    leaveRoom();
    try {
      if (host) await db.ref(`rooms/${code}`).remove();
      else await db.ref(`rooms/${code}/players/${me}`).remove();
    } catch (e) { /* sin conexión: la presencia lo marcará como desconectado */ }
    P().screenHome();
  }

  // ------------------------------------------------------------ Partida (vista de cada jugador)

  function send(a, v) {
    S.sent = true;
    roomRef('actions').push(clean({ pid: S.me, a, v, seq: S.state.seq }));
  }

  function header(st) {
    return `<div class="cabecera"><span>Ronda ${st.round}/${st.rounds} · Sala ${S.code}</span><span class="etiqueta ${st.q.tipo}">${st.q.tipo === 'fecha' ? '📅 Fecha' : '🔢 Número'}</span></div>`;
  }

  const question = (st) => `<div class="panel"><div class="pregunta">${P().esc(st.q.q)}</div></div>`;
  const tag = (p) => p ? `${P().avatarHTML(p.avatar)} ${P().esc(p.name)}` : '¿?';

  function hostOfflineBanner() {
    const h = S.players[S.meta.host];
    return h && h.online === false ? '<p class="aviso">📴 El jefe de sala se ha desconectado. La partida sigue cuando vuelva.</p>' : '';
  }

  function updatePresenceChips() {
    // Durante la partida no se repinta la pantalla entera (se perdería lo que se está escribiendo)
    const banner = document.querySelector('#aviso-jefe');
    if (banner) banner.innerHTML = hostOfflineBanner();
  }

  function renderGame() {
    const st = S.state;
    S.sent = false;
    const players = st.players.map(live);
    const ph = st.phase;
    const prev = S.prevState; // para animar en la mini escena la última jugada
    S.prevState = st;
    if (ph === 'open') return viewOpen(st);
    if (ph === 'turn') return viewTurn(st, players, prev);
    if (ph === 'bridge') return viewBridge(st, players);
    if (ph === 'score') return viewScore(st, players);
    if (ph === 'final') return viewFinal(st, players);
  }

  function viewOpen(st) {
    const opener = pl(st.opener);
    const mine = st.opener === S.me;
    P().render(`
      ${header(st)}<div id="aviso-jefe">${hostOfflineBanner()}</div>
      ${question(st)}
      <p class="centro">${mine ? 'Te toca abrir: di una cifra.<br><span class="suave">Te montarás en la burra como conductor.</span>' : `⏳ ${tag(opener)} está pensando una cifra…`}</p>
      <div class="segundos">${P().OPEN_SECONDS}</div>
      <div class="temporizador"><div style="width:100%"></div></div>
      ${mine ? `<div class="panel col">
        ${P().cifraInput(st.q, st.q.tipo === 'fecha' ? 'Año, p. ej. 1890' : 'Tu cifra')}
        ${st.q.tipo === 'numero' ? '<div class="botones-rapidos"><button class="btn peq" data-mul="1000">×1.000</button><button class="btn peq" data-mul="1000000">×1 millón</button></div>' : ''}
        <button class="btn ambar bloque" id="ok" disabled>${P().ico('burra')} ¡Me subo a la burra!</button>
      </div>` : ''}`);
    let field = null;
    if (mine) {
      const okBtn = document.querySelector('#ok');
      field = P().bindCifra(st.q, (v) => { okBtn.disabled = v === null || S.sent; });
      document.querySelectorAll('[data-mul]').forEach((b) => b.addEventListener('click', () => {
        const v = field.read(); if (v !== null) field.set(v * Number(b.dataset.mul));
      }));
      okBtn.addEventListener('click', () => {
        const v = field.read();
        if (v === null || S.sent) return;
        okBtn.disabled = true; okBtn.textContent = 'Enviado…';
        P().feedback('abre'); send('abre', v);
      });
      field.focus();
    }
    P().startTimer(P().OPEN_SECONDS, () => {
      // Se acaba el tiempo con una cifra escrita: se envía sola
      if (mine && field && !S.sent) { const v = field.read(); if (v !== null) send('abre', v); }
    }, st.deadline, now);
  }

  function viewTurn(st, players, prev) {
    const R = st.R, q = st.q;
    const turnId = R.queue[0];
    const mine = turnId === S.me;
    const driver = pl(R.driver);
    const myStatus = R.status[S.me];
    const steps = q.tipo === 'fecha' ? [['+1', 1], ['+5', 5], ['+25', 25], ['+100', 100]] : [['+1', 'a1'], ['+10 %', 'p10'], ['×2', 'x2'], ['×10', 'x10']];
    const raise = (s) => {
      const b = R.bid;
      if (typeof s === 'number') return b + s;
      if (s === 'a1') return b + 1;
      if (s === 'p10') return Math.max(b + 1, Math.ceil(b * 1.1));
      return b > 0 ? b * (s === 'x2' ? 2 : 10) : b + 1;
    };
    const where = myStatus === 'conductor' ? `${P().ico('burra')} Vas montado en la burra.`
      : myStatus === 'bajado' ? `${P().ico('bajar')} Te has bajado del carro.` : `${P().ico('carreta')} Vas en el carro.`;
    P().render(`
      ${header(st)}<div id="aviso-jefe">${hostOfflineBanner()}</div>
      ${question(st)}
      <div class="cifra-sub">${tag(driver)} va en la burra con</div>
      <div class="cifra-grande">${P().esc(P().fmtQ(R.bid, q))}</div>
      <div class="segundos" style="margin-top:8px">${P().TURN_SECONDS}</div>
      <div class="temporizador"><div style="width:100%"></div></div>
      <div id="rig"></div>
      ${mine ? `<p class="centro"><b>¡Te toca!</b> ¿Qué haces?</p>
        <div class="col" id="acciones">
          <button class="btn ambar bloque" id="subir">${P().ico('subir')} Subir la cifra</button>
          <div class="panel col" id="panel-subir" hidden>
            <div class="botones-rapidos">${steps.map(([l, s]) => `<button class="btn peq" data-step="${s}">${l}</button>`).join('')}</div>
            ${P().cifraInput(q, 'Nueva cifra (mayor)')}
            <button class="btn ambar bloque" id="ok-subir" disabled>${P().ico('burra')} Me monto en la burra</button>
          </div>
          <button class="btn musgo bloque" id="quedar">${P().ico('carreta')} Me quedo en el carro</button>
          <button class="btn sangre bloque" id="bajar">${P().ico('bajar')} Me bajo del carro</button>
        </div>`
        : `<p class="centro">⏳ Le toca a ${tag(pl(turnId))}<br><span class="suave">${where}</span></p>`}
      ${P().cartPanel(players, R, turnId)}`);
    // Mini escena: los demás ven saltar al que acaba de jugar (el propio jugador ya lo vio al pulsar)
    const rigEl = document.querySelector('#rig');
    const scene = window.BridgeScene;
    const last = R.log[R.log.length - 1];
    const sameRound = prev && prev.round === st.round && prev.R && (prev.phase === 'turn' || prev.phase === 'open');
    if (sameRound && last && last.id !== S.me) {
      if (last.action === 'quedar' || last.action === 'tiempo') { scene.rig(rigEl, players, R); scene.cheer(rigEl, last.id); }
      else scene.rig(rigEl, players, prev.R, R);
    } else {
      scene.rig(rigEl, players, R);
    }
    const mark = () => { const me = rigEl.querySelector(`.rig-av[data-id="${S.me}"]`); if (me) me.classList.add('yo'); };
    mark();
    let field = null;
    let raising = false;
    if (mine) {
      P().vibrate(60);
      const okBtn = document.querySelector('#ok-subir');
      field = P().bindCifra(q, (v) => { okBtn.disabled = v === null || !(v > R.bid); });
      const act = (a, v) => {
        if (S.sent) return;
        S.sent = true; // evita que el temporizador envíe otra jugada mientras salta el muñeco
        document.querySelectorAll('#acciones button').forEach((b) => { b.disabled = true; });
        P().feedback(a);
        // Primero el muñeco se sube, se baja o se alegra; después se envía la jugada
        const after = JSON.parse(JSON.stringify(R));
        let anim;
        try {
          P().Logic.act(players, after, S.me, a, v);
          anim = a === 'quedar' ? scene.cheer(rigEl, S.me) : scene.rig(rigEl, players, R, after);
        } catch (e) { anim = Promise.resolve(); }
        anim.then(() => send(a, v));
      };
      document.querySelectorAll('[data-step]').forEach((el) => el.addEventListener('click', () => {
        const s = el.dataset.step; field.set(raise(/^\d+$/.test(s) ? Number(s) : s));
      }));
      P().on('#subir', 'click', () => {
        raising = true;
        document.querySelector('#panel-subir').hidden = false;
        document.querySelector('#subir').hidden = true;
        field.set(raise(q.tipo === 'fecha' ? 1 : 'a1'));
        field.focus();
      });
      okBtn.addEventListener('click', () => { const v = field.read(); if (v !== null && v > R.bid) act('subir', v); });
      P().on('#quedar', 'click', () => act('quedar'));
      P().on('#bajar', 'click', () => act('bajar'));
    }
    P().startTimer(P().TURN_SECONDS, () => {
      if (!mine || S.sent) return;
      const v = field.read();
      if (raising && v !== null && v > R.bid) send('subir', v);
      // si no, el anfitrión le deja en el carro al vencer el plazo
    }, st.deadline, now);
  }

  function viewBridge(st, players) {
    const R = st.R, q = st.q, res = st.result;
    const driver = pl(R.driver);
    P().render(`
      ${header(st)}
      <div class="panel"><div class="pregunta" style="font-size:1.05rem">${P().esc(q.q)}</div></div>
      <div class="escena">
        <canvas id="puente" width="400" height="260"></canvas>
        <div class="revelacion" id="rev">${P().avatarHTML(driver.avatar)} apuesta por <span style="color:var(--ambar-claro)">${P().esc(P().fmtQ(R.bid, q))}</span></div>
      </div>
      <button class="btn fantasma bloque peq" id="saltar" style="margin-top:10px">Saltar ⏩</button>`);
    const scene = window.BridgeScene.play(document.querySelector('#puente'), {
      driver: driver.avatar,
      passengers: res.passengers.map((id) => (pl(id) || {}).avatar || '🙂'),
      success: res.success,
      onSuspense() {
        P().sfx.creak(); P().vibrate(80);
        const rev = document.querySelector('#rev');
        if (rev) rev.innerHTML = `Respuesta real: <span style="color:${res.success ? '#8fd18f' : '#ff8a73'}">${P().esc(P().fmtQ(res.a, q))}</span>`;
      },
      onImpact(ok) {
        if (ok) { P().sfx.win(); P().vibrate([40, 60, 40, 60, 120]); } else { P().sfx.lose(); P().vibrate([300, 80, 500]); }
      },
    });
    P().on('#saltar', 'click', () => scene.skip());
    scene.done.then(() => P().showBarbaridades(res.barbaridades, st.players, q));
  }

  function viewScore(st, players) {
    const R = st.R, q = st.q, res = st.result;
    const driver = pl(R.driver);
    const last = st.round >= st.rounds;
    const rows = players.slice().sort((a, b) => (st.scores[b.id] || 0) - (st.scores[a.id] || 0)).map((p) => {
      const d = res.delta[p.id] || 0;
      const s = R.status[p.id];
      const ico = P().ico(s === 'conductor' ? 'burra' : s === 'bajado' ? 'bajar' : 'carreta');
      return `<tr><td>${ico} ${tag(p)}${p.id === S.me ? ' <span class="suave">(tú)</span>' : ''}</td><td class="num ${d > 0 ? 'mas' : d < 0 ? 'menos' : ''}">${d > 0 ? '+' : ''}${d}</td><td class="num">${st.scores[p.id] || 0}</td></tr>`;
    }).join('');
    P().render(`
      ${header(st)}<div id="aviso-jefe">${hostOfflineBanner()}</div>
      <div class="resultado-titulo ${res.success ? 'ok' : 'ko'}">${res.success ? '¡El carro cruzó el puente!' : '¡Al río con la burra!'}</div>
      ${res.bullseye ? '<p class="centro" style="color:var(--ambar-claro);font-weight:800">🎯 ¡Clavada! +2 para el conductor</p>' : ''}
      <div class="panel">
        <div class="pregunta" style="font-size:1rem">${P().esc(q.q)}</div>
        <p>Respuesta: <b style="color:var(--ambar-claro)">${P().esc(P().fmtQ(res.a, q))}</b> · ${tag(driver)} dijo <b>${P().esc(P().fmtQ(R.bid, q))}</b></p>
        ${res.n ? `<p class="suave" style="margin:0">💡 ${P().esc(res.n)}</p>` : ''}
        ${P().Ratings.widget(q.id)}
      </div>
      ${P().barbaridadesHTML(res.barbaridades, players, q)}
      <div class="panel"><table class="puntos">
        <tr class="suave"><td>Jugador</td><td class="num">Ronda</td><td class="num">Total</td></tr>${rows}
      </table></div>
      ${isHost() ? `<button class="btn ambar bloque" id="seguir">${last ? '🏆 Ver el podio' : 'Siguiente ronda ➜'}</button>`
        : `<p class="suave centro">⏳ Esperando a que el jefe pase a ${last ? 'el podio' : 'la siguiente ronda'}…</p>`}`, true);
    P().on('#seguir', 'click', () => H.next());
    P().Ratings.bind();
    // Anuncio cada 2 rondas y antes del podio, en la pantalla de puntos (no hay cuenta atrás corriendo)
    if ((last || st.round % P().AD_EVERY_ROUNDS === 0) && S.adShownRound !== st.round) {
      S.adShownRound = st.round;
      P().showInterstitial(() => {});
    }
  }

  function viewFinal(st, players) {
    const ranking = players.slice().sort((a, b) => (st.scores[b.id] || 0) - (st.scores[a.id] || 0));
    const [p1, p2, p3] = ranking;
    const sc = (p) => st.scores[p.id] || 0;
    const peana = (p, h, pos) => p ? `<div><div class="podio-av">${P().avatarHTML(p.avatar, 'grande')}</div><div>${P().esc(p.name)}</div><div class="peana" style="height:${h}px">${pos}<br>${sc(p)}</div></div>` : '<div></div>';
    P().render(`
      <h1 class="centro">🏆 ${P().esc(p1.name)} gana</h1>
      <div class="podio">${peana(p2, 70, '2º')}${peana(p1, 100, '1º')}${peana(p3, 50, '3º')}</div>
      <div class="panel"><table class="puntos">
        ${ranking.map((p, i) => `<tr><td>${i + 1}. ${tag(p)}${p.id === S.me ? ' <span class="suave">(tú)</span>' : ''}</td><td class="num">${sc(p)}</td></tr>`).join('')}
      </table></div>
      <div class="col">
        ${isHost() ? `<button class="btn ambar bloque" id="revancha">Revancha (mismos jugadores)</button>
          <button class="btn fantasma bloque" id="sala">Volver a la sala</button>` : '<p class="suave centro">⏳ El jefe decide si hay revancha…</p>'}
        <button class="btn fantasma bloque" id="salir">Salir de la sala</button>
      </div>`, true);
    P().sfx.win();
    P().on('#revancha', 'click', () => H.startGame());
    P().on('#sala', 'click', () => H.backToLobby());
    P().on('#salir', 'click', exitRoom);
  }

  // ------------------------------------------------------------ Anfitrión (autoridad de la partida)

  const H = {
    running: false,
    g: null,        // estado completo, con la pregunta entera (respuesta incluida)
    timer: null,
    bridgeTimer: null,
    actionsRef: null,
    onAction: null,

    start() {
      if (this.running) return;
      this.running = true;
      this.actionsRef = roomRef('actions');
      this.onAction = (snap) => { this.apply(snap.val()); snap.ref.remove(); };
      this.actionsRef.on('child_added', this.onAction);
      // Si el jefe recarga la página a mitad de partida, recupera el estado publicado
      if (S.meta.status === 'playing' && !this.g) this.resume();
    },

    stop() {
      if (this.actionsRef && this.onAction) this.actionsRef.off('child_added', this.onAction);
      this.actionsRef = this.onAction = null;
      clearTimeout(this.timer); clearTimeout(this.bridgeTimer);
      this.running = false;
      this.g = null;
    },

    async resume() {
      const snap = await roomRef('state').once('value');
      const st = snap.val();
      if (!st || !st.q) return;
      normalize(st);
      const full = window.PREGUNTAS.find((x) => x.id === st.q.id) || { ...st.q, a: st.result ? st.result.a : 0, n: '' };
      this.g = { ...st, q: full };
      if (st.R) this.g.R.q = full;
      this.g.openerIdx = this.g.players.findIndex((p) => p.id === st.opener);
      this.g.openAttempts = 0;
      if (st.phase === 'bridge') this.afterBridge(); else this.schedule();
    },

    publish() {
      const g = this.g;
      const reveal = g.phase === 'bridge' || g.phase === 'score' || g.phase === 'final';
      const st = {
        phase: g.phase, seq: (g.seq = (g.seq || 0) + 1), round: g.round, rounds: g.rounds,
        players: g.players.map(({ id, name, avatar }) => ({ id, name, avatar })),
        q: { id: g.q.id, tipo: g.q.tipo, q: g.q.q, u: g.q.u },
        R: g.R ? { bid: g.R.bid, driver: g.R.driver, status: g.R.status, queue: g.R.queue, log: g.R.log } : null,
        opener: g.opener, deadline: g.deadline || 0, scores: g.scores,
        result: reveal && g.result ? { ...g.result, a: g.q.a, n: g.q.n } : null,
      };
      roomRef('state').set(clean(st));
    },

    startGame() {
      const players = playerList().filter((p) => p.online !== false).map(({ id, name, avatar }) => ({ id, name, avatar }));
      if (players.length < P().MIN_PLAYERS) return;
      // Los que no estén conectados al empezar se quedan fuera de la partida
      playerList().filter((p) => p.online === false).forEach((p) => roomRef(`players/${p.id}`).remove());
      this.g = { players, rounds: S.meta.rounds, mode: S.meta.mode, round: 0, scores: Object.fromEntries(players.map((p) => [p.id, 0])), seq: Date.now() };
      // seq empieza en la hora actual: así nunca coincide con el de una partida anterior de la sala
      S.adShownRound = 0;
      roomRef('meta/status').set('playing');
      this.nextRound();
    },

    nextRound() {
      const g = this.g;
      g.round++;
      g.openerIdx = (g.round - 1) % g.players.length;
      g.openAttempts = 0;
      g.q = P().pickQuestion(g.mode);
      g.R = P().Logic.newRound(g.players, g.openerIdx, g.q);
      g.result = null;
      this.setOpen();
    },

    setOpen() {
      const g = this.g;
      g.phase = 'open';
      g.opener = g.players[(g.openerIdx + g.openAttempts) % g.players.length].id;
      g.deadline = now() + P().OPEN_SECONDS * 1000;
      this.publish();
      this.schedule();
    },

    setTurn() {
      const g = this.g;
      g.phase = 'turn';
      g.deadline = now() + P().TURN_SECONDS * 1000;
      this.publish();
      this.schedule();
    },

    /** Programa el fin del plazo actual (o lo adelanta si al que le toca está desconectado). */
    schedule() {
      clearTimeout(this.timer);
      const g = this.g;
      if (!g || (g.phase !== 'open' && g.phase !== 'turn')) return;
      const who = g.phase === 'open' ? g.opener : g.R.queue[0];
      const offline = S.players[who] ? S.players[who].online === false : true;
      const at = offline ? Math.min(g.deadline, now() + OFFLINE_SKIP_MS) : g.deadline + HOST_GRACE_MS;
      const seq = g.seq;
      this.timer = setTimeout(() => { if (this.g && this.g.seq === seq) this.timeout(); }, Math.max(0, at - now()));
    },

    onPresence() { if (this.g) this.schedule(); },

    timeout() {
      const g = this.g;
      if (g.phase === 'open') {
        // Nadie dijo cifra: abre el siguiente jugador
        g.openAttempts++;
        this.setOpen();
      } else if (g.phase === 'turn') {
        // Se acabó el tiempo: se queda en el carro
        P().Logic.act(g.players, g.R, g.R.queue[0], 'quedar');
        g.R.log[g.R.log.length - 1].action = 'tiempo';
        this.after();
      }
    },

    apply(act) {
      const g = this.g;
      if (!g || !act) return;
      try {
        if (g.phase === 'open' && act.a === 'abre' && act.pid === g.opener && typeof act.v === 'number' && isFinite(act.v)) {
          P().Logic.open(g.players, g.R, act.pid, act.v);
          this.after();
        } else if (g.phase === 'turn' && act.pid === g.R.queue[0] && ['subir', 'quedar', 'bajar'].includes(act.a)) {
          if (act.a === 'subir' && !(typeof act.v === 'number' && act.v > g.R.bid)) return;
          P().Logic.act(g.players, g.R, act.pid, act.a, act.v);
          this.after();
        }
      } catch (e) { /* jugada fuera de turno: se ignora */ }
    },

    after() {
      const g = this.g;
      clearTimeout(this.timer);
      if (g.R.queue.length > 0) return this.setTurn();
      g.result = P().Logic.score(g.players, g.R, !!(S.meta && S.meta.barbaridades));
      g.players.forEach((p) => { g.scores[p.id] = (g.scores[p.id] || 0) + g.result.delta[p.id]; });
      g.phase = 'bridge';
      g.deadline = 0;
      this.publish();
      this.afterBridge();
    },

    afterBridge() {
      clearTimeout(this.bridgeTimer);
      this.bridgeTimer = setTimeout(() => {
        if (!this.g || this.g.phase !== 'bridge') return;
        this.g.phase = 'score';
        this.publish();
      }, BRIDGE_MS + (this.g && this.g.result && this.g.result.barbaridades && this.g.result.barbaridades.length ? BARBARIDAD_MS : 0));
    },

    next() {
      const g = this.g;
      if (!g || g.phase !== 'score') return;
      if (g.round >= g.rounds) {
        g.phase = 'final';
        roomRef('meta/status').set('final');
        this.publish();
      } else {
        this.nextRound();
      }
    },

    backToLobby() {
      clearTimeout(this.timer); clearTimeout(this.bridgeTimer);
      this.g = null;
      roomRef('state').remove();
      roomRef('meta/status').set('lobby');
    },
  };

  // ------------------------------------------------------------ Valoraciones de preguntas (ratings/QID)

  let ratingsPromise = null;
  /** Contadores 👍 😐 👎 de todas las preguntas (una lectura por sesión). */
  function loadRatings() {
    if (!ratingsPromise) {
      ratingsPromise = Promise.resolve()
        .then(() => { connect(); return db.ref('ratings').once('value'); })
        .then((s) => s.val() || {})
        .catch(() => ({}));
    }
    return ratingsPromise;
  }

  function rate(qid, kind) {
    try {
      connect();
      db.ref(`ratings/${qid}/${kind}`).set(firebase.database.ServerValue.increment(1)).catch(() => {});
    } catch (e) { /* sin conexión: el voto se pierde */ }
  }

  window.Online = { screenCreate, screenJoin, loadRatings, rate };
})();
