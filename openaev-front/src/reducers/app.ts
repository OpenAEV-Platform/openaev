import { Map as ImmutableMap } from 'immutable';

import { DATA_FETCH_ERROR, IDENTITY_LOGIN_SUCCESS, IDENTITY_LOGOUT_SUCCESS } from '../constants/ActionTypes';
import { type Actions, type DataFetchErrorAction, type IdentityLoginSuccessAction, type Logged } from '../store';
import initialState from './state';

const app = (state = initialState.app, action: Actions) => {
  switch (action.type) {
    case IDENTITY_LOGIN_SUCCESS: {
      const { data: user } = (action as IdentityLoginSuccessAction).payload;
      if (!user) {
        return state;
      }
      const logged: Logged = {
        user: user.user_id,
        lang: user.user_lang,
        theme: user.user_theme,
        admin: !!user.user_admin,
        isOnlyPlayer: !user.user_capabilities && !user.user_grants,
      };
      return state.set('logged', ImmutableMap<keyof Logged, Logged[keyof Logged]>(logged));
    }

    case DATA_FETCH_ERROR: {
      const { payload } = action as DataFetchErrorAction;
      if (payload.status === 401) {
        // If unauthorized, force logout
        return state.set('logged', ImmutableMap({}));
      }
      return state;
    }

    case IDENTITY_LOGOUT_SUCCESS: {
      return state.set('logged', ImmutableMap({}));
    }

    default: {
      return state;
    }
  }
};

export default app;
