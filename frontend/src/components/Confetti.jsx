import { useEffect, useRef } from 'react';
import '../pages/pages.css';

// Confetti (A5) — hiệu ứng giấy màu rơi khi hoàn thành phiên luyện tập.
// Tự viết, KHÔNG thêm dependency (package.json chưa có canvas-confetti).
// Mount khi phiên xong (run=true), tự dọn sau ~3.5s.
const COLORS = ['#E2482B', '#E8A93D', '#7A9B57', '#1F3A5F', '#EFE6D2', '#ffffff'];
const COUNT = 130;
const DURATION_MS = 3500;

export default function Confetti({ run }) {
  const ref = useRef(null);

  useEffect(() => {
    if (!run) return;
    const canvas = ref.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const W = (canvas.width = window.innerWidth);
    const H = (canvas.height = window.innerHeight);

    const parts = Array.from({ length: COUNT }, () => ({
      x: Math.random() * W,
      y: -30 - Math.random() * H * 0.35,
      w: 6 + Math.random() * 7,
      h: 8 + Math.random() * 9,
      vy: 2.2 + Math.random() * 3.2,
      vx: -1.6 + Math.random() * 3.2,
      rot: Math.random() * Math.PI * 2,
      vr: -0.12 + Math.random() * 0.24,
      sway: Math.random() * Math.PI * 2,
      color: COLORS[(Math.random() * COLORS.length) | 0],
    }));

    let raf = 0;
    const start = performance.now();
    const tick = (t) => {
      const elapsed = t - start;
      ctx.clearRect(0, 0, W, H);
      const fade = elapsed > DURATION_MS - 700 ? Math.max(0, (DURATION_MS - elapsed) / 700) : 1;
      for (const p of parts) {
        p.x += p.vx + Math.sin((t / 500) + p.sway) * 0.8;
        p.y += p.vy;
        p.rot += p.vr;
        if (p.y > H + 40) continue;
        ctx.save();
        ctx.globalAlpha = fade;
        ctx.translate(p.x, p.y);
        ctx.rotate(p.rot);
        ctx.fillStyle = p.color;
        ctx.fillRect(-p.w / 2, -p.h / 2, p.w, p.h);
        ctx.restore();
      }
      if (elapsed < DURATION_MS) {
        raf = requestAnimationFrame(tick);
      } else {
        ctx.clearRect(0, 0, W, H);
      }
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [run]);

  if (!run) return null;
  return <canvas ref={ref} className="confetti-canvas" aria-hidden="true" />;
}
