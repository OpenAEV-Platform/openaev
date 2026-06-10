import { composeWithDevTools } from '@redux-devtools/extension';
import { fromJS, isImmutable } from 'immutable';
import * as R from 'ramda';
import { useRef } from 'react';
import { useSelector } from 'react-redux';
import { applyMiddleware, createStore } from 'redux';
import { thunk } from 'redux-thunk';

import { storeHelper } from './actions/Schema';
import { entitiesInitializer } from './reducers/Referential';
import createRootReducer from './reducers/Root';

// Default application state
export const initialState = {
  app: fromJS({
    logged: {},
    worker: { status: 'RUNNING' },
  }),
  referential: entitiesInitializer,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
} as any;

const initStore = () => {
  if (process.env.NODE_ENV === 'development') {
    return createStore(
      createRootReducer(),
      initialState,
      composeWithDevTools(applyMiddleware(thunk)),
    );
  }
  return createStore(
    createRootReducer(),
    initialState,
    applyMiddleware(thunk),
  );
};

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const getJS = (selectorValue: any) => {
  if (!selectorValue) {
    return selectorValue;
  }

  if (isImmutable(selectorValue)) {
    return selectorValue.toJS();
  }
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const result: Record<string, any> = {};
  for (const key in selectorValue) {
    if (Object.prototype.hasOwnProperty.call(selectorValue, key)) {
      if (selectorValue[key] && isImmutable(selectorValue[key])) {
        result[key] = selectorValue[key].toJS();
      } else {
        result[key] = selectorValue[key];
      }
    }
  }
  return result;
};

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type UseHelperCache = { state: any, selector: any, result: any };

// TODO type selector object
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const useHelper = (selector: any) => {
  // Memoization keyed on BOTH the Immutable store identity and the selector identity: selectors
  // run on every dispatch for every subscriber, and getJS (Immutable.toJS) is expensive. The
  // fast-path only applies when neither changed (inline selector closures capturing fresh props
  // always recompute). When the output is deep-equal to the previous one we keep the previous
  // identity so useSelector's reference equality can skip the re-render.
  const cacheRef = useRef<UseHelperCache | null>(null);
  return useSelector(
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (state: any) => {
      const cache = cacheRef.current;
      if (cache && cache.state === state && cache.selector === selector) {
        return cache.result;
      }
      const computed = getJS(selector(storeHelper(state)));
      const result = cache && R.equals(computed, cache.result) ? cache.result : computed;
      cacheRef.current = {
        state,
        selector,
        result,
      };
      return result;
    },
  );
};

export const store = initStore();
