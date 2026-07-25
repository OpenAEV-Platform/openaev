import { alpha, useTheme } from '@mui/material/styles';
import { type ReactFlowState, useStore } from '@xyflow/react';
import moment from 'moment-timezone';
import { memo } from 'react';
import { shallow } from 'zustand/shallow';

import { useFormatter } from '../i18n';
import { formatRelativeTime, GAP_SIZE, MAJOR_EVERY } from './chronoUtils';

export const RULER_HEIGHT = 32;

const selector = (s: ReactFlowState) => ({
  transform: s.transform,
  width: s.width,
});

interface Props {
  minutesPerGap: number;
  startDate?: string;
}

/**
 * The sticky time strip at the top of the playground: a labeled (major)
 * gridline every MAJOR_EVERY gaps with small minor ticks in between. Labels
 * are absolute dates when the timeline is anchored to a start date, relative
 * "d, h, m" offsets otherwise. Rendered in screen space so it never zooms.
 */
const TimelineRulerComponent = ({ minutesPerGap, startDate }: Props) => {
  const theme = useTheme();
  const { fld, ft, vnsdt } = useFormatter();
  const { transform, width } = useStore(selector, shallow);

  const zoom = transform[2] || 1;
  const scaledGap = GAP_SIZE * zoom;
  const majorGap = scaledGap * MAJOR_EVERY;

  // First major index visible on the left edge (never before the origin).
  const firstIndex = Math.max(0, Math.floor(-transform[0] / majorGap));
  const count = Math.ceil(width / majorGap) + 2;

  const majors = [];
  for (let i = 0; i < count; i += 1) {
    const index = firstIndex + i;
    const x = transform[0] + index * majorGap;
    if (x < -majorGap || x > width + majorGap) continue;
    const minutes = index * minutesPerGap * MAJOR_EVERY;
    let label;
    if (startDate === undefined) {
      label = formatRelativeTime(minutes * 60);
    } else {
      const date = moment.utc(startDate).add(minutes, 'm').toDate();
      label = zoom > 0.5 ? `${fld(date)} - ${ft(date)}` : vnsdt(date);
    }
    majors.push({
      index,
      x,
      label,
    });
  }

  // Minor ticks: every gap that is not a major gridline.
  const firstMinorIndex = Math.max(0, Math.floor(-transform[0] / scaledGap));
  const minorCount = Math.ceil(width / scaledGap) + 2;
  const minors = [];
  for (let i = 0; i < minorCount; i += 1) {
    const index = firstMinorIndex + i;
    if (index % MAJOR_EVERY === 0) continue;
    const x = transform[0] + index * scaledGap;
    if (x < 0 || x > width) continue;
    minors.push({
      index,
      x,
    });
  }

  return (
    <div
      style={{
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        height: RULER_HEIGHT,
        zIndex: 6,
        pointerEvents: 'none',
        backgroundColor: alpha(theme.palette.background.default, 0.85),
        backdropFilter: 'blur(6px)',
        borderBottom: `1px solid ${theme.palette.divider}`,
      }}
    >
      <svg style={{
        width: '100%',
        height: '100%',
      }}
      >
        {minors.map(minor => (
          <line
            key={`minor-${minor.index}`}
            x1={minor.x}
            x2={minor.x}
            y1={RULER_HEIGHT - 6}
            y2={RULER_HEIGHT}
            stroke={alpha(theme.palette.text.primary, 0.25)}
            strokeWidth={1}
          />
        ))}
        {majors.map(major => (
          <g key={`major-${major.index}`}>
            <line
              x1={major.x}
              x2={major.x}
              y1={RULER_HEIGHT - 12}
              y2={RULER_HEIGHT}
              stroke={alpha(theme.palette.text.primary, 0.5)}
              strokeWidth={1}
            />
            <text
              x={major.x + 6}
              y={13}
              fill={theme.palette.text.secondary}
              fontSize={10}
              fontFamily={theme.typography.fontFamily}
              style={{ fontVariantNumeric: 'tabular-nums' }}
            >
              {major.label}
            </text>
          </g>
        ))}
      </svg>
    </div>
  );
};

const TimelineRuler = memo(TimelineRulerComponent);
export default TimelineRuler;
