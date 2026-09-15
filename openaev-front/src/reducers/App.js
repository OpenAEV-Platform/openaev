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
      return state.set('logged', logged).set('tenantAccessDenied', false);
    }

    case Constants.DATA_FETCH_ERROR: {
      if (action.payload.status === 401) {
        // If unauthorized, force logout. Also clear a stale tenantAccessDenied so an
        // expired session doesn't get stuck behind the denial screen.
        return state.set('logged', null).set('tenantAccessDenied', false);
      }
      if (action.payload.status === 403 && action.payload.message === 'TENANT_ACCESS_DENIED') {
        // The tenant in the URL is not one the current user belongs to: stop waiting on
        // '/me' (which would otherwise leave 'logged' stuck at its initial placeholder
        // forever, i.e. a permanent blank screen) and surface a dedicated alert instead.
        return state.set('logged', null).set('tenantAccessDenied', true);
      }
      return state;
    }

    case Constants.IDENTITY_LOGOUT_SUCCESS: {
      return state.set('logged', null).set('tenantAccessDenied', false);
    }

    default: {
      return state;
    }
  }
};

export default app;
