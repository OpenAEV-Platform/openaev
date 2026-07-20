import { useCallback, useEffect, useRef, useState } from 'react';

const GAP = 8;
const MIN_CHIP_WIDTH = 60;
const RESIZE_DEBOUNCE_MS = 150;

/**
 * Measures how many use-case chips fit on a single footer row (ported from
 * OpenCTI's marketplace card): chips that fit fully are shown, the last
 * visible chip may shrink with an ellipsis, and the rest collapses into a
 * "+N" chip - so a chip is never clipped mid-label.
 */
const useChipOverflow = (items: string[]) => {
  const [visibleCount, setVisibleCount] = useState(items.length);
  const containerRef = useRef<HTMLDivElement>(null);
  const chipRefs = useRef<(HTMLElement | null)[]>([]);

  const calculateVisibleCount = useCallback(() => {
    if (!containerRef.current) return;

    const containerWidth = containerRef.current.offsetWidth;

    let usedWidth = 0;
    let visibleChips = 0;

    for (let i = 0; i < chipRefs.current.length; i++) {
      const chip = chipRefs.current[i];
      if (!chip) continue;

      const chipWidth = chip.offsetWidth;
      const gapBeforeChip = i > 0 ? GAP : 0;
      const widthNeeded = usedWidth + gapBeforeChip + chipWidth;

      if (widthNeeded <= containerWidth) {
        usedWidth = widthNeeded;
        visibleChips += 1;
        continue;
      }

      // The chip does not fit completely: show it shrunk with an ellipsis
      // only when it is the last one (or the only hidden one) and there is
      // still meaningful space; otherwise leave it for the "+N" chip.
      const spaceLeft = containerWidth - usedWidth - gapBeforeChip;
      const isLastChip = i === items.length - 1;
      const chipsStillHidden = items.length - visibleChips;
      if ((isLastChip || chipsStillHidden === 1) && spaceLeft >= MIN_CHIP_WIDTH) {
        visibleChips += 1;
      }
      break;
    }

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
    visibleCount,
  };
};

export default useChipOverflow;
