// 지도/주행 화면에서 같이 쓰는 fog-of-war 캔버스 유틸.
// 숲속 안개 사진처럼 결이 부드럽고 흐릿하게 겹쳐지도록,
// 크고 옅은 블러 뭉치를 아주 많이 겹쳐 그려서 하나의 연속된 안개처럼 보이게 함.

// 고정 시드 난수 생성기 — 매번 다시 그려도 안개 결 배치가 안 바뀌게 함
// (Math.random을 쓰면 pan/zoom·리렌더마다 안개가 깜빡거리며 다시 배치돼서 지저분해짐)
function mulberry32(seed) {
  return function () {
    seed |= 0;
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// 화면 크기에 맞춰 안개 결(뭉치) 목록을 한 번만 만들어서 캔버스에 캐싱해둠
function getMistWisps(canvasEl, w, h, seed = 20260808) {
  const key = `${w}x${h}:${seed}`;
  if (canvasEl._mistKey === key && canvasEl._mistWisps) {
    return canvasEl._mistWisps;
  }
  const rand = mulberry32(seed);
  const wisps = [];
  const count = Math.max(36, Math.round((w * h) / 5200));
  for (let i = 0; i < count; i++) {
    wisps.push({
      x: rand() * w,
      y: rand() * h,
      rx: 70 + rand() * 150,
      ry: 40 + rand() * 90,
      rot: rand() * Math.PI,
      a: 0.05 + rand() * 0.09,
      light: rand() > 0.55,
    });
  }
  canvasEl._mistKey = key;
  canvasEl._mistWisps = wisps;
  return wisps;
}

// 캔버스에 안개 베이스(구멍 뚫기 전 상태)를 그림. clearRect부터 다시 함.
export function paintMistBase(ctx, canvasEl, w, h, opts = {}) {
  const { baseAlpha = 0.5, seed = 20260808 } = opts;
  ctx.globalCompositeOperation = 'source-over';
  ctx.filter = 'none';
  ctx.clearRect(0, 0, w, h);

  ctx.fillStyle = `rgba(13, 15, 10, ${baseAlpha})`;
  ctx.fillRect(0, 0, w, h);

  const wisps = getMistWisps(canvasEl, w, h, seed);
  ctx.filter = 'blur(38px)';
  wisps.forEach((p) => {
    ctx.save();
    ctx.translate(p.x, p.y);
    ctx.rotate(p.rot);
    ctx.scale(p.rx / p.ry, 1);
    ctx.beginPath();
    ctx.fillStyle = p.light ? `rgba(72, 78, 60, ${p.a})` : `rgba(6, 8, 5, ${p.a + 0.1})`;
    ctx.arc(0, 0, p.ry, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  });
  ctx.filter = 'none';
}

// 특정 화면 좌표(px)를 부드러운 원형으로 걷어냄 (destination-out)
export function punchHole(ctx, x, y, radius, innerStop = 0.55) {
  ctx.globalCompositeOperation = 'destination-out';
  const gradient = ctx.createRadialGradient(x, y, 0, x, y, radius);
  gradient.addColorStop(0, 'rgba(0, 0, 0, 1)');
  gradient.addColorStop(innerStop, 'rgba(0, 0, 0, 0.85)');
  gradient.addColorStop(1, 'rgba(0, 0, 0, 0)');
  ctx.fillStyle = gradient;
  ctx.beginPath();
  ctx.arc(x, y, radius, 0, Math.PI * 2);
  ctx.fill();
  ctx.globalCompositeOperation = 'source-over';
}

// 캔버스를 컨테이너 실제 픽셀 크기에 맞춤. 크기가 바뀌었을 때만 리사이즈(그래야 안 지워짐 조건은 호출부에서 처리).
export function resizeCanvasToContainer(canvasEl, containerEl) {
  const w = containerEl.clientWidth;
  const h = containerEl.clientHeight;
  if (w === 0 || h === 0) return null;
  if (canvasEl.width !== w || canvasEl.height !== h) {
    canvasEl.width = w;
    canvasEl.height = h;
  }
  return { w, h };
}
