import { useLayoutEffect, useState } from 'react';

/**
 * Height (px) filling the viewport from an element's top down to the bottom, minus a gap matching
 * the app shell's bottom padding. Full-height views (graph canvases...) size with this instead of
 * guessing the chrome above with a hardcoded `calc(100vh - Npx)`, which drifts and leaves the page
 * with a vertical scrollbar.
 *
 * Returns a CALLBACK ref (attach it to the element) plus the measured height: the element often
 * mounts later than the component (behind loading states), and a callback ref re-measures at that
 * exact moment where a `useRef` would silently stay unmeasured.
 */
const useRemainingViewportHeight = (
  bottomGap = 24,
  minHeight = 420,
): [(el: HTMLElement | null) => void, number | undefined] => {
  const [element, setElement] = useState<HTMLElement | null>(null);
  const [height, setHeight] = useState<number>();
  useLayoutEffect(() => {
    if (!element) {
      return undefined;
    }
    const measure = () => {
      // Top relative to the document (not the viewport), so an already-scrolled page measures the
      // same as a fresh one.
      const top = element.getBoundingClientRect().top + window.scrollY;
      setHeight(Math.max(minHeight, window.innerHeight - top - bottomGap));
    };
    measure();
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, [element, bottomGap, minHeight]);
  return [setElement, height];
};

export default useRemainingViewportHeight;
