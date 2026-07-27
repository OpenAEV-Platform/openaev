import { Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ReactFlowState, useStore } from '@xyflow/react';
import moment from 'moment-timezone';
import { memo, useEffect, useState } from 'react';
import { shallow } from 'zustand/shallow';

import { useFormatter } from '../i18n';
import { secondsToFlowX } from './chronoUtils';
import { RULER_HEIGHT } from './TimelineRuler';

const selector = (s: ReactFlowState) => ({
  transform: s.transform,
  width: s.width,
});

interface Props {
  minutesPerGap: number;
  startDate?: string;
}

/**
 * A live "now" line on the playground - only when the timeline is anchored to
 * a real start date that is already in the past (a running/finished
 * simulation), so the operator sees at a glance which injects are behind or
 * ahead of the current moment.
 */
const TimelineNowMarkerComponent = ({ minutesPerGap, startDate }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { transform, width } = useStore(selector, shallow);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const interval = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(interval);
  }, []);

  if (startDate === undefined) {
    return null;
  }
  const startMs = moment.utc(startDate).valueOf();
  if (Number.isNaN(startMs) || now < startMs) {
    return null;
  }

  const zoom = transform[2] || 1;
  const x = transform[0] + secondsToFlowX((now - startMs) / 1000, minutesPerGap) * zoom;
  if (x < 0 || x > width) {
    return null;
  }

  const color = theme.palette.warning.main;

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
        width: 1.5,
        backgroundColor: alpha(color, 0.7),
        boxShadow: `0 0 6px ${alpha(color, 0.5)}`,
      }}
      />
      <Typography sx={{
        position: 'absolute',
        top: 4,
        left: 5,
        fontSize: 10,
        fontWeight: 600,
        letterSpacing: '0.08em',
        textTransform: 'uppercase',
        color,
        whiteSpace: 'nowrap',
      }}
      >
        {t('Now')}
      </Typography>
    </div>
  );
};

const TimelineNowMarker = memo(TimelineNowMarkerComponent);
export default TimelineNowMarker;
