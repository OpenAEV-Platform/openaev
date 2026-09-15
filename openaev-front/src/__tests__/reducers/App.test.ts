import { type Map as ImmutableMap } from 'immutable';
import { Map } from 'immutable';
import { describe, expect, it } from 'vitest';

import * as Constants from '../../constants/ActionTypes';
import app from '../../reducers/App';

// 'App.js' is untyped JS: TS infers its state param/return type from the 'Map({})' default,
// which is too narrow for these tests. Re-type it loosely instead of littering every call
// with casts.
const appReducer = app as unknown as (
  state: ImmutableMap<string, unknown>,
  action: unknown,
) => ImmutableMap<string, unknown>;

describe('app reducer DATA_FETCH_ERROR', () => {
  it('logs the user out on a 401 and clears a stale tenantAccessDenied flag', () => {
    const state = Map({
      logged: { user: 'u1' },
      tenantAccessDenied: true,
    });
    const next = appReducer(state, {
      type: Constants.DATA_FETCH_ERROR,
      payload: { status: 401 },
    });
    expect(next.get('logged')).toBeNull();
    expect(next.get('tenantAccessDenied')).toBe(false);
  });

  it('clears the stuck loading placeholder and flags tenant access denied on a 403 TENANT_ACCESS_DENIED', () => {
    // Initial app state before '/me' resolves (see store.ts initialState).
    const state = Map({ logged: {} });
    const next = appReducer(state, {
      type: Constants.DATA_FETCH_ERROR,
      payload: {
        status: 403,
        message: 'TENANT_ACCESS_DENIED',
      },
    });
    expect(next.get('logged')).toBeNull();
    expect(next.get('tenantAccessDenied')).toBe(true);
  });

  it('flags tenant access denied when the error shape is axios-like (nested response)', () => {
    const state = Map({ logged: {} });
    const next = appReducer(state, {
      type: Constants.DATA_FETCH_ERROR,
      payload: {
        response: {
          status: '403',
          data: {
            message: 'TENANT_ACCESS_DENIED',
          },
        },
      },
    });
    expect(next.get('logged')).toBeNull();
    expect(next.get('tenantAccessDenied')).toBe(true);
  });

  it('leaves the state untouched for other 403s (e.g. the generic CSRF retry one)', () => {
    const state = Map({ logged: { user: 'u1' } });
    const next = appReducer(state, {
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
    const next = appReducer(state, {
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
    const next = appReducer(state, {
      type: Constants.IDENTITY_LOGIN_SUCCESS,
      payload: loginPayload,
    });
    expect(next.get('tenantAccessDenied')).toBe(false);
    expect((next.get('logged') as { user: string }).user).toBe('u1');
  });

  it('resets tenantAccessDenied on logout', () => {
    const state = Map({
      tenantAccessDenied: true,
      logged: { user: 'u1' },
    });
    const next = appReducer(state, { type: Constants.IDENTITY_LOGOUT_SUCCESS });
    expect(next.get('logged')).toBeNull();
    expect(next.get('tenantAccessDenied')).toBe(false);
  });
});
