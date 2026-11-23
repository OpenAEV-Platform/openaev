import { describe, expect, it } from 'vitest';

import {
  BUBBLE_MAX,
  BUBBLE_MIN,
  bubbleDiameter,
  failurePercent,
  groupResult,
  groupStartingPoints,
  landingView,
  linksByGroup,
  parseLandingView,
  sortOverviewGroups,
  storyHasContent,
} from '../../../../admin/components/graph/graph-landing';
import { type GraphOutput } from '../../../../admin/components/graph/graph-model';
import { type GraphOverviewGroupOutput, type GraphStartingPointOutput } from '../../../../utils/api-types';

const emptyStory: GraphOutput = {
  graph_nodes: [],
  graph_edges: [],
  graph_truncated: false,
  graph_path: [],
};

const story: GraphOutput = {
  ...emptyStory,
  graph_nodes: [{
    node_id: 'ASSET:a1',
    node_type: 'ASSET',
    node_entity_id: 'a1',
    node_label: 'srv-01',
    node_failed_count: 3,
    node_finding_count: 1,
  }],
};

const group = (id: string, extra: Partial<GraphOverviewGroupOutput> = {}): GraphOverviewGroupOutput => ({
  group_id: id,
  group_name: id,
  ...extra,
});

describe('landing view', () => {
  it('reads only the two known views from the URL', () => {
    expect(parseLandingView('story')).toBe('story');
    expect(parseLandingView('overview')).toBe('overview');
    expect(parseLandingView('everything')).toBeUndefined();
    expect(parseLandingView(null)).toBeUndefined();
  });

  it('opens on the story when it has content, else on the overview', () => {
    expect(storyHasContent(story)).toBe(true);
    expect(storyHasContent(emptyStory)).toBe(false);
    expect(landingView(undefined, story)).toBe('story');
    expect(landingView(undefined, emptyStory)).toBe('overview');
    expect(landingView(undefined, null)).toBe('overview');
  });

  it('keeps the view the URL names, so a shared link restores it', () => {
    expect(landingView('overview', story)).toBe('overview');
    expect(landingView('story', emptyStory)).toBe('story');
  });
});

describe('bubbleDiameter', () => {
  it('stays between the bounds and grows with the square root of the count', () => {
    expect(bubbleDiameter(0, 100)).toBe(BUBBLE_MIN);
    expect(bubbleDiameter(100, 0)).toBe(BUBBLE_MIN);
    expect(bubbleDiameter(100, 100)).toBe(BUBBLE_MAX);
    expect(bubbleDiameter(500, 100)).toBe(BUBBLE_MAX);
    expect(bubbleDiameter(25, 100)).toBe(Math.round(BUBBLE_MIN + (BUBBLE_MAX - BUBBLE_MIN) * 0.5));
  });

  it('keeps 100K assets and a group of one on the same screen', () => {
    expect(bubbleDiameter(1, 100000)).toBeGreaterThanOrEqual(BUBBLE_MIN);
    expect(bubbleDiameter(100000, 100000)).toBeLessThanOrEqual(BUBBLE_MAX);
  });
});

describe('groupResult', () => {
  it('says never tested when nothing was scored', () => {
    expect(groupResult({ group_scored_count: 0 })).toBeNull();
    expect(groupResult({})).toBeNull();
  });

  it('colours by failure rate', () => {
    expect(groupResult({
      group_scored_count: 10,
      group_failed_count: 0,
    })).toBe('SUCCESS');
    expect(groupResult({
      group_scored_count: 10,
      group_failed_count: 1,
      group_failure_rate: 0.1,
    })).toBe('PENDING');
    expect(groupResult({
      group_scored_count: 10,
      group_failed_count: 2,
      group_failure_rate: 0.2,
    })).toBe('FAILED');
  });

  it('computes the rate when the API leaves it out', () => {
    expect(groupResult({
      group_scored_count: 4,
      group_failed_count: 3,
    })).toBe('FAILED');
  });

  it('formats the failure rate as a bounded whole percentage', () => {
    expect(failurePercent(0.456)).toBe(46);
    expect(failurePercent(2)).toBe(100);
    expect(failurePercent(undefined)).toBe(0);
  });
});

describe('sortOverviewGroups', () => {
  it('puts the worst groups first and the ungrouped bucket last', () => {
    const sorted = sortOverviewGroups([
      group('ungrouped', {
        group_ungrouped: true,
        group_failure_rate: 0.9,
      }),
      group('calm', {
        group_failure_rate: 0,
        group_asset_count: 500,
      }),
      group('hot', { group_failure_rate: 0.8 }),
      group('warm-big', {
        group_failure_rate: 0.3,
        group_failed_count: 30,
      }),
      group('warm-small', {
        group_failure_rate: 0.3,
        group_failed_count: 3,
      }),
    ]);
    expect(sorted.map(g => g.group_id)).toEqual(['hot', 'warm-big', 'warm-small', 'calm', 'ungrouped']);
  });
});

describe('linksByGroup', () => {
  it('groups the simulation links by asset group in API order', () => {
    const byGroup = linksByGroup([
      {
        link_group_id: 'g1',
        link_simulation_id: 's1',
        link_simulation_name: 'S1',
      },
      {
        link_group_id: 'g2',
        link_simulation_id: 's2',
        link_simulation_name: 'S2',
      },
      {
        link_group_id: 'g1',
        link_simulation_id: 's3',
        link_simulation_name: 'S3',
      },
    ]);
    expect(byGroup.get('g1')?.map(l => l.link_simulation_id)).toEqual(['s1', 's3']);
    expect(byGroup.get('g2')?.length).toBe(1);
    expect(byGroup.get('g3')).toBeUndefined();
  });
});

describe('groupStartingPoints', () => {
  const point = (kind: GraphStartingPointOutput['point_kind'], id: string): GraphStartingPointOutput => ({
    point_kind: kind,
    point_entity_id: id,
    point_label: id,
    point_node_type: 'ASSET',
  });

  it('orders the sections by reading order, drops the empty ones and keeps the API rank', () => {
    const sections = groupStartingPoints([
      point('LATEST_SIMULATION', 'sim'),
      point('EXPOSED_ASSET', 'a2'),
      point('TOP_FINDING', 'f1'),
      point('EXPOSED_ASSET', 'a1'),
    ]);
    expect(sections.map(s => s.kind)).toEqual(['EXPOSED_ASSET', 'TOP_FINDING', 'LATEST_SIMULATION']);
    expect(sections[0].points.map(p => p.point_entity_id)).toEqual(['a2', 'a1']);
  });

  it('returns no section when there is nothing to start from', () => {
    expect(groupStartingPoints([])).toEqual([]);
  });
});
