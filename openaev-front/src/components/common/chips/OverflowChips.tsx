import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
// The layout of the use-case chips this component comes from, kept as is so that their measures do not change
import { Box, Stack } from '@mui/material';

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
    <Stack
      ref={containerRef}
      direction="row"
      spacing={1}
      sx={{
        overflow: 'hidden',
        flexWrap: 'nowrap',
        position: 'relative',
        minWidth: 0,
      }}
    >
      {labels.map((label, index) => {
        const isVisible = index < visibleCount;
        return (
          <Box
            // two chips may carry the same label
            key={`${index}-${label}`}
            ref={(el: HTMLDivElement | null) => {
              chipRefs.current[index] = el;
            }}
            // Fixed-size chips, so the overflow count is measurable.
            sx={{
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
          </Box>
        );
      })}
      {/* Always mounted, so its width is known before anything is hidden; it is
          only taken out of the flow while there is nothing to count. */}
      <Box
        ref={overflowRef}
        sx={{
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
      </Box>
    </Stack>
  );
};

export default OverflowChips;
