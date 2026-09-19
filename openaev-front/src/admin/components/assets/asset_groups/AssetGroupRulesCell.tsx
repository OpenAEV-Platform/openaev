import { Chip as FdsChip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Box } from '@mui/material';
import { type ReactNode, useEffect, useRef, useState } from 'react';

interface Props {
  /** One entry per rule, in reading order; separators travel with their rule. */
  items: ReactNode[];
  /** The same rules as plain text, for the overflow tooltip. */
  labels: string[];
}

// A rules cell never wraps: the row keeps one line, and whatever does not fit
// is counted in a trailing chip whose tooltip lists every rule. How many fit is
// measured, not guessed — the column is a percentage of a resizable table.
const AssetGroupRulesCell = ({ items, labels }: Props) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [visibleCount, setVisibleCount] = useState(items.length);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return undefined;

    const measure = () => {
      const children = [...container.children] as HTMLElement[];
      // The last child is the counter itself; it is measured with the rest so
      // the count it announces is the count that actually fits beside it.
      const available = container.clientWidth;
      let used = 0;
      let fits = 0;
      for (const child of children) {
        if (child.dataset.overflowCounter === 'true') continue;
        used += child.offsetWidth + 8;
        if (used > available) break;
        fits += 1;
      }
      setVisibleCount(previous => (previous === fits ? previous : Math.max(fits, 1)));
    };

    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(container);
    return () => observer.disconnect();
  }, [items.length]);

  const hidden = items.length - visibleCount;

  return (
    <Box
      ref={containerRef}
      sx={{
        padding: '0px 4px',
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'nowrap',
        overflow: 'hidden',
        gap: 1,
        minWidth: 0,
      }}
    >
      {items.slice(0, visibleCount)}
      {hidden > 0 && (
        <Tooltip>
          <TooltipTrigger asChild>
            <span data-overflow-counter="true" className="inline-flex shrink-0">
              <FdsChip label={`+${hidden}`} severity="neutral" size="sm" />
            </span>
          </TooltipTrigger>
          <TooltipContent>{labels.join(' · ')}</TooltipContent>
        </Tooltip>
      )}
    </Box>
  );
};

export default AssetGroupRulesCell;
