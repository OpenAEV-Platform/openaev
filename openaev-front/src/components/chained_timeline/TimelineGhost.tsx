import { AddCircleOutline } from '@mui/icons-material';
import { Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { memo } from 'react';

import { RULER_HEIGHT } from './TimelineRuler';

interface Props {
  visible: boolean;
  /** Screen X within the playground wrapper. */
  x: number;
  /** Formatted time under the cursor. */
  label: string;
}

/**
 * The creation guide that follows the cursor over empty canvas: a dashed
 * vertical line snapped to the pointer plus a floating "+ time" chip.
 * Clicking the canvas creates an inject at that exact time (handled by the
 * orchestrator - this component is purely visual and never captures events).
 */
const TimelineGhostComponent = ({ visible, x, label }: Props) => {
  const theme = useTheme();
  if (!visible) {
    return null;
  }
  return (
    <div style={{
      position: 'absolute',
      top: RULER_HEIGHT,
      bottom: 0,
      left: x,
      zIndex: 5,
      pointerEvents: 'none',
    }}
    >
      <div style={{
        position: 'absolute',
        top: 0,
        bottom: 0,
        left: 0,
        width: 0,
        borderLeft: `1.5px dashed ${alpha(theme.palette.primary.main, 0.55)}`,
      }}
      />
      <div style={{
        position: 'absolute',
        top: 8,
        left: 8,
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        padding: '3px 8px',
        borderRadius: theme.shape.borderRadius,
        backgroundColor: alpha(theme.palette.background.paper, 0.9),
        border: `1px solid ${alpha(theme.palette.primary.main, 0.5)}`,
        boxShadow: `0 2px 8px ${alpha(theme.palette.common.black, 0.35)}`,
        whiteSpace: 'nowrap',
      }}
      >
        <AddCircleOutline sx={{
          fontSize: 15,
          color: theme.palette.primary.main,
        }}
        />
        <Typography sx={{
          fontSize: 11,
          color: theme.palette.text.primary,
          fontVariantNumeric: 'tabular-nums',
        }}
        >
          {label}
        </Typography>
      </div>
    </div>
  );
};

const TimelineGhost = memo(TimelineGhostComponent);
export default TimelineGhost;
