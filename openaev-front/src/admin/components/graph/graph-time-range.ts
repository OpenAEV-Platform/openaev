import { type GraphNodeOutput } from './graph-model';

/**
 * The time range of the graph, as OpenCTI's GraphToolbarTimeRange: a two-thumb range over the dates
 * the nodes on screen carry (a simulation's start, a finding's last update). A node with a date
 * outside the range leaves the view; a node without a date (an asset, a group, a person) always
 * stays, so the range never cuts the structure the dated nodes hang on.
 */
export type TimeRange = [number, number];

const DAY_MS = 24 * 60 * 60 * 1000;

const timeOf = (node: Pick<GraphNodeOutput, 'node_date'>): number | undefined => {
  if (!node.node_date) {
    return undefined;
  }
  const time = Date.parse(node.node_date);
  return Number.isNaN(time) ? undefined : time;
};

/**
 * The first and last day the nodes carry, widened to whole days (UTC), or undefined when fewer than
 * two distinct days are present: a range over one day filters nothing and the control stays hidden.
 */
export const dateBounds = (nodes: Pick<GraphNodeOutput, 'node_date'>[]): TimeRange | undefined => {
  let min = Number.POSITIVE_INFINITY;
  let max = Number.NEGATIVE_INFINITY;
  nodes.forEach((node) => {
    const time = timeOf(node);
    if (time !== undefined) {
      min = Math.min(min, time);
      max = Math.max(max, time);
    }
  });
  if (!Number.isFinite(min)) {
    return undefined;
  }
  const start = Math.floor(min / DAY_MS) * DAY_MS;
  const end = Math.floor(max / DAY_MS) * DAY_MS + DAY_MS - 1;
  return end - start < 2 * DAY_MS - 1 ? undefined : [start, end];
};

/** Whether a node stays in view under the range: undated nodes always do. */
export const inTimeRange = (node: Pick<GraphNodeOutput, 'node_date'>, range?: TimeRange | null): boolean => {
  if (!range) {
    return true;
  }
  const time = timeOf(node);
  return time === undefined || (time >= range[0] && time <= range[1]);
};

/** The slider's step: one day. */
export const TIME_RANGE_STEP = DAY_MS;

/**
 * The range kept after new nodes arrive: a range the user never narrowed follows the new bounds; a
 * narrowed one is clamped to them (it never points outside the dates on screen).
 */
export const followBounds = (range: TimeRange | null | undefined, previous: TimeRange | undefined, next: TimeRange | undefined): TimeRange | null => {
  if (!range || !next) {
    return null;
  }
  if (previous && range[0] <= previous[0] && range[1] >= previous[1]) {
    return null;
  }
  const start = Math.min(Math.max(range[0], next[0]), next[1]);
  const end = Math.max(Math.min(range[1], next[1]), start);
  return [start, end];
};
