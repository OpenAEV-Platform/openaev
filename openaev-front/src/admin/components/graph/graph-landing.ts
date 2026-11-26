// Pure helpers of the Graph landing: which view opens, how the estate overview sizes and
// colours its bubbles, how the starting-points rail groups its entries. No React here, so the
// rules are unit tested.

import {
  type GraphOverviewGroupOutput,
  type GraphOverviewLinkOutput,
  type GraphStartingPointOutput,
} from '../../../utils/api-types';
import { type GraphOutput, type GraphResult } from './graph-model';

export type LandingView = 'story' | 'overview';

export type StartingPointKind = GraphStartingPointOutput['point_kind'];

/** The rail's sections, in reading order: where am I exposed first, then what to look at next. */
export const STARTING_POINT_KINDS: StartingPointKind[] = [
  'EXPOSED_ASSET',
  'FAILING_GROUP',
  'ATTACK_PATH',
  'TOP_FINDING',
  'EXPOSED_PERSON',
  'LATEST_SIMULATION',
];

/** Catalog keys of the rail's section titles. */
export const STARTING_POINT_KIND_LABEL: Record<StartingPointKind, string> = {
  EXPOSED_ASSET: 'Exposed assets',
  FAILING_GROUP: 'Failing asset groups',
  ATTACK_PATH: 'Attack paths',
  TOP_FINDING: 'Top findings',
  EXPOSED_PERSON: 'Exposed persons',
  LATEST_SIMULATION: 'Latest simulation',
};

/** Smallest and largest bubble diameters of the estate overview, in px. */
export const BUBBLE_MIN = 72;
export const BUBBLE_MAX = 168;

/** The view a URL value names; anything else is no choice (the data decides). */
export const parseLandingView = (value: string | null | undefined): LandingView | undefined => (
  value === 'story' || value === 'overview' ? value : undefined
);

/** True when the story has something to tell: at least one node. */
export const storyHasContent = (story: GraphOutput | null | undefined): boolean => (story?.graph_nodes.length ?? 0) > 0;

/**
 * The view the landing shows: the one the URL names, else the story when it has content, else the
 * overview (nothing failed yet, so there is no chain to tell, only the estate to describe).
 */
export const landingView = (requested: LandingView | undefined, story: GraphOutput | null | undefined): LandingView => {
  if (requested) {
    return requested;
  }
  return storyHasContent(story) ? 'story' : 'overview';
};

/**
 * Bubble diameter by asset count: area proportional to the count (square root), between BUBBLE_MIN
 * and BUBBLE_MAX, relative to the largest group shown, so 40 groups of any size stay on one screen.
 */
export const bubbleDiameter = (count: number, maxCount: number): number => {
  if (maxCount <= 0 || count <= 0) {
    return BUBBLE_MIN;
  }
  const ratio = Math.sqrt(Math.min(count, maxCount) / maxCount);
  return Math.round(BUBBLE_MIN + (BUBBLE_MAX - BUBBLE_MIN) * ratio);
};

/**
 * The result colour of a group: FAILED from 20% of scored expectations failed, PENDING when some
 * failed below that, SUCCESS when scored with none failed, none when nothing was scored yet (a grey
 * bubble says "never tested", which is not the same as "safe").
 */
export const groupResult = (group: Pick<GraphOverviewGroupOutput, 'group_scored_count' | 'group_failed_count' | 'group_failure_rate'>): GraphResult | null => {
  const scored = group.group_scored_count ?? 0;
  const failed = group.group_failed_count ?? 0;
  if (scored <= 0) {
    return null;
  }
  if (failed <= 0) {
    return 'SUCCESS';
  }
  const rate = group.group_failure_rate ?? failed / scored;
  return rate >= 0.2 ? 'FAILED' : 'PENDING';
};

/** Failure rate as a whole percentage, for the bubble caption. */
export const failurePercent = (rate: number | null | undefined): number => Math.round(Math.max(0, Math.min(1, rate ?? 0)) * 100);

/** The groups worst first: highest failure rate, then most failures, then largest; the ungrouped bucket last. */
export const sortOverviewGroups = (groups: GraphOverviewGroupOutput[]): GraphOverviewGroupOutput[] => [...groups].sort((a, b) => {
  if (Boolean(a.group_ungrouped) !== Boolean(b.group_ungrouped)) {
    return a.group_ungrouped ? 1 : -1;
  }
  return (b.group_failure_rate ?? 0) - (a.group_failure_rate ?? 0)
    || (b.group_failed_count ?? 0) - (a.group_failed_count ?? 0)
    || (b.group_asset_count ?? 0) - (a.group_asset_count ?? 0)
    || a.group_name.localeCompare(b.group_name);
});

/** The simulations linked to each group, keyed by group id, in the order the API ranked them. */
export const linksByGroup = (links: GraphOverviewLinkOutput[]): Map<string, GraphOverviewLinkOutput[]> => {
  const byGroup = new Map<string, GraphOverviewLinkOutput[]>();
  links.forEach((link) => {
    byGroup.set(link.link_group_id, [...(byGroup.get(link.link_group_id) ?? []), link]);
  });
  return byGroup;
};

/** The rail's sections: one per kind that has entries, in STARTING_POINT_KINDS order, each in the API's rank order. */
export const groupStartingPoints = (points: GraphStartingPointOutput[]): {
  kind: StartingPointKind;
  points: GraphStartingPointOutput[];
}[] => STARTING_POINT_KINDS
  .map(kind => ({
    kind,
    points: points.filter(p => p.point_kind === kind),
  }))
  .filter(section => section.points.length > 0);
