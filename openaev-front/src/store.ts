import { composeWithDevTools } from '@redux-devtools/extension';
import { is, isImmutable } from 'immutable';
import { useMemo } from 'react';
import { useSelector } from 'react-redux';
import { applyMiddleware, createStore } from 'redux';
import { thunk } from 'redux-thunk';

import { type ActionType, type DATA_DELETE_SUCCESS, type DATA_FETCH_ERROR, type DATA_FETCH_SUBMITTED, type DATA_FETCH_SUCCESS, type DATA_UPDATE_SUCCESS, type IDENTITY_LOGIN_FAILED, type IDENTITY_LOGIN_SUCCESS, type IDENTITY_LOGOUT_SUCCESS } from './constants/ActionTypes';
import { type EntitiesJS, type EntityKeys } from './reducers/entities';
import createRootReducer from './reducers/Root';
import initialState from './reducers/state';
import { type User } from './utils/api-types';

export type StoreState = typeof initialState;

interface Action {
  type: ActionType;
  payload: unknown;
  fromStream?: boolean;
}

export interface DataFetchSubmittedAction extends Action {
  type: typeof DATA_FETCH_SUBMITTED;
  payload: EntityKeys;
}

export interface DataFetchSuccessAction extends Action {
  type: typeof DATA_FETCH_SUCCESS;
  payload: {
    entities: Partial<EntitiesJS>;
    result: string | string[];
  };
}

export interface DataUpdateSuccessAction extends Action {
  type: typeof DATA_UPDATE_SUCCESS;
  payload: {
    entities: Partial<EntitiesJS> & { settings: Record<string, Record<string, unknown>> };
    result: string | string[];
  };
}

export interface DataDeleteSuccessAction extends Action {
  type: typeof DATA_DELETE_SUCCESS;
  payload: {
    id: string;
    type: EntityKeys;
  };
}

export interface DataFetchErrorAction extends Action {
  type: typeof DATA_FETCH_ERROR;
  payload: { status: number };
}

export interface IdentityLoginSuccessAction extends Action {
  type: typeof IDENTITY_LOGIN_SUCCESS;
  payload: { data: User };
}

export interface IdentityLoginFailedAction extends Action {
  type: typeof IDENTITY_LOGIN_FAILED;
  payload: { status: 'ERROR' };
}

export interface IdentityLogoutSuccessAction extends Action {
  type: typeof IDENTITY_LOGOUT_SUCCESS;
  payload: null;
}

export type Actions
  = DataFetchSubmittedAction
    | DataFetchSuccessAction
    | DataUpdateSuccessAction
    | DataDeleteSuccessAction
    | DataFetchErrorAction
    | IdentityLoginSuccessAction
    | IdentityLoginFailedAction
    | IdentityLogoutSuccessAction;

export interface Logged {
  user: string;
  lang?: string;
  theme?: string;
  admin: boolean;
  isOnlyPlayer: boolean;
}

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

export const useSelectorHelper = <T>(selector: ((state: StoreState) => { toJS(): T })) => {
  const selected = useSelector(selector, is);

  const jsValue = useMemo(() => {
    if (!selected || !isImmutable(selected)) {
      return selected as T;
    }
    return selected.toJS() as T;
  }, [selected]);

  return jsValue;
};

export const store = initStore();
