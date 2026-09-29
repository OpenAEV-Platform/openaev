import { useCallback, useEffect, useRef, useState } from 'react';

const GAP = 8;
const RESIZE_DEBOUNCE_MS = 150;

/**
 * How many use-case chips fit on one row: only whole chips show, the rest collapse
 * into "+N" whose width is reserved. A chip is never cut — the library Chip has no
 * ellipsis, so a shrunken one clipped mid-word.
 */
const useChipOverflow = (items: string[]) => {
  const [visibleCount, setVisibleCount] = useState(items.length);
  const containerRef = useRef<HTMLDivElement>(null);
  const chipRefs = useRef<(HTMLElement | null)[]>([]);
  const overflowRef = useRef<HTMLElement | null>(null);

  const calculateVisibleCount = useCallback(() => {
    if (!containerRef.current) return;

    const containerWidth = containerRef.current.offsetWidth;
    const overflowWidth = overflowRef.current?.offsetWidth ?? 0;

    let usedWidth = 0;
    let visibleChips = 0;

    for (let i = 0; i < chipRefs.current.length; i++) {
      const chip = chipRefs.current[i];
      if (!chip) continue;

      const widthNeeded = usedWidth + (i > 0 ? GAP : 0) + chip.offsetWidth;
      // Whatever is left behind needs the "+N" chip, so keep its room.
      const stillHidden = items.length - (i + 1);
      const reserved = stillHidden > 0 ? GAP + overflowWidth : 0;
      if (widthNeeded + reserved > containerWidth) break;

      usedWidth = widthNeeded;
      visibleChips += 1;
    }

    // One chip always shows: a lone "+3" says less than a use case and a count.
    setVisibleCount(Math.max(1, visibleChips));
  }, [items.length]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return undefined;

    let timeout: ReturnType<typeof setTimeout> | undefined;
    const observer = new ResizeObserver(() => {
      clearTimeout(timeout);
      timeout = setTimeout(calculateVisibleCount, RESIZE_DEBOUNCE_MS);
    });

    observer.observe(container);
    calculateVisibleCount();

    return () => {
      clearTimeout(timeout);
      observer.disconnect();
    };
  }, [calculateVisibleCount]);

  return {
    containerRef,
    chipRefs,
    overflowRef,
    visibleCount,
  };
};

export default useChipOverflow;
