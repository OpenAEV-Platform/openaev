import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';

import useChipOverflow from './useChipOverflow';

interface Props {
  /** The labels of the chips, as displayed. */
  labels: string[];
}

/**
 * Chips on one row: the ones that fit are shown, the rest collapse into "+N", whose tooltip lists them. A chip is
 * never clipped mid-label.
 */
const OverflowChips = ({ labels }: Props) => {
  const { containerRef, chipRefs, overflowRef, visibleCount } = useChipOverflow(labels);

  const hiddenCount = labels.length - visibleCount;
  // Only what the "+N" stands for: the chips already shown are not repeated.
  const hiddenLabels = labels.slice(visibleCount).join(', ');

  return (
    <div
      ref={containerRef}
      style={{
        display: 'flex',
        flexWrap: 'nowrap',
        gap: 8,
        overflow: 'hidden',
        position: 'relative',
        minWidth: 0,
      }}
    >
      {labels.map((label, index) => {
        const isVisible = index < visibleCount;
        return (
          <div
            // two chips may carry the same label
            key={`${index}-${label}`}
            ref={(element) => {
              chipRefs.current[index] = element;
            }}
            // Fixed-size chips, so the overflow count is measurable.
            style={{
              flexShrink: 0,
              maxWidth: '100%',
              minWidth: 0,
              visibility: isVisible ? 'visible' : 'hidden',
              position: isVisible ? 'relative' : 'absolute',
            }}
          >
            <Tooltip>
              <TooltipTrigger asChild>
                <Chip
                  label={label}
                  severity="info"
                  style={{ maxWidth: '100%' }}
                />
              </TooltipTrigger>
              {label && <TooltipContent>{label}</TooltipContent>}
            </Tooltip>
          </div>
        );
      })}
      {/* Always mounted, so its width is known before anything is hidden; it is
          only taken out of the flow while there is nothing to count. */}
      <div
        ref={(element) => {
          overflowRef.current = element;
        }}
        style={{
          flexShrink: 0,
          visibility: hiddenCount > 0 ? 'visible' : 'hidden',
          position: hiddenCount > 0 ? 'relative' : 'absolute',
        }}
      >
        <Tooltip>
          <TooltipTrigger asChild>
            <Chip label={`+${Math.max(hiddenCount, 1)}`} severity="info" />
          </TooltipTrigger>
          {hiddenLabels && <TooltipContent>{hiddenLabels}</TooltipContent>}
        </Tooltip>
      </div>
    </div>
  );
};

export default OverflowChips;
