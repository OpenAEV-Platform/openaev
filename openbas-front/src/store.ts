import { composeWithDevTools } from '@redux-devtools/extension';
import { fromJS, isImmutable } from 'immutable';
import * as R from 'ramda';
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

// TODO type selector object
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const useHelper = (selector: any) => {
  return useSelector(
    state => getJS(selector(storeHelper(state))),
    R.equals, // deep-equality to avoid re-renders when structurally equal
  );
};

export const store = initStore();
