import React, { useEffect, useRef, useState } from 'react';

import './HoldToConfirm.css';

const DEFAULT_DURATION = 1500;
const SHAKE_MS = 380;

// Rounded-rect outline starting at the top centre and running clockwise, so the
// progress stroke grows clockwise from 12 o'clock.
const RING_PATH =
  'M 50 1.5 H 80 A 18.5 18.5 0 0 1 80 38.5 H 20 A 18.5 18.5 0 0 1 20 1.5 Z';

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Hold-to-confirm button: the outer ring fills over ``duration`` ms, releasing
 * early rewinds it, and a full hold shakes once then shows a check + label.
 */
export default function HoldToConfirm({
  label,
  successLabel,
  errorLabel,
  onConfirm,
  duration = DEFAULT_DURATION,
}) {
  const [progress, setProgress] = useState(0);
  const [phase, setPhase] = useState('idle');
  const [rewinding, setRewinding] = useState(false);
  const rafRef = useRef(0);
  const startedAtRef = useRef(0);
  const timersRef = useRef([]);

  useEffect(
    () => () => {
      cancelAnimationFrame(rafRef.current);
      timersRef.current.forEach(clearTimeout);
    },
    []
  );

  const complete = async () => {
    cancelAnimationFrame(rafRef.current);
    setPhase('shaking');
    try {
      // Let the shake play while the write is in flight; success waits for both.
      await Promise.all([onConfirm(), sleep(SHAKE_MS)]);
      setPhase('success');
    } catch (error) {
      setPhase('error');
      timersRef.current.push(
        setTimeout(() => {
          setPhase('idle');
          setProgress(0);
        }, 1600)
      );
    }
  };

  const tick = () => {
    const ratio = Math.min(
      1,
      (performance.now() - startedAtRef.current) / duration
    );
    setProgress(ratio);
    if (ratio >= 1) {
      complete();
    } else {
      rafRef.current = requestAnimationFrame(tick);
    }
  };

  const start = (event) => {
    if (phase === 'holding' || phase === 'shaking' || phase === 'success') return;
    if (event.pointerType === 'mouse' && event.button !== 0) return;
    if (event.repeat) return;
    event.preventDefault();
    startedAtRef.current = performance.now();
    setRewinding(false);
    setProgress(0);
    setPhase('holding');
    rafRef.current = requestAnimationFrame(tick);
  };

  const cancel = () => {
    if (phase !== 'holding') return;
    cancelAnimationFrame(rafRef.current);
    setPhase('idle');
    setRewinding(true);
    setProgress(0);
    timersRef.current.push(setTimeout(() => setRewinding(false), 220));
  };

  const onKeyDown = (event) => {
    if (event.key === ' ' || event.key === 'Enter') start(event);
  };
  const onKeyUp = (event) => {
    if (event.key === ' ' || event.key === 'Enter') cancel();
  };

  const labelText =
    phase === 'success'
      ? `✓ ${successLabel}`
      : phase === 'error'
        ? errorLabel
        : label;

  return (
    <button
      type="button"
      className={`hold-confirm is-${phase}${rewinding ? ' is-rewinding' : ''}`}
      onPointerDown={start}
      onPointerUp={cancel}
      onPointerCancel={cancel}
      onKeyDown={onKeyDown}
      onKeyUp={onKeyUp}
      onContextMenu={(event) => event.preventDefault()}
      aria-label={phase === 'success' ? successLabel : label}
      data-testid="hold-to-confirm"
    >
      <svg
        className="hold-ring"
        viewBox="0 0 100 40"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <path
          className="hold-progress"
          pathLength="100"
          style={{ strokeDasharray: 100, strokeDashoffset: 100 - progress * 100 }}
          d={RING_PATH}
        />
      </svg>
      <span className="hold-label">{labelText}</span>
    </button>
  );
}
