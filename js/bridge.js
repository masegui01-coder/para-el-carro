// Escena 2D del puente colgante sobre el río tenebroso.
// BridgeScene.play(canvas, opciones) dibuja la burra (con el conductor montado) tirando del carro
// (con los pasajeros) y devuelve { done: Promise, skip() }.
// Fases: 'walking' (hasta el centro) -> 'suspense' (cruje, se revela la cifra) ->
//        'crossing' -> 'crossed'   |   'breaking' -> 'falling' -> 'fell'
(function () {
  'use strict';

  // Coordenadas lógicas; el canvas se escala a su ancho real.
  const W = 400, H = 260;
  const CLIFF_Y = 140, LEFT = 80, RIGHT = 320, MID = 200, HALF = 120, SAG = 18;
  const RIVER_Y = 226;
  const GS = 1.35; // escala del grupo burra + carro
  const EMOJI_FONT = '"Segoe UI Emoji","Apple Color Emoji","Noto Color Emoji",sans-serif';

  const T_WALK = 2800, T_SUSPENSE = 2200, T_CROSS = 2400, T_PARTY = 2200, T_FALL = 4000;

  const bridgeY = (x) => (x <= LEFT || x >= RIGHT) ? CLIFF_Y : CLIFF_Y + SAG * (1 - ((x - MID) / HALF) ** 2);
  const bridgeSlope = (x) => (x <= LEFT || x >= RIGHT) ? 0 : SAG * (-2 * (x - MID) / (HALF * HALF));
  const lerp = (a, b, t) => a + (b - a) * t;
  const clamp01 = (t) => Math.max(0, Math.min(1, t));
  const easeInOut = (t) => t < .5 ? 2 * t * t : 1 - (-2 * t + 2) ** 2 / 2;

  // Números pseudoaleatorios con semilla, para que árboles y tablones no "bailen" entre partidas.
  function rng(seed) {
    return () => { seed = (seed * 16807) % 2147483647; return (seed - 1) / 2147483646; };
  }

  function drawTree(ctx, x, y, len, angle, depth, rand) {
    if (depth === 0 || len < 2) return;
    const x2 = x + Math.cos(angle) * len, y2 = y + Math.sin(angle) * len;
    ctx.lineWidth = Math.max(.6, depth * .9);
    ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x2, y2); ctx.stroke();
    const n = 2 + (rand() < .35 ? 1 : 0);
    for (let i = 0; i < n; i++) {
      drawTree(ctx, x2, y2, len * (.62 + rand() * .18), angle + (rand() - .5) * 1.4, depth - 1, rand);
    }
  }

  function paintBackground(k) {
    const c = document.createElement('canvas');
    c.width = W * k; c.height = H * k;
    const ctx = c.getContext('2d');
    ctx.scale(k, k);
    const rand = rng(7);

    const sky = ctx.createLinearGradient(0, 0, 0, H);
    sky.addColorStop(0, '#26303f'); sky.addColorStop(.55, '#151a23'); sky.addColorStop(1, '#0a0c10');
    ctx.fillStyle = sky; ctx.fillRect(0, 0, W, H);

    for (let i = 0; i < 40; i++) {
      ctx.fillStyle = `rgba(255,255,255,${.2 + rand() * .5})`;
      ctx.fillRect(rand() * W, rand() * 90, 1, 1);
    }

    const glow = ctx.createRadialGradient(330, 42, 4, 330, 42, 60);
    glow.addColorStop(0, 'rgba(230,235,255,.55)'); glow.addColorStop(1, 'rgba(230,235,255,0)');
    ctx.fillStyle = glow; ctx.fillRect(250, 0, 150, 110);
    ctx.fillStyle = '#e9edf7'; ctx.beginPath(); ctx.arc(330, 42, 17, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = 'rgba(160,170,190,.35)';
    ctx.beginPath(); ctx.arc(325, 38, 4, 0, 7); ctx.arc(336, 48, 3, 0, 7); ctx.fill();

    // Árboles lejanos
    ctx.strokeStyle = 'rgba(40,48,60,.9)';
    for (let i = 0; i < 9; i++) drawTree(ctx, 20 + i * 45 + rand() * 20, 150, 18 + rand() * 12, -Math.PI / 2, 5, rand);

    // Fondo del barranco
    const gorge = ctx.createLinearGradient(0, CLIFF_Y, 0, H);
    gorge.addColorStop(0, '#1a1f27'); gorge.addColorStop(1, '#07090c');
    ctx.fillStyle = gorge; ctx.fillRect(LEFT - 10, CLIFF_Y, RIGHT - LEFT + 20, H - CLIFF_Y);

    // Acantilados
    const rock = (pts) => {
      ctx.beginPath(); ctx.moveTo(pts[0][0], pts[0][1]);
      pts.slice(1).forEach(([x, y]) => ctx.lineTo(x, y));
      ctx.closePath();
      const g = ctx.createLinearGradient(0, CLIFF_Y, 0, H);
      g.addColorStop(0, '#3a3129'); g.addColorStop(1, '#14110e');
      ctx.fillStyle = g; ctx.fill();
    };
    rock([[0, CLIFF_Y], [LEFT, CLIFF_Y], [LEFT + 8, 168], [LEFT - 4, 196], [LEFT + 6, 230], [LEFT - 2, H], [0, H]]);
    rock([[W, CLIFF_Y], [RIGHT, CLIFF_Y], [RIGHT - 7, 165], [RIGHT + 5, 198], [RIGHT - 6, 232], [RIGHT + 2, H], [W, H]]);
    ctx.fillStyle = '#2f4a32';
    ctx.fillRect(0, CLIFF_Y - 3, LEFT + 2, 5);
    ctx.fillRect(RIGHT - 2, CLIFF_Y - 3, W - RIGHT + 2, 5);

    // Árboles retorcidos en primer plano
    ctx.strokeStyle = '#0d0f13';
    drawTree(ctx, 14, CLIFF_Y, 34, -Math.PI / 2 + .1, 7, rand);
    drawTree(ctx, 388, CLIFF_Y, 38, -Math.PI / 2 - .15, 7, rand);
    return c;
  }

  function drawEmoji(ctx, ch, x, y, size, angle) {
    ctx.save();
    ctx.translate(x, y);
    if (angle) ctx.rotate(angle);
    ctx.font = `${size}px ${EMOJI_FONT}`;
    ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.fillText(ch, 0, 0);
    ctx.restore();
  }

  // Posiciones (locales al grupo) de los pasajeros sobre la carga del carro.
  function seatOf(i, n) {
    const span = Math.min(44, 10 + n * 7);
    const x = n === 1 ? -29 : -29 - span / 2 + (span * i) / (n - 1);
    return { x, y: -46 - (i % 2) * 5 };
  }

  function drawCart(ctx, t, wheelRot, lantern) {
    // Rueda
    ctx.save();
    ctx.translate(-28, -9);
    ctx.strokeStyle = '#2b1a0c'; ctx.lineWidth = 2.4;
    ctx.beginPath(); ctx.arc(0, 0, 9, 0, Math.PI * 2); ctx.stroke();
    ctx.lineWidth = 1.2; ctx.rotate(wheelRot);
    for (let i = 0; i < 6; i++) {
      ctx.rotate(Math.PI / 3);
      ctx.beginPath(); ctx.moveTo(0, 0); ctx.lineTo(9, 0); ctx.stroke();
    }
    ctx.restore();
    // Caja
    ctx.fillStyle = '#6b4423';
    ctx.fillRect(-54, -31, 48, 19);
    ctx.strokeStyle = '#3e2714'; ctx.lineWidth = 1;
    for (let y = -25; y < -12; y += 6) { ctx.beginPath(); ctx.moveTo(-54, y); ctx.lineTo(-6, y); ctx.stroke(); }
    ctx.strokeRect(-54, -31, 48, 19);
    // Carga de paja y sacos
    ctx.fillStyle = '#b08a3e';
    ctx.beginPath(); ctx.ellipse(-42, -35, 11, 7, 0, 0, 7); ctx.fill();
    ctx.beginPath(); ctx.ellipse(-20, -35, 12, 7, 0, 0, 7); ctx.fill();
    ctx.fillStyle = '#8c6a2c';
    ctx.beginPath(); ctx.ellipse(-31, -40, 13, 6, 0, 0, 7); ctx.fill();
    ctx.strokeStyle = '#5a3e17'; ctx.lineWidth = .8;
    ctx.beginPath(); ctx.moveTo(-50, -38); ctx.lineTo(-12, -33); ctx.moveTo(-48, -32); ctx.lineTo(-10, -40); ctx.stroke();
    // Varas hacia la burra
    ctx.strokeStyle = '#4a2f17'; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(-6, -17); ctx.lineTo(14, -19); ctx.stroke();
    // Farol
    ctx.strokeStyle = '#2b1a0c'; ctx.lineWidth = 1;
    ctx.beginPath(); ctx.moveTo(-6, -31); ctx.lineTo(-2, -38); ctx.lineTo(1, -38); ctx.lineTo(1, -35); ctx.stroke();
    const flicker = .75 + Math.sin(t / 90) * .1 + Math.sin(t / 37) * .05;
    const g = ctx.createRadialGradient(1, -31, 0, 1, -31, 22 * lantern);
    g.addColorStop(0, `rgba(255,190,90,${.55 * flicker})`); g.addColorStop(1, 'rgba(255,190,90,0)');
    ctx.fillStyle = g; ctx.beginPath(); ctx.arc(1, -31, 22 * lantern, 0, 7); ctx.fill();
    ctx.fillStyle = '#ffcf6e'; ctx.fillRect(-1.5, -34, 5, 6);
  }

  function drawDonkey(ctx, step, sad) {
    const leg = Math.sin(step) * 4;
    ctx.strokeStyle = '#4e4339'; ctx.lineWidth = 2.6; ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(16, -17); ctx.lineTo(16 + leg, 0);
    ctx.moveTo(20, -17); ctx.lineTo(20 - leg, 0);
    ctx.moveTo(33, -17); ctx.lineTo(33 - leg, 0);
    ctx.moveTo(37, -17); ctx.lineTo(37 + leg, 0);
    ctx.stroke();
    // Cola
    ctx.lineWidth = 1.4;
    ctx.beginPath(); ctx.moveTo(12, -24); ctx.quadraticCurveTo(7, -20, 8 + Math.sin(step * .7) * 2, -13); ctx.stroke();
    // Cuerpo
    ctx.fillStyle = '#7d6e60';
    ctx.beginPath(); ctx.ellipse(26, -22, 15, 8, 0, 0, 7); ctx.fill();
    ctx.fillStyle = '#9a8b7c';
    ctx.beginPath(); ctx.ellipse(26, -18, 11, 3.5, 0, 0, 7); ctx.fill();
    // Arnés
    ctx.strokeStyle = '#3e2714'; ctx.lineWidth = 1.4;
    ctx.beginPath(); ctx.moveTo(14, -19); ctx.lineTo(36, -24); ctx.stroke();
    // Cuello y cabeza
    ctx.fillStyle = '#7d6e60';
    ctx.beginPath(); ctx.moveTo(35, -27); ctx.lineTo(44, -37); ctx.lineTo(48, -33); ctx.lineTo(40, -20); ctx.closePath(); ctx.fill();
    ctx.save(); ctx.translate(49, -34); ctx.rotate(sad ? .5 : .25);
    ctx.beginPath(); ctx.ellipse(0, 0, 8, 4.5, 0, 0, 7); ctx.fill();
    ctx.fillStyle = '#b5a797'; ctx.beginPath(); ctx.ellipse(6, .5, 3, 3, 0, 0, 7); ctx.fill();
    ctx.fillStyle = '#111'; ctx.beginPath(); ctx.arc(-1, -1.5, 1, 0, 7); ctx.fill();
    ctx.restore();
    // Orejas
    ctx.fillStyle = '#6a5c4f';
    const ear = sad ? .9 : 0;
    ctx.save(); ctx.translate(45, -37); ctx.rotate(-.35 + ear);
    ctx.beginPath(); ctx.ellipse(0, -6, 1.8, 6, 0, 0, 7); ctx.fill(); ctx.restore();
    ctx.save(); ctx.translate(47, -37); ctx.rotate(.05 + ear);
    ctx.beginPath(); ctx.ellipse(0, -6, 1.8, 6, 0, 0, 7); ctx.fill(); ctx.restore();
  }

  function play(canvas, opts) {
    const driver = opts.driver || '🙂';
    const passengers = (opts.passengers || []).slice(0, 7);
    const success = !!opts.success;

    const cssW = canvas.clientWidth || canvas.parentElement.clientWidth || 360;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const k = (cssW * dpr) / W;
    canvas.width = Math.round(W * k); canvas.height = Math.round(H * k);
    const ctx = canvas.getContext('2d');
    const bg = paintBackground(k);
    const plankRand = rng(11);
    const planks = [];
    for (let x = LEFT + 3; x < RIGHT - 2; x += 6.5) {
      if (plankRand() < .07) continue; // tablones que faltan
      planks.push({ x, w: 4.5 + plankRand(), tilt: (plankRand() - .5) * .15, shade: 80 + plankRand() * 40 });
    }

    let start = null, last = null, finished = false, skipped = false, resolveDone;
    let suspenseFired = false, impactFired = false;
    const done = new Promise((r) => { resolveDone = r; });
    const particles = [];
    let splashText = 0;

    // Estado de caída (se inicializa al romperse el puente)
    let fall = null;

    function addConfetti() {
      for (let i = 0; i < 70; i++) {
        particles.push({
          kind: 'confeti', x: 250 + Math.random() * 140, y: -10 - Math.random() * 60,
          vx: (Math.random() - .5) * 30, vy: 40 + Math.random() * 60, rot: Math.random() * 6, vr: (Math.random() - .5) * 10,
          color: ['#f2a93b', '#ffd27a', '#8fd18f', '#e8dcff', '#ff8a73'][i % 5], life: 3,
        });
      }
    }

    function addSplash(x, big) {
      const n = big ? 34 : 12;
      for (let i = 0; i < n; i++) {
        const a = -Math.PI / 2 + (Math.random() - .5) * 1.6;
        const v = (big ? 90 : 60) + Math.random() * 80;
        particles.push({ kind: 'gota', x, y: RIVER_Y, vx: Math.cos(a) * v, vy: Math.sin(a) * v, life: 1.4, r: 1 + Math.random() * 1.8 });
      }
    }

    function startFall(x, y) {
      fall = {
        x, y, vy: -40, rot: 0, vr: .9, sunk: false,
        riders: [
          { ch: driver, x: x + 26 * GS, y: y - 36 * GS, vx: 70, vy: -170, rot: 0, vr: 6, sunk: false, size: 15 * GS },
          ...passengers.map((p, i) => {
            const s = seatOf(i, passengers.length);
            return { ch: p, x: x + s.x * GS, y: y + s.y * GS, vx: (Math.random() - .5) * 120, vy: -120 - Math.random() * 90, rot: 0, vr: (Math.random() - .5) * 12, sunk: false, size: 13 * GS };
          }),
        ],
      };
    }

    function drawRiver(t) {
      const g = ctx.createLinearGradient(0, RIVER_Y, 0, H);
      g.addColorStop(0, '#13303a'); g.addColorStop(1, '#061015');
      ctx.fillStyle = g; ctx.fillRect(LEFT - 6, RIVER_Y, RIGHT - LEFT + 12, H - RIVER_Y);
      ctx.strokeStyle = 'rgba(150,200,210,.18)'; ctx.lineWidth = 1;
      for (let row = 0; row < 3; row++) {
        ctx.beginPath();
        for (let x = LEFT - 6; x <= RIGHT + 6; x += 4) {
          const y = RIVER_Y + 6 + row * 10 + Math.sin(x / 12 + t / 400 + row) * 1.6;
          x === LEFT - 6 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
        }
        ctx.stroke();
      }
    }

    function drawFog(t) {
      for (let i = 0; i < 4; i++) {
        const x = ((t / (60 + i * 25)) + i * 120) % (W + 200) - 100;
        const y = 175 + i * 14;
        const g = ctx.createRadialGradient(x, y, 0, x, y, 90);
        g.addColorStop(0, 'rgba(160,175,190,.10)'); g.addColorStop(1, 'rgba(160,175,190,0)');
        ctx.fillStyle = g; ctx.fillRect(x - 90, y - 40, 180, 80);
      }
    }

    // Dibuja el puente. breakT (0..1) abre el puente por el centro; cada mitad cuelga de su anclaje.
    function drawBridge(t, breakT, wobble) {
      const swing = breakT >= 1 && fall ? Math.sin(t / 260) * .06 * Math.exp(-(t - fall.t0) / 1800) : 0;
      const ang = breakT * 1.25 + swing;
      const halfOf = (x) => (x < MID ? { ax: LEFT, dir: 1 } : { ax: RIGHT, dir: -1 });
      const tf = (x, y) => {
        if (breakT <= 0) return { x, y: y + wobble * Math.sin((x - LEFT) / (RIGHT - LEFT) * Math.PI) };
        const { ax, dir } = halfOf(x);
        const a = ang * dir, dx = x - ax, dy = y - CLIFF_Y;
        return { x: ax + dx * Math.cos(a) - dy * Math.sin(a), y: CLIFF_Y + dx * Math.sin(a) + dy * Math.cos(a), a };
      };
      // Postes
      ctx.fillStyle = '#3e2714';
      ctx.fillRect(LEFT - 3, CLIFF_Y - 24, 4, 26); ctx.fillRect(RIGHT - 1, CLIFF_Y - 24, 4, 26);
      // Cuerdas (pasamanos) y tirantes
      ctx.strokeStyle = '#8a6a43'; ctx.lineWidth = 1.2;
      for (const side of [LEFT, RIGHT]) {
        const xs = [];
        for (let x = side; side === LEFT ? x <= MID : x >= MID; x += side === LEFT ? 8 : -8) xs.push(x);
        ctx.beginPath();
        xs.forEach((x, i) => {
          const p = tf(x, bridgeY(x) - 20 + (x === side ? -2 : 0));
          i ? ctx.lineTo(p.x, p.y) : ctx.moveTo(side === LEFT ? LEFT - 1 : RIGHT + 1, CLIFF_Y - 22);
        });
        ctx.stroke();
        ctx.lineWidth = .6;
        xs.forEach((x) => {
          const a = tf(x, bridgeY(x) - 20), b = tf(x, bridgeY(x));
          ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y); ctx.stroke();
        });
        ctx.lineWidth = 1.2;
      }
      // Tablones
      planks.forEach((p) => {
        const q = tf(p.x, bridgeY(p.x));
        ctx.save(); ctx.translate(q.x, q.y);
        ctx.rotate(Math.atan(bridgeSlope(p.x)) + p.tilt + (q.a || 0));
        ctx.fillStyle = `rgb(${p.shade + 30},${p.shade},${p.shade * .6})`;
        ctx.fillRect(-p.w / 2, -1, p.w, 3.2);
        ctx.restore();
      });
    }

    function drawGroup(t, x, y, ang, opt) {
      ctx.save();
      ctx.translate(x, y); ctx.rotate(ang); ctx.scale(GS, GS);
      drawCart(ctx, t, x / 9, opt.lantern);
      drawDonkey(ctx, opt.step, opt.sad);
      if (opt.riders) {
        passengers.forEach((p, i) => {
          const s = seatOf(i, passengers.length);
          const hop = opt.party ? -Math.abs(Math.sin(t / 140 + i)) * 7 : 0;
          drawEmoji(ctx, p, s.x, s.y + hop, 13);
        });
        drawEmoji(ctx, driver, 26, -36 + (opt.party ? -Math.abs(Math.sin(t / 120)) * 6 : 0), 15);
      }
      ctx.restore();
    }

    function frame(now) {
      if (finished) return;
      if (start === null) { start = 0; last = now; }
      // Reloj virtual: "Saltar" acelera la escena x5 en vez de cortarla.
      const realDt = Math.min(50, now - last); last = now;
      start += realDt * (skipped ? 5 : 1);
      const t = start;
      const dt = (realDt * (skipped ? 5 : 1)) / 1000;

      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.drawImage(bg, 0, 0);
      ctx.setTransform(k, 0, 0, k, 0, 0);
      drawRiver(t);

      const tSusp = T_WALK, tAfter = T_WALK + T_SUSPENSE;
      let phase;
      if (t < tSusp) phase = 'walking';
      else if (t < tAfter) phase = 'suspense';
      else if (success) phase = t < tAfter + T_CROSS ? 'crossing' : 'crossed';
      else phase = t < tAfter + 400 ? 'breaking' : (t < tAfter + T_FALL ? 'falling' : 'fell');

      if (phase !== 'walking' && !suspenseFired) { suspenseFired = true; opts.onSuspense && opts.onSuspense(); }

      const wobble = phase === 'suspense' ? Math.sin(t / 30) * 1.2 * clamp01((t - tSusp) / 600) : (phase === 'walking' ? 1.5 * clamp01((t - 1800) / 1000) : 0);
      const breakT = success ? 0 : clamp01((t - tAfter) / 400);
      drawBridge(t, breakT, wobble);

      if (phase === 'walking' || phase === 'suspense' || phase === 'crossing' || phase === 'crossed') {
        let x;
        if (phase === 'walking') x = lerp(28, MID, easeInOut(t / T_WALK));
        else if (phase === 'suspense') x = MID;
        else x = lerp(MID, 330, easeInOut(clamp01((t - tAfter) / T_CROSS)));
        const moving = phase === 'walking' || phase === 'crossing';
        const y = bridgeY(x) + (phase === 'suspense' ? wobble * .8 : 0);
        drawGroup(t, x, y, Math.atan(bridgeSlope(x)) * .6, {
          step: moving ? t / 110 : 0, riders: true, party: phase === 'crossed', lantern: 1, sad: false,
        });
        if (phase === 'crossed' && !impactFired) { impactFired = true; addConfetti(); opts.onImpact && opts.onImpact(true); }
      } else {
        if (!fall) { startFall(MID, bridgeY(MID)); fall.t0 = t; }
        if (phase !== 'breaking') {
          // Gravedad: grupo y jinetes
          if (!fall.sunk) {
            fall.vy += 520 * dt; fall.y += fall.vy * dt; fall.rot += fall.vr * dt;
            if (fall.y >= RIVER_Y + 6) {
              fall.sunk = true; fall.sinkT = t; addSplash(fall.x, true); splashText = t;
              if (!impactFired) { impactFired = true; opts.onImpact && opts.onImpact(false); }
            }
          }
          fall.riders.forEach((r) => {
            if (r.sunk) return;
            r.vy += 520 * dt; r.x += r.vx * dt; r.y += r.vy * dt; r.rot += r.vr * dt;
            r.x = Math.max(LEFT + 8, Math.min(RIGHT - 8, r.x));
            if (r.y >= RIVER_Y + 2) { r.sunk = true; r.sinkT = t; r.y = RIVER_Y + 2; addSplash(r.x, false); }
          });
        }
        // Grupo cayendo / hundiéndose
        let gy = fall.y, rot = fall.rot;
        if (fall.sunk) {
          const s = clamp01((t - fall.sinkT) / 1500);
          gy = RIVER_Y + 6 + s * 16 + Math.sin(t / 300) * 1.5;
          rot = fall.rot + Math.sin(t / 400) * .05;
        }
        ctx.save();
        if (fall.sunk) { ctx.beginPath(); ctx.rect(0, 0, W, RIVER_Y + 4); ctx.clip(); }
        drawGroup(t, fall.x, gy, rot, { step: t / 60, riders: false, lantern: fall.sunk ? .3 : 1, sad: true });
        ctx.restore();
        fall.riders.forEach((r, i) => {
          const y = r.sunk ? RIVER_Y + 1 + Math.sin(t / 250 + i) * 1.5 : r.y;
          drawEmoji(ctx, r.ch, r.x, y, r.size, r.sunk ? Math.sin(t / 300 + i) * .3 : r.rot);
          if (r.sunk && t - r.sinkT > 700) drawEmoji(ctx, '💦', r.x + 9, y - 10, 9);
        });
      }

      // Partículas
      for (let i = particles.length - 1; i >= 0; i--) {
        const p = particles[i];
        p.life -= dt;
        if (p.life <= 0) { particles.splice(i, 1); continue; }
        if (p.kind === 'gota') {
          p.vy += 300 * dt; p.x += p.vx * dt; p.y += p.vy * dt;
          ctx.fillStyle = `rgba(170,215,230,${Math.min(1, p.life)})`;
          ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, 7); ctx.fill();
        } else {
          p.x += p.vx * dt; p.y += p.vy * dt; p.rot += p.vr * dt;
          ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(p.rot);
          ctx.fillStyle = p.color; ctx.fillRect(-2, -1, 4, 2.5); ctx.restore();
        }
      }

      drawFog(t);

      if (splashText && t - splashText < 1600) {
        const s = clamp01((t - splashText) / 250);
        ctx.save(); ctx.translate(MID, 196); ctx.scale(s, s);
        ctx.font = 'bold 26px Georgia, serif'; ctx.textAlign = 'center';
        ctx.lineWidth = 4; ctx.strokeStyle = '#061015'; ctx.strokeText('¡PLAF!', 0, 0);
        ctx.fillStyle = '#bfe6f0'; ctx.fillText('¡PLAF!', 0, 0);
        ctx.restore();
      }

      const end = success ? tAfter + T_CROSS + T_PARTY : tAfter + T_FALL;
      if (t >= end) {
        finished = true;
        if (!impactFired) { impactFired = true; opts.onImpact && opts.onImpact(success); }
        resolveDone(success ? 'crossed' : 'fell');
        return;
      }
      requestAnimationFrame(frame);
    }

    requestAnimationFrame(frame);
    return { done, skip() { skipped = true; } };
  }

  window.BridgeScene = { play };
})();
