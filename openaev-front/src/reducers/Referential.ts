import { fromJS, List, Map, OrderedMap } from 'immutable';
import * as R from 'ramda';

import * as Constants from '../constants/ActionTypes';

export const entitiesInitializer = Map({
  entities: Map({
    files: Map({}),
    users: Map({}),
    groups: Map({}),
    roles: Map({}),
    grants: Map({}),
    organizations: Map({}),
    tokens: Map({}),
    exercises: Map({}),
    objectives: Map({}),
    evaluations: Map({}),
    comchecks: Map({}),
    comcheckstatuses: Map({}),
    channelreaders: Map({}),
    simulationchallengesreaders: Map({}),
    teams: Map({}),
    injects: Map({}),
    atomics: Map({}),
    atomicdetails: Map({}),
    targetresults: Map({}),
    injector_contracts: Map({}),
    inject_statuses: Map({}),
    communications: Map({}),
    logs: Map({}),
    tags: Map({}),
    documents: Map({}),
    platformParameters: Map({}),
    publicPlatformParameters: Map({}),
    channels: Map({}),
    payloads: Map({}),
    challenges: Map({}),
    articles: Map({}),
    injectexpectations: Map({}),
    lessonstemplates: Map({}),
    lessonstemplatecategorys: Map({}),
    lessonstemplatequestions: Map({}),
    lessonscategorys: Map({}),
    lessonsquestions: Map({}),
    lessonsanswers: Map({}),
    variables: Map({}),
    killchainphases: Map({}),
    attackpatterns: Map({}),
    endpoints: Map({}),
    asset_groups: Map({}),
    securityplatforms: Map({}),
    scenarios: Map({}),
    injectors: Map({}),
    collectors: Map({}),
    executors: Map({}),
    mitigations: Map({}),
    agents: Map({}),
    domains: Map({}),
    catalog_connectors: Map({}),
    connector_instances: Map({}),
    platform_capabilities: Map({}),
    tenant_capabilities: Map({}),
    tenantXtmHubRegistrations: Map({}),
    notifications: Map({}),
  }),
});

export const ENTITY_SIZE_SOFT_CAP = 5000;
const EVICTABLE_ENTITY_TYPES = new Set([
  'injects', 'injectexpectations', 'inject_statuses',
  'communications', 'logs', 'targetresults',
  'comcheckstatuses', 'channelreaders', 'simulationchallengesreaders',
]);

// High-growth entity maps are kept bounded with an LRU window. Immutable's plain
// `Map` iterates in hash order (not insertion order), so `takeLast` on a plain
// `Map` would keep an arbitrary subset and could evict an entity that is still
// on screen and actively receiving updates. Instead we keep these maps as an
// `OrderedMap` and move every key touched by the current action to the
// most-recently-used end; once a map exceeds the soft cap we drop the
// least-recently-used entries from the front. This guarantees the entries being
// read/updated right now (e.g. an inject receiving a burst of SSE events) are
// never the ones evicted.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const applyLruEviction = (state: any, action: any): any => {
  const payloadEntities = action.payload?.entities;
  if (!payloadEntities) return state;

  const entities = state.get('entities');
  if (!entities || !Map.isMap(entities)) return state;

  let result = state;
  Object.keys(payloadEntities).forEach((entityType: string) => {
    if (!EVICTABLE_ENTITY_TYPES.has(entityType)) return;
    const currentMap = entities.get(entityType);
    if (!currentMap || !Map.isMap(currentMap)) return;

    // Encode recency in iteration order so eviction is deterministic and safe.
    let lruMap = OrderedMap.isOrderedMap(currentMap) ? currentMap : OrderedMap(currentMap);
    Object.keys(payloadEntities[entityType] ?? {}).forEach((key: string) => {
      if (lruMap.has(key)) {
        // delete + re-set moves the key to the MRU end of the OrderedMap.
        const value = lruMap.get(key);
        lruMap = lruMap.delete(key).set(key, value);
      }
    });

    if (lruMap.size > ENTITY_SIZE_SOFT_CAP) {
      lruMap = lruMap.takeLast(ENTITY_SIZE_SOFT_CAP);
    }

    if (lruMap !== currentMap) {
      result = result.setIn(['entities', entityType], lruMap);
    }
  });
  return result;
};

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const mergeDeepOverwriteLists = (a: any, b: any, deep = 0) => {
  // First, check if 'b' is null to avoid overwriting 'a', even if 'a' is mergeable.
  // Then, check if 'a' is mergeable.
  // Then, merge a is not a list & b is immutable then merge them otherwise return b
  if (!b) {
    return b;
  }
  if (deep < 3 && a && Map.isMap(a)) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return a.mergeWith((c: any, d: any): any => mergeDeepOverwriteLists(c, d, deep + 1), b);
  }
  if (!List.isList(a) && Map.isMap(b)) {
    return a.merge(b);
  }
  return b;
};

/**
 * Entity keys that are platform-scoped.
 * These are preserved when switching tenant so the user session is not lost.
 */
const PLATFORM_ENTITIES = [
  'platformParameters',
  'users',
  'tokens',
  'groups',
  'roles',
  'grants',
  'organizations',
  'capabilities',
] as const;

// Some chaining screens read workflow configuration from the synthetic
// `workflowconfigurations` entity keyed by workflow id. SSE updates for workflows
// are emitted as `workflows`, so mirror those fields to keep the configuration
// view live without a manual page reload.
type WorkflowLike = {
  workflow_rate_limit_enabled?: boolean;
  workflow_max_attempts?: number;
  workflow_max_temporal_rate_seconds?: number;
  workflow_timeout_enabled?: boolean;
  workflow_timeout_seconds?: number;
  workflow_safe_mode_enabled?: boolean;
  workflow_scope_rules?: unknown[];
  workflow_scope_variables?: unknown[];
};

type ReferentialPayload = {
  entities?: {
    workflows?: Record<string, WorkflowLike>;
    workflowconfigurations?: Record<string, unknown>;
  };
  [key: string]: unknown;
};

const withWorkflowConfigurations = (payload: ReferentialPayload) => {
  const workflowEntities = payload?.entities?.workflows;
  if (!workflowEntities) {
    return payload;
  }

  const workflowConfigurations = Object.fromEntries(
    Object.entries(workflowEntities).map(([workflowId, workflow]) => [
      workflowId,
      {
        workflow_configuration_rate_limit_enabled: workflow.workflow_rate_limit_enabled,
        workflow_configuration_max_attempts: workflow.workflow_max_attempts,
        workflow_configuration_max_temporal_rate_seconds:
          workflow.workflow_max_temporal_rate_seconds,
        workflow_configuration_timeout_enabled: workflow.workflow_timeout_enabled,
        workflow_configuration_timeout_seconds: workflow.workflow_timeout_seconds,
        workflow_configuration_safe_mode_enabled: workflow.workflow_safe_mode_enabled,
        workflow_scope_rules: workflow.workflow_scope_rules ?? [],
        workflow_scope_variables: workflow.workflow_scope_variables ?? [],
      },
    ]),
  );

  return R.assocPath(
    ['entities', 'workflowconfigurations'],
    {
      ...workflowConfigurations,
      ...(payload.entities.workflowconfigurations ?? {}),
    },
    payload,
  );
};

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const referential = (state: any = Map({}), action: any = {}) => {
  switch (action.type) {
    case Constants.DATA_UPDATE_SUCCESS:
    case Constants.DATA_FETCH_SUCCESS: {
      if (action.payload?.entities?.settings) {
        const firstKey = Object.keys(action.payload.entities.settings)[0];
        const firstValue = action.payload.entities.settings[firstKey];
        return state.setIn(
          ['entities', 'platformParameters', 'parameters', firstValue['setting_key']],
          firstValue['setting_value'],
        );
      } else {
        const payload = withWorkflowConfigurations(action.payload);
        const merged = mergeDeepOverwriteLists(state, fromJS(R.dissoc('result', payload)));
        return applyLruEviction(merged, {
          ...action,
          payload,
        });
      }
    }
    case Constants.DATA_DELETE_SUCCESS: {
      const toDeleteIn = state.getIn(['entities', action.payload.type]);
      if (toDeleteIn) {
        return state.setIn(
          ['entities', action.payload.type],
          state.getIn(['entities', action.payload.type]).delete(action.payload.id),
        );
      }
      return state;
    }
    // Batched form used by the SSE pipeline: the whole delete backlog is
    // applied in a single reducer pass / subscriber notification instead of
    // one dispatch per deleted entity. Payload: Array<{ id, type }>.
    case Constants.DATA_DELETE_BATCH_SUCCESS: {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      return (action.payload ?? []).reduce((acc: any, { id, type }: {
        id: string;
        type: string;
      }) => {
        const entityMap = acc.getIn(['entities', type]);
        if (entityMap) {
          return acc.setIn(['entities', type], entityMap.delete(id));
        }
        return acc;
      }, state);
    }
    case Constants.DATA_FETCH_ERROR: {
      if (action.payload.status === 401) {
        // If unauthorized, reset all entities except platform parameters.
        return entitiesInitializer.setIn(['entities', 'publicPlatformParameters'], state.getIn(['entities', 'publicPlatformParameters']));
      }
      return state;
    }

    case Constants.TENANT_SWITCH_SUCCESS: {
      // Reset all tenant-scoped entities but preserve platform-scoped entities
      let nextState = entitiesInitializer;
      for (const key of PLATFORM_ENTITIES) {
        nextState = nextState.setIn(['entities', key], state.getIn(['entities', key]));
      }
      return nextState;
    }

    case Constants.IDENTITY_LOGOUT_SUCCESS: {
      // Upon logout, reset all entities except for platform parameters.
      return entitiesInitializer.setIn(['entities', 'publicPlatformParameters'], state.getIn(['entities', 'publicPlatformParameters']));
    }
    default: {
      return state;
    }
  }
};

export default referential;
