import React, { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import * as THREE from 'three';
import './SphereView.css';

const RADIUS = 5.5;
const SPRITE_W = 1.5;
const ZOOM_MIN = 9;
const ZOOM_MAX = 24;
const BLOOM_DURATION = 1.4;
const HOVER_SCALE = 1.3;

const clamp = (v, lo, hi) => Math.min(Math.max(v, lo), hi);

// Elastic settle — overshoots past the target, giving the bloom its "pop".
function easeOutBack(x) {
  const c1 = 1.70158;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
}

// Even distribution of `count` points on a unit sphere (golden-angle spiral).
// Midpoint shift keeps every book off the rotation axis, so the first/last
// books are not pinned to the top/bottom poles while the sphere rotates.
function fibonacciSphere(count) {
  const pts = [];
  const golden = Math.PI * (3 - Math.sqrt(5));
  for (let i = 0; i < count; i++) {
    const y = 1 - ((i + 0.5) / count) * 2;
    const r = Math.sqrt(Math.max(0, 1 - y * y));
    const theta = golden * i;
    pts.push(new THREE.Vector3(Math.cos(theta) * r, y, Math.sin(theta) * r));
  }
  return pts;
}

// Placeholder cover: gradient tinted by title hash + first character.
function fallbackTexture(title) {
  const c = document.createElement('canvas');
  c.width = 128;
  c.height = 182;
  const ctx = c.getContext('2d');
  let hue = 0;
  for (let i = 0; i < title.length; i++) hue = (hue * 31 + title.charCodeAt(i)) % 360;
  const grad = ctx.createLinearGradient(0, 0, 0, 182);
  grad.addColorStop(0, `hsl(${hue}, 42%, 62%)`);
  grad.addColorStop(1, `hsl(${(hue + 30) % 360}, 48%, 40%)`);
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, 128, 182);
  ctx.fillStyle = 'rgba(255,255,255,0.92)';
  ctx.font = 'bold 64px sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText((title[0] || '?').toUpperCase(), 64, 91);
  const texture = new THREE.CanvasTexture(c);
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

// Bake a cover onto an opaque canvas and trim the transparent padding, so the
// sprite shows the cover at its true aspect ratio, full-bleed. The CDN thumbs
// are square webps with transparent padding — blending those semi-transparent
// pixels over the bright background washes the covers out.
function bakeCover(img) {
  const w = img.naturalWidth;
  const h = img.naturalHeight;
  // Find the cover bounds from the ORIGINAL alpha, then crop to it WITHOUT a
  // white underlay — covers are photographed at an angle (trapezoid, rounded
  // corners), so the trimmed bounding box has transparent corners that must
  // stay transparent instead of showing white edges.
  const src = document.createElement('canvas');
  src.width = w;
  src.height = h;
  const sctx = src.getContext('2d');
  sctx.drawImage(img, 0, 0);
  const sd = sctx.getImageData(0, 0, w, h).data;
  let minX = w, minY = h, maxX = -1, maxY = -1;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      if (sd[(y * w + x) * 4 + 3] > 32) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
      }
    }
  }
  const tw = maxX >= 0 ? maxX - minX + 1 : w;
  const th = maxY >= 0 ? maxY - minY + 1 : h;
  const c = document.createElement('canvas');
  c.width = tw;
  c.height = th;
  c.getContext('2d').drawImage(img, minX, minY, tw, th, 0, 0, tw, th);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  return t;
}

/**
 * 3D sphere view: items bloom outward from the center onto a rotating,
 * draggable sphere. Hover shows details, click selects.
 */
export default function SphereView({ items, getThumb, getTitle, getSubtitle, onSelect, autoRotateSpeed = 0.004 }) {
  const containerRef = useRef(null);
  const tooltipRef = useRef(null);
  const restartBloomRef = useRef(null);
  const { t } = useTranslation();

  // Keep latest callbacks in a ref so the scene only rebuilds when items change.
  const propsRef = useRef({ getThumb, getTitle, getSubtitle, onSelect, autoRotateSpeed });
  propsRef.current = { getThumb, getTitle, getSubtitle, onSelect, autoRotateSpeed };

  useEffect(() => {
    const container = containerRef.current;
    const tooltip = tooltipRef.current;
    if (!container || !tooltip) return;

    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    container.appendChild(renderer.domElement);

    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(50, 1, 0.1, 100);
    camera.position.set(0, 0, 14);

    const group = new THREE.Group();
    scene.add(group);

    const { getThumb, getTitle, getSubtitle, onSelect, autoRotateSpeed } = propsRef.current;

    // ── Build sprites ──
    const dirs = fibonacciSphere(items.length);
    const states = [];
    const sprites = [];
    let disposed = false;
    items.forEach((item, i) => {
      const title = getTitle(item) || 'Untitled';
      const url = getThumb(item);
      const dir = dirs[i];
      // TextureLoader calls onLoad/onError — it never dispatches 'load'/'error'
      // events on the texture itself, so the aspect/bake swap must live in the
      // callbacks.
      const st = { progress: 0, delay: i * 0.025, hoverEase: 1, sprite: null, dir, item, title, texture: null, aspect: url ? 1 : 182 / 128 };
      let texture;
      if (url) {
        texture = new THREE.TextureLoader().load(url, () => {
          if (disposed) return;
          const baked = bakeCover(texture.image);
          st.aspect = baked.image.height / baked.image.width;
          st.sprite.material.map = baked;
          st.sprite.material.needsUpdate = true;
          st.texture.dispose();
          st.texture = baked;
        }, undefined, () => {
          if (disposed) return;
          const fb = fallbackTexture(st.title);
          st.sprite.material.map = fb;
          st.sprite.material.needsUpdate = true;
          st.texture.dispose();
          st.texture = fb;
        });
        texture.colorSpace = THREE.SRGBColorSpace;
      } else {
        texture = fallbackTexture(title);
      }
      st.texture = texture;
      const material = new THREE.SpriteMaterial({ map: texture, transparent: true });
      const sprite = new THREE.Sprite(material);
      st.sprite = sprite;
      states.push(st);
      sprites.push(sprite);
      group.add(sprite);
    });

    // ── Bloom timeline ──
    let bloomStart = performance.now();
    const restartBloom = () => {
      states.forEach((s) => { s.progress = 0; });
      bloomStart = performance.now();
    };
    restartBloomRef.current = restartBloom;

    // ── Interaction state ──
    const raycaster = new THREE.Raycaster();
    const pointer = new THREE.Vector2(2, 2); // off-canvas until first move
    let hovered = null;
    let dragging = false;
    let moved = 0;
    let lastX = 0;
    let lastY = 0;

    const onPointerDown = (e) => {
      dragging = true;
      moved = 0;
      lastX = e.clientX;
      lastY = e.clientY;
      renderer.domElement.classList.add('dragging');
      renderer.domElement.setPointerCapture(e.pointerId);
    };
    const onPointerMove = (e) => {
      const rect = renderer.domElement.getBoundingClientRect();
      pointer.x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
      pointer.y = -((e.clientY - rect.top) / rect.height) * 2 + 1;
      if (dragging) {
        const dx = e.clientX - lastX;
        const dy = e.clientY - lastY;
        moved += Math.abs(dx) + Math.abs(dy);
        group.rotation.y += dx * 0.006;
        group.rotation.x = clamp(group.rotation.x + dy * 0.004, -1.2, 1.2);
        lastX = e.clientX;
        lastY = e.clientY;
      }
    };
    const onPointerUp = () => {
      dragging = false;
      renderer.domElement.classList.remove('dragging');
      if (moved < 6 && hovered && onSelect) onSelect(hovered.item);
    };
    const onPointerLeave = () => {
      hovered = null;
      tooltip.style.display = 'none';
    };
    const onWheel = (e) => {
      e.preventDefault();
      camera.position.z = clamp(camera.position.z + e.deltaY * 0.01, ZOOM_MIN, ZOOM_MAX);
      updateHeight();
    };

    renderer.domElement.addEventListener('pointerdown', onPointerDown);
    renderer.domElement.addEventListener('pointermove', onPointerMove);
    renderer.domElement.addEventListener('pointerup', onPointerUp);
    renderer.domElement.addEventListener('pointerleave', onPointerLeave);
    renderer.domElement.addEventListener('wheel', onWheel, { passive: false });

    // ── Resize ──
    // Size the area so the fully-bloomed sphere (with easeOutBack overshoot)
    // fits the height; re-derived on zoom so zooming never clips the sphere.
    const updateHeight = () => {
      const box = container.parentElement; // .sphere-view owns the height
      const w = box.clientWidth;
      if (!w) return;
      const halfH = Math.tan(THREE.MathUtils.degToRad(camera.fov / 2)) * camera.position.z;
      const target = (w * 2 * RADIUS * 1.15) / (2 * halfH);
      box.style.height = `${Math.min(Math.max(target, 300), window.innerHeight * 0.9)}px`;
    };
    const resize = () => {
      updateHeight();
      const w = container.clientWidth;
      const h = container.clientHeight;
      renderer.setSize(w, h);
      camera.aspect = w / h;
      camera.updateProjectionMatrix();
    };
    resize();
    const ro = new ResizeObserver(resize);
    ro.observe(container);

    // ── Render loop ──
    const titleEl = tooltip.querySelector('.sphere-tooltip-title');
    const subEl = tooltip.querySelector('.sphere-tooltip-sub');
    let raf;
    let last = performance.now();
    const animate = (now) => {
      raf = requestAnimationFrame(animate);
      const dt = Math.min((now - last) / 1000, 0.05);
      last = now;

      if (!dragging) group.rotation.y += autoRotateSpeed * dt * 60;

      // Bloom: fly each sprite from center to its shell position, staggered.
      const elapsed = (now - bloomStart) / 1000;
      for (const st of states) {
        const p = clamp((elapsed - st.delay) / BLOOM_DURATION, 0, 1);
        const e = easeOutBack(p);
        st.sprite.position.copy(st.dir).multiplyScalar(RADIUS * e);
        const hover = clamp(st.hoverEase, 0.4, HOVER_SCALE);
        const s = Math.max(e, 0.01) * hover;
        st.sprite.scale.set(SPRITE_W * s, SPRITE_W * st.aspect * s, 1);
      }

      // Hover raycast
      const wasHovered = hovered;
      hovered = null;
      raycaster.setFromCamera(pointer, camera);
      const hits = raycaster.intersectObjects(sprites, false);
      if (hits.length) {
        const st = states.find((s) => s.sprite === hits[0].object);
        if (st) hovered = st;
      }
      if (hovered !== wasHovered) {
        if (hovered) {
          titleEl.textContent = hovered.title;
          subEl.textContent = getSubtitle(hovered.item) || '';
          tooltip.style.display = 'block';
        } else {
          tooltip.style.display = 'none';
        }
      }
      for (const st of states) {
        const target = hovered === st ? HOVER_SCALE : 1;
        st.hoverEase += (target - st.hoverEase) * Math.min(dt * 10, 1);
      }

      // Keep tooltip glued to the hovered sprite.
      if (hovered) {
        const v = new THREE.Vector3();
        hovered.sprite.getWorldPosition(v);
        v.project(camera);
        const rect = container.getBoundingClientRect();
        const x = rect.left + (v.x * 0.5 + 0.5) * container.clientWidth;
        const y = rect.top + (-v.y * 0.5 + 0.5) * container.clientHeight;
        tooltip.style.left = `${x}px`;
        tooltip.style.top = `${y}px`;
        tooltip.classList.toggle('below', y < 150);
      }

      renderer.render(scene, camera);
    };
    animate(performance.now());

    return () => {
      disposed = true;
      cancelAnimationFrame(raf);
      ro.disconnect();
      renderer.domElement.removeEventListener('pointerdown', onPointerDown);
      renderer.domElement.removeEventListener('pointermove', onPointerMove);
      renderer.domElement.removeEventListener('pointerup', onPointerUp);
      renderer.domElement.removeEventListener('pointerleave', onPointerLeave);
      renderer.domElement.removeEventListener('wheel', onWheel);
      renderer.domElement.remove();
      states.forEach((st) => {
        st.texture.dispose();
        st.sprite.material.dispose();
      });
      renderer.dispose();
      restartBloomRef.current = null;
    };
  }, [items]);

  return (
    <div className="sphere-view">
      <div ref={containerRef} className="sphere-canvas" />
      <div className="sphere-controls">
                <button className="btn-pill-link" onClick={() => restartBloomRef.current && restartBloomRef.current()}>
          {t('sphere.bloom')}
        </button>
      </div>
      <div ref={tooltipRef} className="sphere-tooltip" style={{ display: 'none' }}>
        <h4 className="sphere-tooltip-title" />
        <p className="sphere-tooltip-sub" />
        <p className="sphere-tooltip-hint">{t('sphere.hint')}</p>
      </div>
    </div>
  );
}
