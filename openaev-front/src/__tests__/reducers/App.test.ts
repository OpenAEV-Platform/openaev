import { Map } from 'immutable';
import { describe, expect, it } from 'vitest';

import * as Constants from '../../constants/ActionTypes';
import app from '../../reducers/App';

describe('app reducer DATA_FETCH_ERROR', () => {
  it('logs the user out on a 401', () => {
    const state = Map({ logged: { user: 'u1' } });
    const next = app(state, {
      type: Constants.DATA_FETCH_ERROR,
      payload: { status: 401 },
    });
    expect(next.get('logged')).toBeNull();
  });

  it('clears the stuck loading placeholder and flags tenant access denied on a 403 TENANT_ACCESS_DENIED', () => {
    // Initial app state before '/me' resolves (see store.ts initialState).
    const state = Map({ logged: {} });
    const next = app(state, {
      type: Constants.DATA_FETCH_ERROR,
      payload: {
        status: 403,
        message: 'TENANT_ACCESS_DENIED',
      },
    });
    expect(next.get('logged')).toBeNull();
    expect(next.get('tenantAccessDenied')).toBe(true);
  });

  it('leaves the state untouched for other 403s (e.g. the generic CSRF retry one)', () => {
    const state = Map({ logged: { user: 'u1' } });
    const next = app(state, {
      type: Constants.DATA_FETCH_ERROR,
      payload: {
        status: 403,
        message: 'Forbidden',
      },
    });
    expect(next).toBe(state);
    expect(next.get('tenantAccessDenied')).toBeUndefined();
  });

  it('leaves the state untouched for other statuses', () => {
    const state = Map({ logged: { user: 'u1' } });
    const next = app(state, {
      type: Constants.DATA_FETCH_ERROR,
      payload: { status: 500 },
    });
    expect(next).toBe(state);
  });
});

describe('app reducer IDENTITY_LOGIN_SUCCESS / IDENTITY_LOGOUT_SUCCESS', () => {
  const loginPayload = {
    entities: {
      users: {
        u1: {
          user_id: 'u1',
          user_lang: 'auto',
          user_theme: 'dark',
          user_admin: false,
          user_capabilities: ['CAP'],
        },
      },
    },
    result: 'u1',
  };

  it('resets tenantAccessDenied on a successful login (e.g. after switching to a valid tenant)', () => {
    const state = Map({ tenantAccessDenied: true });
    const next = app(state, {
      type: Constants.IDENTITY_LOGIN_SUCCESS,
      payload: loginPayload,
    });
    expect(next.get('tenantAccessDenied')).toBe(false);
    expect(next.get('logged').user).toBe('u1');
  });

  it('resets tenantAccessDenied on logout', () => {
    const state = Map({
      tenantAccessDenied: true,
      logged: { user: 'u1' },
    });
    const next = app(state, { type: Constants.IDENTITY_LOGOUT_SUCCESS });
    expect(next.get('logged')).toBeNull();
    expect(next.get('tenantAccessDenied')).toBe(false);
  });
});
