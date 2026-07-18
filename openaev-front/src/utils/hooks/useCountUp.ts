import { useEffect, useRef, useState } from 'react';

/**
 * Animates a number from 0 (or the previous value) to the target value.
 * Used by dashboard widgets to give KPIs a "live" feel.
 */
const useCountUp = (target: number, duration = 1200) => {
  const [value, setValue] = useState(0);
  const previousRef = useRef(0);

  useEffect(() => {
    const from = previousRef.current;
    const delta = target - from;
    if (delta === 0) {
      setValue(target);
      return undefined;
    }
    let frame: number;
    const start = performance.now();
    const tick = (now: number) => {
      const progress = Math.min((now - start) / duration, 1);
      // easeOutCubic for a snappy then smooth landing
      const eased = 1 - Math.pow(1 - progress, 3);
      setValue(from + delta * eased);
      if (progress < 1) {
        frame = requestAnimationFrame(tick);
      } else {
        previousRef.current = target;
      }
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [target, duration]);

  return value;
};

export default useCountUp;
