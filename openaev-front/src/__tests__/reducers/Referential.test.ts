import { Map, OrderedMap } from 'immutable';
import { describe, expect, it } from 'vitest';

import referential, { DATA_DELETE_BATCH_SUCCESS } from '../../constants/ActionTypes';
import referential, { applyLruEviction, ENTITY_SIZE_SOFT_CAP } from '../../reducers/Referential';

const buildState = (entityType: string, entries: [string, unknown][], ordered = true) =>
  Map({ entities: Map({ [entityType]: ordered ? OrderedMap(entries) : Map(entries) }) });

const fetchAction = (entityType: string, touchedKeys: string[]) => ({
  type: 'DATA_FETCH_SUCCESS',
  payload: { entities: { [entityType]: Object.fromEntries(touchedKeys.map(key => [key, { id: key }])) } },
});

describe('applyLruEviction', () => {
  it('moves keys touched by the action to the most-recently-used end', () => {
    const state = buildState('injects', [['a', 1], ['b', 2], ['c', 3]]);
    const result = applyLruEviction(state, fetchAction('injects', ['a']));
    expect(result.getIn(['entities', 'injects']).keySeq().toArray()).toEqual(['b', 'c', 'a']);
  });

  it('bumps a touched key to the end even when the source map is a plain (hash-ordered) Map', () => {
    const state = buildState('injects', [['a', 1], ['b', 2], ['c', 3]], false);
    const result = applyLruEviction(state, fetchAction('injects', ['a']));
    const injects = result.getIn(['entities', 'injects']);
    expect(injects.size).toBe(3);
    expect(injects.keySeq().last()).toBe('a');
  });

  it('keeps a frequently-updated early entry and evicts the least-recently-used ones over the cap', () => {
    const entries: [string, number][] = [['active', 0]];
    for (let i = 1; i <= ENTITY_SIZE_SOFT_CAP; i += 1) {
      entries.push([`e${i}`, i]);
    }
    // 'active' was inserted first but is touched by the current action, so it
    // must survive eviction (this is the exact "active view" regression).
    const state = buildState('injects', entries);
    const result = applyLruEviction(state, fetchAction('injects', ['active']));
    const injects = result.getIn(['entities', 'injects']);
    expect(injects.size).toBe(ENTITY_SIZE_SOFT_CAP);
    expect(injects.has('active')).toBe(true);
    expect(injects.has('e1')).toBe(false);
    expect(injects.has(`e${ENTITY_SIZE_SOFT_CAP}`)).toBe(true);
  });

  it('does not evict non-evictable entity types even when over the cap', () => {
    const entries: [string, number][] = [];
    for (let i = 0; i <= ENTITY_SIZE_SOFT_CAP; i += 1) {
      entries.push([`u${i}`, i]);
    }
    const state = buildState('users', entries);
    const result = applyLruEviction(state, fetchAction('users', ['u0']));
    expect(result.getIn(['entities', 'users']).size).toBe(ENTITY_SIZE_SOFT_CAP + 1);
  });

  it('leaves maps under the cap intact in size while refreshing recency', () => {
    const state = buildState('logs', [['x', 1], ['y', 2]]);
    const result = applyLruEviction(state, fetchAction('logs', ['x']));
    const logs = result.getIn(['entities', 'logs']);
    expect(logs.size).toBe(2);
    expect(logs.keySeq().toArray()).toEqual(['y', 'x']);
  });

  it('returns the state unchanged when the action has no entities payload', () => {
    const state = buildState('injects', [['a', 1]]);
    const result = applyLruEviction(state, {
      type: 'DATA_FETCH_SUCCESS',
      payload: {},
    });
    expect(result).toBe(state);
  });
});

describe('referential DATA_DELETE_BATCH_SUCCESS', () => {
  it('applies a whole delete backlog in a single reducer pass', () => {
    const state = Map({
      entities: Map({
        injects: Map({
          a: 1,
          b: 2,
        }),
        logs: Map({ x: 1 }),
      }),
    });
    const next = referential(state, {
      type: DATA_DELETE_BATCH_SUCCESS,
      payload: [
        {
          id: 'a',
          type: 'injects',
        },
        {
          id: 'x',
          type: 'logs',
        },
        // Unknown entity type must be ignored, not crash.
        {
          id: 'z',
          type: 'unknown_type',
        },
      ],
    });
    expect(next.getIn(['entities', 'injects']).has('a')).toBe(false);
    expect(next.getIn(['entities', 'injects']).has('b')).toBe(true);
    expect(next.getIn(['entities', 'logs']).size).toBe(0);
  });

  it('returns the state unchanged for an empty batch', () => {
    const state = Map({ entities: Map({ injects: Map({ a: 1 }) }) });
    const next = referential(state, {
      type: DATA_DELETE_BATCH_SUCCESS,
      payload: [],
    });
    expect(next).toBe(state);
  });
});

describe('referential', () => {
  it('mirrors workflow stream updates to workflowconfigurations', () => {
    const state = referential(undefined, {
      type: 'DATA_UPDATE_SUCCESS',
      payload: {
        entities: {
          workflows: {
            workflow1: {
              workflow_rate_limit_enabled: true,
              workflow_max_attempts: 5,
              workflow_max_temporal_rate_seconds: 30,
              workflow_timeout_enabled: true,
              workflow_timeout_seconds: 60,
              workflow_safe_mode_enabled: false,
              workflow_scope_rules: [{ workflow_scope_rule_id: 'rule-1' }],
              workflow_scope_variables: [{ scope_variable_id: 'var-1' }],
            },
          },
        },
      },
    });

    expect(state.getIn(['entities', 'workflowconfigurations', 'workflow1']).toJS()).toEqual({
      workflow_configuration_rate_limit_enabled: true,
      workflow_configuration_max_attempts: 5,
      workflow_configuration_max_temporal_rate_seconds: 30,
      workflow_configuration_timeout_enabled: true,
      workflow_configuration_timeout_seconds: 60,
      workflow_configuration_safe_mode_enabled: false,
      workflow_scope_rules: [{ workflow_scope_rule_id: 'rule-1' }],
      workflow_scope_variables: [{ scope_variable_id: 'var-1' }],
    });
  });

  it('keeps explicit workflowconfigurations payload when both are present', () => {
    const state = referential(undefined, {
      type: 'DATA_UPDATE_SUCCESS',
      payload: {
        entities: {
          workflows: {
            workflow1: {
              workflow_max_attempts: 5,
              workflow_timeout_enabled: true,
            },
          },
          workflowconfigurations: {
            workflow1: {
              workflow_configuration_max_attempts: 8,
              workflow_configuration_timeout_enabled: false,
            },
          },
        },
      },
    });

    expect(state.getIn(['entities', 'workflowconfigurations', 'workflow1']).toJS()).toEqual({
      workflow_configuration_max_attempts: 8,
      workflow_configuration_timeout_enabled: false,
    });
  });
});
