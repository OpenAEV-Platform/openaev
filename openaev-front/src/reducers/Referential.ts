import { fromJS, List as ImmutableList, Map as ImmutableMap } from 'immutable';

import { DATA_DELETE_SUCCESS, DATA_FETCH_ERROR, DATA_FETCH_SUBMITTED, DATA_FETCH_SUCCESS, DATA_UPDATE_SUCCESS, IDENTITY_LOGOUT_SUCCESS } from '../constants/ActionTypes';
import { type Actions, type DataDeleteSuccessAction, type DataFetchErrorAction, type DataFetchSuccessAction, type DataUpdateSuccessAction, type StoreState } from '../store';
import { entitiesInitializer, type EntityKeys } from './entities';
import initialState from './state';

const mergeDeepOverwriteLists = (a: unknown, b: unknown, deep = 0) => {
  // First, check if 'b' is null to avoid overwriting 'a', even if 'a' is mergeable.
  // Then, check if 'a' is mergeable.
  // Then, merge a is not a list & b is immutable then merge them otherwise return b
  if (!b) {
    return b;
  }
  if (deep < 1 && a && ImmutableMap.isMap(a)) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return a.mergeWith((c: unknown, d: unknown): any => mergeDeepOverwriteLists(c, d, deep + 1), b as Iterable<[string, unknown]>);
  }
  if (!ImmutableList.isList(a) && ImmutableMap.isMap(b)) {
    return (a as ImmutableMap<unknown, unknown>).merge(b);
  }
  return b;
};

const entityKeysToStoreFromStream = ['injects'] as EntityKeys[];

const referential = (state: StoreState['referential'] = initialState.referential, action: Actions) => {
  switch (action.type) {
    case DATA_FETCH_SUBMITTED: {
      // TODO check because this seems to be useless
      // const { payload } = action as DataFetchSubmittedAction;
      // if (payload) {
      //   return state.setIn(['entities', payload], ImmutableMap({}));
      // }
      return state;
    }
    case DATA_FETCH_SUCCESS: {
      const { payload } = action as DataFetchSuccessAction;
      const entityKey = Object.keys(payload.entities)[0] as EntityKeys;
      if (!action.fromStream || entityKeysToStoreFromStream.includes(entityKey)) {
        return state.updateIn(['entities', entityKey], map => mergeDeepOverwriteLists(map, fromJS(payload.entities[entityKey])));
      }
      return state;
    }
    case DATA_UPDATE_SUCCESS: {
      const { payload } = action as DataUpdateSuccessAction;
      const entityKey = Object.keys(payload.entities)[0] as EntityKeys;
      // TODO check that is still needed bceause merge deepfix that no ?
      if (payload.entities.settings) {
        const firstKey = Object.keys(payload.entities.settings)[0];
        const firstValue = payload.entities.settings[firstKey];
        return state.setIn(
          ['entities', 'platformParameters', 'parameters', firstValue['setting_key']],
          firstValue['setting_value'],
        );
      }
      return state.updateIn(['entities', entityKey], map => (map as ImmutableMap<string, unknown>).size ? mergeDeepOverwriteLists(map, fromJS(payload.entities[entityKey])) : map);
    }
    case DATA_DELETE_SUCCESS: {
      const { payload } = action as DataDeleteSuccessAction;
      if (state.entities[payload.type]) {
        return state.setIn(['entities', payload.type], state.entities[payload.type].delete(payload.id));
      }
      return state;
    }
    case DATA_FETCH_ERROR: {
      const { payload } = action as DataFetchErrorAction;
      if (payload.status === 401) {
        // If unauthorized, reset all entities except platform parameters.
        return entitiesInitializer.setIn(['entities', 'platformParameters'], state.entities.get('platformParameters'));
      }
      return state;
    }
    case IDENTITY_LOGOUT_SUCCESS: {
      // Upon logout, reset all entities except for platform parameters.
      return entitiesInitializer.setIn(['entities', 'platformParameters'], state.entities.get('platformParameters'));
    }
    default: {
      return state;
    }
  }
};

export default referential;
