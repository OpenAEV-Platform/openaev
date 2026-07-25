import { splitDuration } from '../../utils/Time';

/**
 * Shared time/space math for the chained timeline: one gridline "gap" is a
 * fixed number of flow pixels; the active time scale decides how many minutes
 * that gap represents. Major (labeled) gridlines sit every MAJOR_EVERY gaps.
 */

// Flow pixels between two vertical gridlines.
export const GAP_SIZE = 125;

// Inject card metrics shared by the node component and the orchestrator's
// row-packing auto-layout.
export const NODE_WIDTH = 270;
export const NODE_HEIGHT_CLEARANCE = 170;
export const NODE_WIDTH_CLEARANCE = NODE_WIDTH + 60;

// A labeled (major) gridline every N gaps; the gaps in between are minor ticks.
export const MAJOR_EVERY = 3;

export interface TimeScale {
  /** Minutes represented by one gap. */
  minutesPerGap: number;
  /** Short human label for the interval between two MAJOR gridlines. */
  label: string;
}

// The four supported zoom scales: a major gridline every 15 minutes, 1 hour,
// 12 hours or 1 day (minutesPerGap * MAJOR_EVERY).
export const TIME_SCALES: TimeScale[] = [
  {
    minutesPerGap: 5,
    label: '15m',
  },
  {
    minutesPerGap: 20,
    label: '1h',
  },
  {
    minutesPerGap: 240,
    label: '12h',
  },
  {
    minutesPerGap: 480,
    label: '1d',
  },
];

/** Convert a flow-space X coordinate to seconds since the timeline origin. */
export const flowXToSeconds = (x: number, minutesPerGap: number) => {
  return Math.max(0, Math.round((x / GAP_SIZE) * minutesPerGap * 60));
};

/** Convert seconds since the timeline origin to a flow-space X coordinate. */
export const secondsToFlowX = (seconds: number, minutesPerGap: number) => {
  return (seconds / 60) * (GAP_SIZE / minutesPerGap);
};

/** "3 d, 4 h, 05 m" style relative label used across the timeline. */
export const formatRelativeTime = (seconds: number) => {
  const duration = splitDuration(Math.max(0, seconds));
  return `${Number(duration.days)} d, ${Number(duration.hours)} h, ${Number(duration.minutes)} m`;
};
