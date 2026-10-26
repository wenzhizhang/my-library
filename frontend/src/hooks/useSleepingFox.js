import { useCallback, useEffect, useRef, useState } from 'react';

// Idle for one minute → sleep for one minute → back awake, repeating while the
// user stays idle. Any interaction resets the cycle to the awake phase.
const IDLE_MS = 60_000;
const SLEEP_MS = 60_000;
const TICK_MS = 1_000;

export default function useSleepingFox() {
  const [sleeping, setSleeping] = useState(false);
  const lastActivity = useRef(Date.now());

  const touch = useCallback(() => {
    lastActivity.current = Date.now();
    setSleeping(false);
  }, []);

  useEffect(() => {
    const timer = setInterval(() => {
      const idle = Date.now() - lastActivity.current;
      const phase = Math.floor((idle - IDLE_MS) / SLEEP_MS);
      setSleeping(idle >= IDLE_MS && phase % 2 === 0);
    }, TICK_MS);
    return () => clearInterval(timer);
  }, []);

  return { sleeping, touch };
}
