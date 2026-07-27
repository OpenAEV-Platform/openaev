import { alpha, useTheme } from '@mui/material/styles';
import { type ReactFlowState, useStore } from '@xyflow/react';
import { memo } from 'react';
import { shallow } from 'zustand/shallow';

import { GAP_SIZE, MAJOR_EVERY } from './chronoUtils';

const selector = (s: ReactFlowState) => ({
  transform: s.transform,
  patternId: `timeline-grid-${s.rfId}`,
});

/**
 * The vertical time gridlines of the playground: a slightly stronger line on
 * every major (labeled) gridline and subtle lines on the minor gaps, both
 * theme-aware and following the pan/zoom transform.
 */
const TimelineGridComponent = () => {
  const theme = useTheme();
  const { transform, patternId } = useStore(selector, shallow);

  const zoom = transform[2] || 1;
  const scaledGap = GAP_SIZE * zoom;
  const patternWidth = scaledGap * MAJOR_EVERY;

  const majorColor = alpha(theme.palette.text.primary, 0.1);
  const minorColor = alpha(theme.palette.text.primary, 0.04);

  return (
    <svg
      className="react-flow__background"
      style={{
        position: 'absolute',
        width: '100%',
        height: '100%',
        top: 0,
        left: 0,
      }}
      data-testid="timeline-grid"
    >
      <pattern
        id={patternId}
        x={transform[0] % patternWidth}
        y={0}
        width={patternWidth}
        height={10}
        patternUnits="userSpaceOnUse"
      >
        <line x1={0} x2={0} y1={0} y2={10} stroke={majorColor} strokeWidth={1} />
        {Array.from({ length: MAJOR_EVERY - 1 }, (_, i) => (
          <line
            key={i}
            x1={(i + 1) * scaledGap}
            x2={(i + 1) * scaledGap}
            y1={0}
            y2={10}
            stroke={minorColor}
            strokeWidth={1}
          />
        ))}
      </pattern>
      <rect x="0" y="0" width="100%" height="100%" fill={`url(#${patternId})`} />
    </svg>
  );
};

const TimelineGrid = memo(TimelineGridComponent);
export default TimelineGrid;
