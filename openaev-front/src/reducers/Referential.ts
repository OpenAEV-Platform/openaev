import { fromJS, List, Map } from 'immutable';
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
    reports: Map({}),
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
  }),
});

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
        return mergeDeepOverwriteLists(state, fromJS(R.dissoc('result', action.payload)));
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
    case Constants.DATA_FETCH_ERROR: {
      if (action.payload.status === 401) {
        // If unauthorized, reset all entities except platform parameters.
        return entitiesInitializer.setIn(['entities', 'platformParameters'], state.getIn(['entities', 'platformParameters']));
      }
      return state;
    }

    case Constants.IDENTITY_LOGOUT_SUCCESS: {
      // Upon logout, reset all entities except for platform parameters.
      return entitiesInitializer.setIn(['entities', 'platformParameters'], state.getIn(['entities', 'platformParameters']));
    }
    default: {
      return state;
    }
  }
};

export default referential;
