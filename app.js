/* ============================================================
   Hoshi-no-Tani — The Valley of Stars
   A Ghibli-inspired valley generated entirely in code:
   no textures, no models, no recordings.
   Inspired by lentils801's CodePen of the same name.
   ============================================================ */
(function () {
  'use strict';

  /* ------------------------------------------------------------
     Seeded value noise (deterministic — same scene every load)
     ------------------------------------------------------------ */
  function mulberry32(seed) {
    let a = seed >>> 0;
    return function () {
      a |= 0; a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  var rand = mulberry32(0x51A7);
  var perm = new Uint8Array(512);
  (function () {
    var p = new Uint8Array(256);
    for (var i = 0; i < 256; i++) p[i] = i;
    for (var i = 255; i > 0; i--) {
      var j = (rand() * (i + 1)) | 0;
      var t = p[i]; p[i] = p[j]; p[j] = t;
    }
    for (var k = 0; k < 512; k++) perm[k] = p[k & 255];
  })();

  function fade(t) { return t * t * (3 - 2 * t); }
  function grad(h, x, y) {
    var u = h < 4 ? x : y;
    var v = h < 4 ? y : x;
    return ((h & 1) ? -u : u) + ((h & 2) ? -v : v);
  }
  function noise2(x, y) {
    var X = Math.floor(x) & 255, Y = Math.floor(y) & 255;
    x -= Math.floor(x); y -= Math.floor(y);
    var u = fade(x), v = fade(y);
    var A = perm[X] + Y, B = perm[X + 1] + Y;
    return 0.5 + 0.5 * (
      (1 - u) * (1 - v) * grad(perm[A], x, y) +
      u * (1 - v) * grad(perm[B], x - 1, y) +
      (1 - u) * v * grad(perm[A + 1], x, y - 1) +
      u * v * grad(perm[B + 1], x - 1, y - 1)
    ) / 1.414;
  }
  function fbm(x, y, oct) {
    var v = 0, a = 1, f = 1, n = 0;
    for (var i = 0; i < oct; i++) { v += a * noise2(x * f, y * f); n += a; a *= 0.5; f *= 2.1; }
    return v / n;
  }
  function smoothstep(a, b, x) {
    var t = Math.min(1, Math.max(0, (x - a) / (b - a)));
    return t * t * (3 - 2 * t);
  }
  function clamp(v, a, b) { return Math.min(b, Math.max(a, v)); }
  function lerp(a, b, t) { return a + (b - a) * t; }

  /* ------------------------------------------------------------
     Terrain height field — the whole valley from one function
     ------------------------------------------------------------ */
  function terrainHeight(x, z) {
    var r = Math.hypot(x, z);
    var h = fbm(x * 0.008, z * 0.008, 5) * 58 + 4;
    h += (fbm(x * 0.031 + 41.7, z * 0.031 - 19.3, 3) - 0.5) * 10;
    // valley bowl: flatten toward the centre
    h = lerp(h * 0.1, h, smoothstep(40, 200, r));
    // mountain ring around the horizon
    h += smoothstep(210, 340, r) * (95 + fbm(x * 0.0055 + 9, z * 0.0055 - 7, 4) * 45);
    // drop off beyond the ring so the ridge hides the world edge
    h -= smoothstep(340, 380, r) * 320;
    // pond depression
    var pd = Math.hypot(x - 48, z + 42);
    h -= (1 - smoothstep(4, 26, pd)) * 6.5;
    return h;
  }

  /* ------------------------------------------------------------
     Scene globals
     ------------------------------------------------------------ */
  var PRESETS = {
    Low:   { grass: 5000,  flowers: 260, trees: 28,  rocks: 10, clouds: 6,  wind: 0,   scale: 0.75, shadows: false },
    Med:   { grass: 9500,  flowers: 500, trees: 55,  rocks: 18, clouds: 8,  wind: 80,  scale: 1.0,  shadows: false },
    High:  { grass: 16000, flowers: 800, trees: 85,  rocks: 26, clouds: 10, wind: 150, scale: 1.15, shadows: true },
    Ultra: { grass: 26000, flowers: 1100, trees: 115, rocks: 34, clouds: 12, wind: 200, scale: 1.3,  shadows: true }
  };
  var PRESET_NAMES = ['Low', 'Med', 'High', 'Ultra'];
  var TRACK_R = 118;
  var EYE = 2.3;

  var renderer, scene, camera, clock;
  var skyMat, sunLight, hemiLight, sunSprite, sunCore;
  var terrain, water;
  var bladeGeo, grassMat, grassMesh;
  var flowerHeadGeo, flowerStemGeo, flowerHeadMat, flowerStemMat, flowerMesh, stemMesh;
  var trunkGeo, foliageGeo;
  var cloudGroups = [];
  var rockMeshes = [];
  var windLines = null;
  var train = null, smokePool = [];
  var cache = {};

  var state = {
    quality: 'High',
    density: 1,
    flying: false,
    cine: false,
    cineT: 0,
    paused: false,
    sunA: -0.9,
    trainOn: false
  };
  var windParams = { mean: 4.2, gustiness: 1.0 };
  var keys = {};
  var yaw = 0, pitch = -0.06;
  var pos = { x: 0, y: 0, z: 96 };
  var dragging = false;
  var panelOpen = false;
  var ignoreMoveOnce = false;

  var windDirTmp = new THREE.Vector3();
  var sunDirTmp = new THREE.Vector3();

  /* ------------------------------------------------------------
     Audio — wind + generative music + whistle, all by code
     ------------------------------------------------------------ */
  var AudioSys = (function () {
    var ctx = null, master = null, musicTimer = null, chordIdx = 0, musicOn = true;
    var CHORDS = [
      [130.81, 196.0, 329.63],   // C
      [110.0, 164.81, 261.63],   // Am
      [87.31, 130.81, 220.0],    // F
      [98.0, 146.83, 246.94]     // G
    ];
    function ensure() {
      if (ctx) return;
      var AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return;
      ctx = new AC();
      master = ctx.createGain();
      master.gain.value = 0.7;
      master.connect(ctx.destination);
      // wind: looped filtered noise with slow gain LFO
      var len = ctx.sampleRate * 2;
      var buf = ctx.createBuffer(1, len, ctx.sampleRate);
      var d = buf.getChannelData(0);
      var last = 0;
      for (var i = 0; i < len; i++) { var w = Math.random() * 2 - 1; last = (last + 0.02 * w) / 1.02; d[i] = last * 3.5; }
      var src = ctx.createBufferSource(); src.buffer = buf; src.loop = true;
      var lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = 320; lp.Q.value = 0.4;
      var windGain = ctx.createGain(); windGain.gain.value = 0.14;
      var lfo = ctx.createOscillator(); lfo.frequency.value = 0.09;
      var lfoGain = ctx.createGain(); lfoGain.gain.value = 0.06;
      lfo.connect(lfoGain); lfoGain.connect(windGain.gain);
      src.connect(lp); lp.connect(windGain); windGain.connect(master);
      src.start(); lfo.start();
      schedule();
      musicTimer = setInterval(schedule, 8000);
    }
    function playChord(when, i) {
      if (!ctx) return;
      var ch = CHORDS[i % CHORDS.length];
      var g = ctx.createGain(); g.gain.value = 0;
      var f = ctx.createBiquadFilter(); f.type = 'lowpass'; f.frequency.value = 820; f.Q.value = 0.6;
      g.connect(f); f.connect(master);
      g.gain.setValueAtTime(0, when);
      g.gain.linearRampToValueAtTime(0.08, when + 2.4);
      g.gain.setValueAtTime(0.08, when + 5.4);
      g.gain.linearRampToValueAtTime(0.0001, when + 7.8);
      var det = [0, 3, -3, 5];
      for (var k = 0; k < ch.length; k++) {
        var o = ctx.createOscillator(); o.type = 'triangle';
        o.frequency.value = ch[k];
        o.detune.value = det[k % 4] + (Math.random() * 4 - 2);
        o.connect(g); o.start(when); o.stop(when + 8);
      }
      var b = ctx.createOscillator(); b.type = 'sine'; b.frequency.value = ch[0] / 2;
      var bg = ctx.createGain(); bg.gain.value = 0;
      bg.gain.setValueAtTime(0, when);
      bg.gain.linearRampToValueAtTime(0.11, when + 1);
      bg.gain.setValueAtTime(0.11, when + 5.5);
      bg.gain.linearRampToValueAtTime(0.0001, when + 7.8);
      b.connect(bg); bg.connect(master); b.start(when); b.stop(when + 8);
    }
    function schedule() {
      if (!musicOn || !ctx) return;
      playChord(ctx.currentTime + 0.15, chordIdx);
      chordIdx++;
    }
    return {
      start: function () {
        try {
          ensure();
          if (ctx && ctx.state === 'suspended') ctx.resume();
        } catch (e) { /* audio is optional */ }
      },
      setVolume: function (v) { if (master) master.gain.value = v; },
      setMusic: function (on) {
        musicOn = on;
        if (musicTimer) { clearInterval(musicTimer); musicTimer = null; }
        if (on) { schedule(); musicTimer = setInterval(schedule, 8000); }
      },
      whistle: function () {
        if (!ctx || !master) return;
        var t = ctx.currentTime;
        var o = ctx.createOscillator(); o.type = 'square';
        o.frequency.setValueAtTime(720, t);
        o.frequency.exponentialRampToValueAtTime(420, t + 0.9);
        var f = ctx.createBiquadFilter(); f.type = 'bandpass'; f.frequency.value = 900; f.Q.value = 2;
        var g = ctx.createGain();
        g.gain.setValueAtTime(0.0001, t);
        g.gain.exponentialRampToValueAtTime(0.07, t + 0.06);
        g.gain.exponentialRampToValueAtTime(0.0001, t + 1.1);
        o.connect(f); f.connect(g); g.connect(master);
        o.start(t); o.stop(t + 1.2);
      }
    };
  })();

  /* ------------------------------------------------------------
     Init
     ------------------------------------------------------------ */
  function init() {
    var app = document.getElementById('app');
    renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1.0;
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    applyPixelRatio();
    app.appendChild(renderer.domElement);
    var cv = renderer.domElement;
    cv.style.cursor = 'grab';

    scene = new THREE.Scene();
    scene.fog = new THREE.Fog(0xdce3d4, 140, 640);
    camera = new THREE.PerspectiveCamera(60, window.innerWidth / window.innerHeight, 0.1, 1500);
    camera.rotation.order = 'YXZ';
    clock = new THREE.Clock();

    buildSky();
    buildLights();
    buildSun();
    buildTerrain();
    buildWater();
    buildScatterCache();
    buildAll(state.quality);
    buildSmokePool();

    pos.y = terrainHeight(pos.x, pos.z) + EYE;

    bindInput();
    bindUI();
    window.addEventListener('resize', onResize);
    window.__hnt = { scene: scene, renderer: renderer, state: state, getTrain: function () { return train; },
      getCam: function () { return camera; }, hAt: terrainHeight, getPos: function () { return pos; } };
    requestAnimationFrame(animate);
  }

  /* ---------------- sky dome ---------------- */
  function buildSky() {
    var geo = new THREE.SphereGeometry(1000, 32, 18);
    skyMat = new THREE.ShaderMaterial({
      side: THREE.BackSide,
      depthWrite: false,
      fog: false,
      uniforms: {
        uTop: { value: new THREE.Color(0x6fa8c8) },
        uMid: { value: new THREE.Color(0xcfe6e0) },
        uBot: { value: new THREE.Color(0xf6e7c0) },
        uSun: { value: new THREE.Vector3(0.6, 0.5, 0.5) },
        uSunCol: { value: new THREE.Color(0xffe2a0) }
      },
      vertexShader: [
        'varying vec3 vW;',
        'void main(){',
        '  vW = (modelMatrix * vec4(position, 1.0)).xyz;',
        '  gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);',
        '}'
      ].join('\n'),
      fragmentShader: [
        'uniform vec3 uTop; uniform vec3 uMid; uniform vec3 uBot;',
        'uniform vec3 uSun; uniform vec3 uSunCol;',
        'varying vec3 vW;',
        'void main(){',
        '  vec3 d = normalize(vW);',
        '  float h = d.y * 0.5 + 0.5;',
        '  vec3 col = mix(uBot, uMid, smoothstep(0.02, 0.5, h));',
        '  col = mix(col, uTop, smoothstep(0.42, 0.95, h));',
        '  float s = max(dot(d, normalize(uSun)), 0.0);',
        '  col += uSunCol * pow(s, 24.0) * 0.55;',
        '  col += uSunCol * pow(s, 3.0) * 0.12;',
        '  gl_FragColor = vec4(col, 1.0);',
        '}'
      ].join('\n')
    });
    var sky = new THREE.Mesh(geo, skyMat);
    sky.renderOrder = -1;
    scene.add(sky);
  }

  /* ---------------- lights ---------------- */
  function buildLights() {
    sunLight = new THREE.DirectionalLight(0xffe2b0, 1.6);
    sunLight.castShadow = PRESETS[state.quality].shadows;
    sunLight.shadow.mapSize.set(2048, 2048);
    sunLight.shadow.camera.left = -300;
    sunLight.shadow.camera.right = 300;
    sunLight.shadow.camera.top = 300;
    sunLight.shadow.camera.bottom = -300;
    sunLight.shadow.camera.near = 50;
    sunLight.shadow.camera.far = 1100;
    sunLight.shadow.bias = -0.0006;
    scene.add(sunLight);
    scene.add(sunLight.target);
    hemiLight = new THREE.HemisphereLight(0xbfd8ee, 0x8d9b62, 0.85);
    scene.add(hemiLight);
  }

  /* ---------------- sun sprites (code-drawn glow) ---------------- */
  function glowCanvas(inner, mid) {
    var c = document.createElement('canvas');
    c.width = c.height = 256;
    var g = c.getContext('2d');
    var gr = g.createRadialGradient(128, 128, 0, 128, 128, 128);
    gr.addColorStop(0, inner);
    gr.addColorStop(0.3, mid);
    gr.addColorStop(1, 'rgba(255,220,150,0)');
    g.fillStyle = gr;
    g.fillRect(0, 0, 256, 256);
    return c;
  }
  function buildSun() {
    var tex = new THREE.CanvasTexture(glowCanvas('rgba(255,242,205,1)', 'rgba(255,214,140,0.5)'));
    tex.colorSpace = THREE.SRGBColorSpace;
    var mat = new THREE.SpriteMaterial({
      map: tex, transparent: true, blending: THREE.AdditiveBlending,
      depthWrite: false, fog: false
    });
    sunSprite = new THREE.Sprite(mat);
    sunSprite.scale.set(360, 360, 1);
    scene.add(sunSprite);
    var tex2 = new THREE.CanvasTexture(glowCanvas('rgba(255,250,235,1)', 'rgba(255,236,190,0.6)'));
    tex2.colorSpace = THREE.SRGBColorSpace;
    var mat2 = new THREE.SpriteMaterial({
      map: tex2, transparent: true, blending: THREE.AdditiveBlending,
      depthWrite: false, fog: false
    });
    sunCore = new THREE.Sprite(mat2);
    sunCore.scale.set(130, 130, 1);
    scene.add(sunCore);
  }

  /* ---------------- terrain ---------------- */
  function buildTerrain() {
    var geo = new THREE.PlaneGeometry(760, 760, 220, 220);
    geo.rotateX(-Math.PI / 2);
    var posA = geo.attributes.position;
    for (var i = 0; i < posA.count; i++) {
      posA.setY(i, terrainHeight(posA.getX(i), posA.getZ(i)));
    }
    geo.computeVertexNormals();
    // vertex colours: meadow → dry → rock, sandy near the pond, rocky on slopes
    var nor = geo.attributes.normal;
    var cols = new Float32Array(posA.count * 3);
    var base = new THREE.Color(0x87a963), dry = new THREE.Color(0xc4b177);
    var rock = new THREE.Color(0x8d8f84), sand = new THREE.Color(0xd9c58f);
    var c = new THREE.Color();
    for (var j = 0; j < posA.count; j++) {
      var x = posA.getX(j), z = posA.getZ(j), h = posA.getY(j);
      var ny = nor.getY(j);
      var slope = Math.max(0, 1 - ny);
      var pd = Math.hypot(x - 48, z + 42);
      var jit = (noise2(x * 0.05 + 3.1, z * 0.05 - 1.7) - 0.5) * 0.12;
      c.copy(base);
      c.lerp(dry, clamp(smoothstep(5, 15, h) + jit, 0, 1));
      c.lerp(rock, clamp(smoothstep(9, 26, h) + smoothstep(0.5, 0.85, slope) + jit * 0.5, 0, 1));
      c.lerp(sand, (1 - smoothstep(16, 30, pd)) * 0.55);
      cols[j * 3] = c.r; cols[j * 3 + 1] = c.g; cols[j * 3 + 2] = c.b;
    }
    geo.setAttribute('color', new THREE.BufferAttribute(cols, 3));
    var mat = new THREE.MeshStandardMaterial({
      vertexColors: true, roughness: 1, metalness: 0, flatShading: true
    });
    terrain = new THREE.Mesh(geo, mat);
    terrain.receiveShadow = PRESETS[state.quality].shadows;
    scene.add(terrain);
  }

  /* ---------------- pond ---------------- */
  function buildWater() {
    var waterY = terrainHeight(48, -42) + 4.5;
    var geo = new THREE.CircleGeometry(20, 48);
    geo.rotateX(-Math.PI / 2);
    var mat = new THREE.MeshStandardMaterial({
      color: 0x5f9c93, roughness: 0.18, metalness: 0.12, transparent: true, opacity: 0.92
    });
    water = new THREE.Mesh(geo, mat);
    water.position.set(48, waterY, -42);
    scene.add(water);
  }

  /* ---------------- grass ---------------- */
  function makeBladeGeo() {
    var seg = 4, pts = seg + 1;
    var pos = [], idx = [], col = [];
    var baseC = new THREE.Color(0x5f8f4e), tipC = new THREE.Color(0xa9c477);
    var c = new THREE.Color();
    for (var i = 0; i < pts; i++) {
      var t = i / seg;
      var h = t;
      var w = 0.055 * (1 - t) + 0.006;
      var lean = t * t * 0.05;
      pos.push(-w + lean, h, 0, w + lean, h, 0);
      c.copy(baseC).lerp(tipC, t);
      col.push(c.r, c.g, c.b, c.r, c.g, c.b);
    }
    for (var k = 0; k < seg; k++) {
      var a = k * 2, b = k * 2 + 1, cc = k * 2 + 2, d = k * 2 + 3;
      idx.push(a, b, d, a, d, cc);
    }
    var g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    g.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
    g.setIndex(idx);
    g.computeVertexNormals();
    return g;
  }
  function buildGrass() {
    if (!bladeGeo) { bladeGeo = makeBladeGeo(); grassMat = buildGrassMat(); }
    if (grassMesh) { scene.remove(grassMesh); grassMesh.dispose(); grassMesh = null; }
    var preset = PRESETS[state.quality];
    var spots = cache[state.quality].grass;
    var n = Math.max(0, Math.round(spots.length * state.density));
    var mesh = new THREE.InstancedMesh(bladeGeo, grassMat, n);
    var m = new THREE.Matrix4(), q = new THREE.Quaternion(), e = new THREE.Euler();
    var s = new THREE.Vector3(), p = new THREE.Vector3(), col = new THREE.Color();
    var pal = ['#6d9e55', '#7fae60', '#8fb868', '#a8b56b', '#c2ae6a', '#93b05e'];
    for (var i = 0; i < n; i++) {
      var sp = spots[i];
      e.set(sp.lean, sp.ry, 0); q.setFromEuler(e);
      s.set(1, sp.s, 1); p.set(sp.x, sp.y + 0.02, sp.z);
      m.compose(p, q, s);
      mesh.setMatrixAt(i, m);
      col.set(pal[sp.ci]); mesh.setColorAt(i, col);
    }
    mesh.instanceMatrix.needsUpdate = true;
    if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
    mesh.frustumCulled = false;
    scene.add(mesh);
    grassMesh = mesh;
  }
  function buildGrassMat() {
    var mat = new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 1, metalness: 0 });
    mat.onBeforeCompile = function (shader) {
      shader.uniforms.uTime = { value: 0 };
      shader.uniforms.uWind = { value: 0 };
      shader.uniforms.uWindDir = { value: new THREE.Vector3(-0.35, 0, 0.9) };
      shader.vertexShader = shader.vertexShader
        .replace('#include <common>',
          '#include <common>\nuniform float uTime;\nuniform float uWind;\nuniform vec3 uWindDir;')
        .replace('#include <begin_vertex>', [
          '#include <begin_vertex>',
          '  {',
          '    float gh = position.y * length(instanceMatrix[1].xyz);',
          '    float ph = instanceMatrix[3].x * 1.73 + instanceMatrix[3].z * 2.31;',
          '    float sw = sin(uTime * 2.1 + ph) * 0.6 + sin(uTime * 3.7 + ph * 1.7) * 0.4;',
          '    transformed += uWindDir * (sw * uWind * gh * 0.38);',
          '    transformed.x += sin(uTime * 1.6 + ph * 0.7) * uWind * gh * 0.1;',
          '  }'
        ].join('\n'));
      mat.userData.shader = shader;
    };
    return mat;
  }

  /* ---------------- flowers ---------------- */
  function buildFlowers() {
    if (flowerMesh) {
      scene.remove(flowerMesh); scene.remove(stemMesh);
      flowerMesh = null; stemMesh = null;
    }
    if (!flowerHeadGeo) {
      flowerHeadGeo = new THREE.IcosahedronGeometry(0.34, 1);
      flowerHeadGeo.scale(1, 0.6, 1);
      flowerStemGeo = new THREE.CylinderGeometry(0.045, 0.06, 0.75, 5);
      flowerHeadMat = new THREE.MeshStandardMaterial({ roughness: 0.8, flatShading: true });
      flowerStemMat = new THREE.MeshBasicMaterial({ color: 0x6f9a52 });
    }
    var preset = PRESETS[state.quality];
    var spots = cache[state.quality].flowers;
    var n = spots.length;
    var heads = new THREE.InstancedMesh(flowerHeadGeo, flowerHeadMat, n);
    var stems = new THREE.InstancedMesh(flowerStemGeo, flowerStemMat, n);
    var m = new THREE.Matrix4(), q = new THREE.Quaternion(), e = new THREE.Euler();
    var s = new THREE.Vector3(), p = new THREE.Vector3(), col = new THREE.Color();
    var pal = ['#e8a0b4', '#f2d06b', '#f5f0e6', '#c9a8e0', '#efb9a0'];
    for (var i = 0; i < n; i++) {
      var sp = spots[i];
      e.set(0, sp.ry, 0); q.setFromEuler(e);
      s.set(1, 1, 1);
      p.set(sp.x, sp.y + 0.37, sp.z); m.compose(p, q, s);
      stems.setMatrixAt(i, m);
      var sc = sp.sc;
      s.set(sc, sc * 0.6, sc);
      p.set(sp.x, sp.y + 0.75, sp.z); m.compose(p, q, s);
      heads.setMatrixAt(i, m);
      col.set(pal[sp.ci]); heads.setColorAt(i, col);
    }
    stems.instanceMatrix.needsUpdate = true;
    heads.instanceMatrix.needsUpdate = true;
    if (heads.instanceColor) heads.instanceColor.needsUpdate = true;
    stems.frustumCulled = false; heads.frustumCulled = false;
    scene.add(stems); scene.add(heads);
    stemMesh = stems; flowerMesh = heads;
  }

  /* ---------------- trees & rocks ---------------- */
  function buildTrees() {
    clearGroup('trees');
    var preset = PRESETS[state.quality];
    if (!trunkGeo) {
      trunkGeo = new THREE.CylinderGeometry(0.32, 0.5, 3.4, 7);
      foliageGeo = new THREE.IcosahedronGeometry(1, 0);
    }
    var trunkMat = new THREE.MeshStandardMaterial({ color: 0x7a5b3f, roughness: 0.95 });
    var pal = ['#6f9e5a', '#7fae68', '#5d8f4e', '#93b872', '#a8c078'];
    var col = new THREE.Color();
    var spots = cache[state.quality].trees;
    for (var i = 0; i < spots.length; i++) {
      var sp = spots[i];
      var g = new THREE.Group();
      var trunk = new THREE.Mesh(trunkGeo, trunkMat);
      trunk.position.y = 1.7;
      g.add(trunk);
      var fm = new THREE.MeshStandardMaterial({
        color: col.set(pal[sp.ci]), roughness: 0.9, flatShading: true
      });
      var b1 = new THREE.Mesh(foliageGeo, fm);
      b1.position.y = 3.6; b1.scale.set(2.2 * sp.s, 1.5 * sp.s, 2.0 * sp.s);
      var b2 = new THREE.Mesh(foliageGeo, fm);
      b2.position.set(0.9 * sp.s, 3.1, 0.5 * sp.s); b2.scale.set(1.3 * sp.s, 1.1 * sp.s, 1.2 * sp.s);
      var b3 = new THREE.Mesh(foliageGeo, fm);
      b3.position.set(-0.8 * sp.s, 3.3, -0.6 * sp.s); b3.scale.set(1.2 * sp.s, 1.0 * sp.s, 1.1 * sp.s);
      g.add(b1, b2, b3);
      g.position.set(sp.x, sp.y, sp.z);
      g.rotation.y = sp.ry;
      g.scale.setScalar(sp.sc);
      var sh = PRESETS[state.quality].shadows;
      g.traverse(function (o) { if (o.isMesh) { o.castShadow = sh; o.receiveShadow = false; } });
      scene.add(g);
      cache[state.quality].treeGroups.push(g);
    }
  }
  function buildRocks() {
    clearGroup('rocks');
    var geo = new THREE.DodecahedronGeometry(1, 0);
    var mat = new THREE.MeshStandardMaterial({ color: 0x9a9b90, roughness: 1, flatShading: true });
    var spots = cache[state.quality].rocks;
    for (var i = 0; i < spots.length; i++) {
      var sp = spots[i];
      var r = new THREE.Mesh(geo, mat);
      r.position.set(sp.x, sp.y + sp.s * 0.35, sp.z);
      r.scale.set(sp.s, sp.s * 0.7, sp.s);
      r.rotation.y = sp.ry;
      r.castShadow = PRESETS[state.quality].shadows;
      scene.add(r);
      cache[state.quality].rockGroups.push(r);
    }
  }

  /* ---------------- clouds ---------------- */
  function buildClouds() {
    clearGroup('clouds');
    var spots = cache[state.quality].clouds;
    var white = new THREE.Color(0xfdfaf2);
    var tint = new THREE.Color();
    for (var i = 0; i < spots.length; i++) {
      var sp = spots[i];
      var g = new THREE.Group();
      for (var k = 0; k < sp.parts.length; k++) {
        var pt = sp.parts[k];
        var col = tint.copy(white).lerp(new THREE.Color(0xe8e6da), pt.g);
        var mat = new THREE.MeshLambertMaterial({ color: col, flatShading: true, transparent: true, opacity: 0.95 });
        var sph = new THREE.Mesh(new THREE.SphereGeometry(pt.r, 12, 10), mat);
        sph.position.set(pt.dx, pt.dy, pt.dz);
        sph.scale.set(1, 0.6, 1);
        g.add(sph);
      }
      g.position.set(sp.x, sp.y, sp.z);
      scene.add(g);
      cache[state.quality].cloudGroups.push(g);
    }
    cloudGroups = cache[state.quality].cloudGroups;
  }

  /* ---------------- wind field lines ---------------- */
  function buildWindLines() {
    if (windLines) { scene.remove(windLines); windLines.geometry.dispose(); windLines.material.dispose(); windLines = null; }
    var count = PRESETS[state.quality].wind;
    var cb = document.getElementById('cWindField');
    if (count <= 0) { if (cb) cb.disabled = true; return; }
    var spots = [];
    for (var i = 0; i < count; i++) {
      var ang = rand() * Math.PI * 2, r = 40 + rand() * 230;
      spots.push({ x: Math.cos(ang) * r, z: Math.sin(ang) * r, ph: rand() * Math.PI * 2, len: 3 + rand() * 3 });
    }
    var geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(new Float32Array(count * 6), 3));
    var mat = new THREE.LineBasicMaterial({ color: 0xf2e8cf, transparent: true, opacity: 0.45 });
    windLines = new THREE.LineSegments(geo, mat);
    windLines.userData.spots = spots;
    scene.add(windLines);
    var cb = document.getElementById('cWindField');
    if (cb) cb.disabled = false;
  }
  function updateWindLines(t) {
    var spots = windLines.userData.spots;
    var arr = windLines.geometry.attributes.position.array;
    var wd = getWindDir(t);
    for (var i = 0; i < spots.length; i++) {
      var b = spots[i];
      var sx = b.x + Math.sin(t * 0.5 + b.ph) * 3;
      var sz = b.z + Math.cos(t * 0.43 + b.ph * 1.3) * 3;
      var sy = terrainHeight(sx, sz) + 3.8 + Math.sin(t * 0.9 + b.ph) * 0.4;
      var fl = 2 + Math.sin(t * 0.7 + b.ph);
      var o = i * 6;
      arr[o] = sx; arr[o + 1] = sy; arr[o + 2] = sz;
      arr[o + 3] = sx + wd.x * b.len * fl; arr[o + 4] = sy + 0.9; arr[o + 5] = sz + wd.z * b.len * fl;
    }
    windLines.geometry.attributes.position.needsUpdate = true;
  }

  /* ---------------- train + smoke ---------------- */
  function buildSmokePool() {
    for (var i = 0; i < 26; i++) {
      var mat = new THREE.MeshBasicMaterial({ color: 0xf7f2e6, transparent: true, opacity: 0, depthWrite: false });
      var mesh = new THREE.Mesh(new THREE.SphereGeometry(0.55, 8, 6), mat);
      mesh.visible = false;
      scene.add(mesh);
      smokePool.push({ mesh: mesh, life: 0, max: 1, vx: 0, vy: 0, vz: 0, base: 0.5 });
    }
  }
  function emitSmoke() {
    var v = new THREE.Vector3(3.7, 6.4, 0);
    train.updateMatrixWorld(true);
    train.localToWorld(v);
    for (var i = 0; i < smokePool.length; i++) {
      var p = smokePool[i];
      if (p.life > 0) continue;
      p.mesh.visible = true;
      p.mesh.position.set(v.x + (rand() - 0.5) * 0.6, v.y + (rand() - 0.5) * 0.4, v.z + (rand() - 0.5) * 0.6);
      p.vx = (rand() - 0.5) * 0.8;
      p.vy = 1.6 + rand() * 0.8;
      p.vz = (rand() - 0.5) * 0.8;
      p.max = 2.6 + rand() * 1.6;
      p.life = p.max;
      p.base = 0.5 + rand() * 0.3;
      return;
    }
  }
  function updateSmoke(dt, t) {
    var wd = getWindDir(t);
    for (var i = 0; i < smokePool.length; i++) {
      var p = smokePool[i];
      if (p.life <= 0) { p.mesh.visible = false; continue; }
      p.life -= dt;
      p.vy += 1.5 * dt;
      p.vx += wd.x * windMag * 0.3 * dt;
      p.vz += wd.z * windMag * 0.3 * dt;
      p.mesh.position.x += p.vx * dt;
      p.mesh.position.y += p.vy * dt;
      p.mesh.position.z += p.vz * dt;
      var k = 1 - p.life / p.max;
      var sc = p.base + k * 1.7;
      p.mesh.scale.setScalar(sc);
      p.mesh.material.opacity = k * 0.5;
    }
  }
  function buildTrain() {
    var g = new THREE.Group();
    var wheels = [];
    var red = new THREE.MeshStandardMaterial({ color: 0xa8432f, roughness: 0.65 });
    var dark = new THREE.MeshStandardMaterial({ color: 0x39404e, roughness: 0.8 });
    var green = new THREE.MeshStandardMaterial({ color: 0x48594a, roughness: 0.75 });
    var gold = new THREE.MeshStandardMaterial({ color: 0xd8a24a, roughness: 0.45, metalness: 0.3 });
    var wheelGeo = new THREE.CylinderGeometry(0.78, 0.78, 0.26, 16);
    wheelGeo.rotateZ(Math.PI / 2);
    function axle(x) {
      [1.35, -1.35].forEach(function (zs) {
        var w = new THREE.Mesh(wheelGeo, dark);
        w.position.set(x, 0.78, zs);
        g.add(w);
        wheels.push(w);
      });
    }
    // boiler
    var boilerGeo = new THREE.CylinderGeometry(1.7, 1.7, 6.8, 18);
    boilerGeo.rotateZ(Math.PI / 2);
    var boiler = new THREE.Mesh(boilerGeo, green);
    boiler.position.set(0.4, 3.0, 0);
    g.add(boiler);
    // cab
    var cab = new THREE.Mesh(new THREE.BoxGeometry(3.4, 3.4, 3.2), red);
    cab.position.set(-3.6, 3.4, 0);
    g.add(cab);
    var roof = new THREE.Mesh(new THREE.BoxGeometry(3.8, 0.3, 3.6), dark);
    roof.position.set(-3.6, 5.3, 0);
    g.add(roof);
    // chimney + dome + headlight
    var chimney = new THREE.Mesh(new THREE.CylinderGeometry(0.62, 0.45, 1.7, 12), dark);
    chimney.position.set(3.6, 5.5, 0);
    g.add(chimney);
    var dome = new THREE.Mesh(new THREE.CylinderGeometry(0.5, 0.5, 0.7, 10), gold);
    dome.position.set(1.2, 4.9, 0);
    g.add(dome);
    var light = new THREE.Mesh(new THREE.SphereGeometry(0.22, 10, 8), gold);
    light.position.set(4.4, 3.0, 0);
    g.add(light);
    // wheels
    [-3.6, -0.8, 2.2].forEach(axle);
    // cargo car
    var car = new THREE.Mesh(new THREE.BoxGeometry(3.8, 2.0, 3.0), green);
    car.position.set(-7.8, 2.1, 0);
    g.add(car);
    var carRoof = new THREE.Mesh(new THREE.BoxGeometry(3.2, 0.5, 2.6), red);
    carRoof.position.set(-7.8, 3.3, 0);
    g.add(carRoof);
    [-7.8].forEach(axle);
    var sh = PRESETS[state.quality].shadows;
    g.traverse(function (o) { if (o.isMesh) o.castShadow = sh; });
    g.userData = { wheels: wheels, smokeT: 0, angle: rand() * Math.PI * 2 };
    return g;
  }
  function toggleTrain() {
    if (train) {
      scene.remove(train);
      train.traverse(function (o) {
        if (o.isMesh) { o.geometry.dispose(); o.material.dispose(); }
      });
      train = null;
      state.trainOn = false;
    } else {
      train = buildTrain();
      scene.add(train);
      state.trainOn = true;
      AudioSys.whistle();
    }
  }
  function updateTrain(dt) {
    var spd = 11;
    var ud = train.userData;
    ud.angle = (ud.angle + dt * spd / TRACK_R) % (Math.PI * 2);
    var a = ud.angle;
    var px = Math.sin(a) * TRACK_R, pz = Math.cos(a) * TRACK_R;
    train.position.set(px, terrainHeight(px, pz), pz);
    train.rotation.y = a;
    train.rotation.z = Math.sin(a * 2) * 0.03;
    for (var i = 0; i < ud.wheels.length; i++) ud.wheels[i].rotation.x += dt * spd / 0.78;
    ud.smokeT -= dt;
    if (ud.smokeT <= 0) { ud.smokeT = 0.55; emitSmoke(); }
  }

  /* ---------------- scatter caches (deterministic per preset) ---------------- */
  function buildScatterCache() {
    for (var q = 0; q < PRESET_NAMES.length; q++) {
      var name = PRESET_NAMES[q];
      var preset = PRESETS[name];
      var entry = {
        grass: [], flowers: [], trees: [], rocks: [], clouds: [], wind: [],
        treeGroups: [], rockGroups: [], cloudGroups: []
      };
      cache[name] = entry;
      // grass
      var grassPal = ['#6d9e55', '#7fae60', '#8fb868', '#a8b56b', '#c2ae6a', '#93b05e'];
      for (var gi = 0; gi < preset.grass; gi++) {
        var sp = scatterSpot(0, 320, 7.5, 20, 9);
        if (!sp) continue;
        entry.grass.push({
          x: sp.x, z: sp.z, y: sp.y,
          s: 0.6 + rand() * 0.9, ry: rand() * Math.PI * 2,
          lean: (rand() - 0.5) * 0.3, ci: (rand() * grassPal.length) | 0
        });
      }
      // flowers
      var flPal = ['#e8a0b4', '#f2d06b', '#f5f0e6', '#c9a8e0', '#efb9a0'];
      for (var fi = 0; fi < preset.flowers; fi++) {
        var fp = scatterSpot(0, 240, 5, 20, 5);
        if (!fp) continue;
        entry.flowers.push({ x: fp.x, z: fp.z, y: fp.y, ry: rand() * Math.PI * 2, sc: 0.8 + rand() * 0.5, ci: (rand() * flPal.length) | 0 });
      }
      // trees
      var treePal = ['#6f9e5a', '#7fae68', '#5d8f4e', '#93b872', '#a8c078'];
      for (var ti = 0; ti < preset.trees; ti++) {
        var tp = scatterSpot(30, 320, 7, 34, 7);
        if (!tp) continue;
        entry.trees.push({ x: tp.x, z: tp.z, y: tp.y, ry: rand() * Math.PI * 2, sc: 0.85 + rand() * 1.1, s: 0.8 + rand() * 0.6, ci: (rand() * treePal.length) | 0 });
      }
      // rocks
      for (var ri = 0; ri < preset.rocks; ri++) {
        var ang = rand() * Math.PI * 2, r = 30 + rand() * 290;
        var rx = Math.cos(ang) * r, rz = Math.sin(ang) * r;
        var ry2 = terrainHeight(rx, rz);
        if (slopeAt(rx, rz) > 10) continue;
        entry.rocks.push({ x: rx, z: rz, y: ry2, s: 0.7 + rand() * 1.6, ry: rand() * Math.PI * 2 });
      }
      // clouds
      for (var ci2 = 0; ci2 < preset.clouds; ci2++) {
        var ca = rand() * Math.PI * 2, cr = 60 + rand() * 190;
        var parts = [];
        var np = 3 + ((rand() * 3) | 0);
        for (var pj = 0; pj < np; pj++) {
          parts.push({
            r: 8 + rand() * 9,
            dx: (rand() - 0.5) * 26, dy: (rand() - 0.5) * 6, dz: (rand() - 0.5) * 18,
            g: rand() * 0.35
          });
        }
        entry.clouds.push({ x: Math.cos(ca) * cr, z: Math.sin(ca) * cr, y: 150 + rand() * 80, parts: parts });
      }
    }
  }
  function scatterSpot(minR, maxR, maxSlope, avoidPond, avoidTrackBand) {
    for (var tries = 0; tries < 24; tries++) {
      var ang = rand() * Math.PI * 2, r = minR + rand() * (maxR - minR);
      var x = Math.cos(ang) * r, z = Math.sin(ang) * r;
      var pd = Math.hypot(x - 48, z + 42);
      if (pd < avoidPond) continue;
      if (Math.abs(Math.hypot(x, z) - TRACK_R) < avoidTrackBand) continue;
      if (slopeAt(x, z) > maxSlope) continue;
      return { x: x, z: z, y: terrainHeight(x, z) };
    }
    return null;
  }
  function slopeAt(x, z) {
    var d = 5;
    return Math.abs(terrainHeight(x + d, z) - terrainHeight(x - d, z)) +
           Math.abs(terrainHeight(x, z + d) - terrainHeight(x, z - d));
  }

  /* ---------------- rebuild by quality ---------------- */
  var GROUP_KEYS = { trees: 'treeGroups', rocks: 'rockGroups', clouds: 'cloudGroups' };
  function clearGroup(key) {
    var entry = cache[state.quality];
    var list = entry[GROUP_KEYS[key]] || [];
    for (var i = 0; i < list.length; i++) {
      var obj = list[i];
      scene.remove(obj);
      obj.traverse(function (o) {
        if (o.isMesh) { o.geometry.dispose(); o.material.dispose(); }
      });
    }
    list.length = 0;
  }
  function buildAll(name) {
    state.quality = name;
    var preset = PRESETS[name];
    applyPixelRatio();
    sunLight.castShadow = preset.shadows;
    if (terrain) terrain.receiveShadow = preset.shadows;
    buildClouds();
    buildTrees();
    buildRocks();
    buildFlowers();
    buildGrass();
    buildWindLines();
    if (train) {
      var sh = preset.shadows;
      train.traverse(function (o) { if (o.isMesh) o.castShadow = sh; });
    }
  }
  function setQuality(name) {
    if (name === state.quality) { buildAll(name); return; }
    buildAll(name);
    var btns = document.querySelectorAll('.qbtn');
    for (var i = 0; i < btns.length; i++) btns[i].classList.toggle('active', btns[i].dataset.q === name);
  }
  function applyPixelRatio() {
    var preset = PRESETS[state.quality];
    var dpr = window.devicePixelRatio || 1;
    renderer.setPixelRatio(Math.min(2, dpr * preset.scale));
  }

  /* ---------------- input ---------------- */
  function bindInput() {
    var cv = renderer.domElement;
    window.addEventListener('keydown', function (e) {
      keys[e.code] = true;
      if (e.code === 'Space' || e.code === 'ArrowUp' || e.code === 'ArrowDown') e.preventDefault();
      if (e.repeat) return;
      switch (e.code) {
        case 'KeyF': state.flying = !state.flying; state.cine = false; break;
        case 'KeyT': toggleTrain(); break;
        case 'KeyC': state.cine = !state.cine; break;
        case 'KeyH': togglePanel(); break;
        case 'KeyP': state.paused = !state.paused; break;
        case 'Digit1': setQuality('Low'); break;
        case 'Digit2': setQuality('Med'); break;
        case 'Digit3': setQuality('High'); break;
        case 'Digit4': setQuality('Ultra'); break;
        case 'Escape': dragging = false; closePanel(); break;
      }
    });
    window.addEventListener('keyup', function (e) { keys[e.code] = false; });
    window.addEventListener('blur', function () { keys = {}; dragging = false; });
    cv.addEventListener('click', function () {
      if (panelOpen) { closePanel(); return; }
      tryLock();
    });
    cv.addEventListener('mousedown', function () { dragging = true; });
    window.addEventListener('mouseup', function () { dragging = false; });
    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('pointerlockchange', function () {
      if (document.pointerLockElement === cv) {
        // browsers fire a junk mousemove right after locking — ignore it
        ignoreMoveOnce = true;
      } else {
        dragging = false;
      }
    });
  }
  function tryLock() {
    var cv = renderer.domElement;
    try {
      if (cv.requestPointerLock && document.pointerLockElement !== cv) cv.requestPointerLock();
    } catch (e) { /* pointer lock unavailable — drag-look fallback works */ }
  }
  function onMouseMove(e) {
    if (ignoreMoveOnce) { ignoreMoveOnce = false; return; }
    var mx = e.movementX || 0, my = e.movementY || 0;
    if (Math.abs(mx) > 300 || Math.abs(my) > 300) return; // junk events (lock snap, synthetic)
    var locked = document.pointerLockElement === renderer.domElement;
    if (!locked && !dragging) return;
    yaw -= mx * 0.0022;
    pitch -= my * 0.0022;
    var lim = state.flying ? 1.5 : 1.2;
    pitch = Math.max(-lim, Math.min(lim, pitch));
  }

  /* ---------------- camera ---------------- */
  function anyMove() {
    return keys['KeyW'] || keys['KeyA'] || keys['KeyS'] || keys['KeyD'] || keys['Space'] || keys['ControlLeft'] || keys['ControlRight'];
  }
  function updateCamera(dt) {
    if (state.cine) {
      state.cineT += dt / 90;
      var a = state.cineT * Math.PI * 2;
      camera.position.set(Math.sin(a) * 185, 60 + Math.sin(a) * 12, Math.cos(a) * 185);
      camera.lookAt(0, 12, 0);
      if (anyMove()) state.cine = false;
      return;
    }
    var shift = keys['ShiftLeft'] || keys['ShiftRight'];
    var spd = state.flying ? (shift ? 46 : 20) : (shift ? 17 : 7.6);
    var fx = -Math.sin(yaw), fz = -Math.cos(yaw);
    var rx = Math.cos(yaw), rz = -Math.sin(yaw);
    var dx = 0, dz = 0;
    if (keys['KeyW']) { dx += fx; dz += fz; }
    if (keys['KeyS']) { dx -= fx; dz -= fz; }
    if (keys['KeyD']) { dx += rx; dz += rz; }
    if (keys['KeyA']) { dx -= rx; dz -= rz; }
    var len = Math.hypot(dx, dz);
    if (len > 0) { dx /= len; dz /= len; }
    var tx = pos.x + dx * spd * dt;
    var tz = pos.z + dz * spd * dt;
    var ty;
    if (state.flying) {
      var up = (keys['Space'] ? 1 : 0) - (keys['ControlLeft'] || keys['ControlRight'] ? 1 : 0);
      ty = pos.y + up * spd * 0.8 * dt;
    } else {
      ty = terrainHeight(tx, tz) + EYE;
    }
    var k = Math.min(1, dt * 12);
    pos.x += (tx - pos.x) * k;
    pos.z += (tz - pos.z) * k;
    pos.y += (ty - pos.y) * k;
    camera.position.set(pos.x, pos.y, pos.z);
    camera.rotation.set(pitch, yaw, 0, 'YXZ');
  }

  /* ---------------- wind helper ---------------- */
  var windMag = 1;
  function getWindDir(t) {
    windDirTmp.set(Math.sin(t * 0.05) * 0.35, 0, 0.9 + Math.cos(t * 0.04) * 0.15).normalize();
    return windDirTmp;
  }

  /* ---------------- resize ---------------- */
  function onResize() {
    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(window.innerWidth, window.innerHeight);
  }

  /* ---------------- UI ---------------- */
  function $(id) { return document.getElementById(id); }
  function bindUI() {
    bindRange('sGrass', function (v) { state.density = v / 100; buildGrass(); });
    bindRange('sScale', function (v) {
      var preset = PRESETS[state.quality];
      var dpr = window.devicePixelRatio || 1;
      renderer.setPixelRatio(Math.min(2, dpr * preset.scale * v / 100));
    });
    bindRange('sWind', function (v) { windParams.mean = v; });
    bindRange('sGust', function (v) { windParams.gustiness = v; });
    bindRange('sExp', function (v) { renderer.toneMappingExposure = v; });
    bindRange('sPaint', function (v) { renderer.domElement.style.filter = 'saturate(' + v + ')'; });
    bindRange('sVol', function (v) { AudioSys.setVolume(v); });
    $('cMusic').addEventListener('change', function () { AudioSys.setMusic(this.checked); });
    $('cWindField').addEventListener('change', function () { if (windLines) windLines.visible = this.checked; });
    var btns = document.querySelectorAll('.qbtn');
    for (var i = 0; i < btns.length; i++) {
      btns[i].addEventListener('click', function () { setQuality(this.dataset.q); });
    }
    $('close').addEventListener('click', closePanel);
    // veil
    var prog = 0;
    var iv = setInterval(function () {
      prog += Math.random() * 9 + 3;
      $('barFill').style.width = Math.min(100, prog) + '%';
      if (prog >= 100) {
        clearInterval(iv);
        $('prep').textContent = 'ready';
        $('enter').disabled = false;
      }
    }, 70);
    $('enter').addEventListener('click', function () {
      this.blur();
      // CSS transition fades the veil in real browsers; the timeout is a
      // guarantee for throttled/headless environments
      $('veil').classList.add('gone');
      setTimeout(function () {
        $('veil').style.opacity = '0';
        $('veil').style.pointerEvents = 'none';
      }, 1900);
      AudioSys.start();
      setTimeout(tryLock, 80);
    });
  }
  function bindRange(id, fn) {
    var el = $(id);
    el.addEventListener('input', function () {
      var v = parseFloat(el.value);
      $(id + 'V').textContent = el.value;
      fn(v);
    });
  }
  function togglePanel() {
    panelOpen = !panelOpen;
    $('panel').classList.toggle('closed', !panelOpen);
  }
  function closePanel() {
    panelOpen = false;
    $('panel').classList.add('closed');
  }

  /* ---------------- main loop ---------------- */
  function animate() {
    requestAnimationFrame(animate);
    var dt = Math.min(clock.getDelta(), 0.05);
    var t = clock.elapsedTime;

    // sun drift (P pauses)
    if (!state.paused) state.sunA += dt * 0.006;
    sunDirTmp.set(Math.cos(state.sunA) * 0.78, 0.46, Math.sin(state.sunA) * 0.78).normalize();
    sunLight.position.copy(sunDirTmp).multiplyScalar(520);
    sunSprite.position.copy(sunDirTmp).multiplyScalar(840);
    sunCore.position.copy(sunDirTmp).multiplyScalar(840);
    skyMat.uniforms.uSun.value.copy(sunDirTmp);

    // wind
    var gust = windParams.gustiness * (0.55 + 0.45 * Math.sin(t * 0.31) * Math.sin(t * 0.67 + 1.4));
    windMag = Math.max(0.1, windParams.mean * (0.8 + gust * 0.5));
    var wd = getWindDir(t);
    var shader = grassMat && grassMat.userData.shader;
    if (shader) {
      shader.uniforms.uTime.value = t;
      shader.uniforms.uWind.value = windMag;
      shader.uniforms.uWindDir.value.copy(wd);
    }

    // clouds drift
    for (var i = 0; i < cloudGroups.length; i++) {
      var cg = cloudGroups[i];
      cg.position.x += wd.x * windMag * 0.5 * dt;
      cg.position.z += wd.z * windMag * 0.5 * dt;
      if (cg.position.x > 440) cg.position.x = -440;
      if (cg.position.x < -440) cg.position.x = 440;
      if (cg.position.z > 440) cg.position.z = -440;
      if (cg.position.z < -440) cg.position.z = 440;
    }

    if (windLines && windLines.visible) updateWindLines(t);
    updateSmoke(dt, t);
    if (train) updateTrain(dt);

    updateCamera(dt);
    renderer.render(scene, camera);
  }

  /* ---------------- boot ---------------- */
  try {
    init();
  } catch (err) {
    var e = document.getElementById('err');
    if (e) e.textContent = 'Could not start: ' + err.message;
    console.error(err);
  }
})();
