import { Map } from 'immutable';

import * as Constants from '../constants/ActionTypes';

const app = (state = Map({}), action = {}) => {
  switch (action.type) {
    case Constants.IDENTITY_LOGIN_SUCCESS: {
      const user = action.payload.entities.users[action.payload.result];
      const logged = {
        user: user.user_id,
        lang: user.user_lang,
        theme: user.user_theme,
        admin: user.user_admin,
        isOnlyPlayer:
            !user.user_capabilities && !user.user_grants,
      };
      return state.set('logged', logged);
    }

    case Constants.DATA_FETCH_ERROR: {
      if (action.payload.status === 401) {
        // If unauthorized, force logout
        return state.set('logged', null);
      }
      return state;
    }

    case Constants.IDENTITY_LOGOUT_SUCCESS: {
      return state.set('logged', null);
    }

    default: {
      return state;
    }
  }
};

export default app;
