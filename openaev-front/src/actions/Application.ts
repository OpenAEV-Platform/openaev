import { FORM_ERROR } from 'final-form';
import { type Dispatch } from 'redux';

import * as Constants from '../constants/ActionTypes';
import { getReferential, postReferential, putReferential, simpleCall } from '../utils/Action';
import type { PolicyInput, SettingsEnterpriseEditionUpdateInput, SettingsPlatformWhitemarkUpdateInput, SettingsUpdateInput, ThemeInput, User } from '../utils/api-types';
import * as schema from './Schema';

interface ResetValues {
  password: string;
  password_validation: string;
}

interface LoginData {
  login: string;
  password?: string;
  lang?: string;
}

type AppDispatch = Dispatch;

export const fetchPlatformParameters = () => (dispatch: AppDispatch) => {
  return getReferential(schema.platformParameters, '/api/settings')(dispatch);
};

export const updatePlatformParameters = (data: SettingsUpdateInput) => (dispatch: AppDispatch) => {
  return putReferential(
    schema.platformParameters,
    '/api/settings',
    data,
  )(dispatch);
};

export const updatePlatformPolicies = (data: PolicyInput) => (dispatch: AppDispatch) => {
  return putReferential(
    schema.platformParameters,
    '/api/settings/policies',
    data,
  )(dispatch);
};

export const updatePlatformEnterpriseEditionParameters = (data: SettingsEnterpriseEditionUpdateInput) => (dispatch: AppDispatch) => {
  return putReferential(
    schema.platformParameters,
    '/api/settings/enterprise-edition',
    data,
  )(dispatch);
};

export const updatePlatformWhitemarkParameters = (data: SettingsPlatformWhitemarkUpdateInput) => (dispatch: AppDispatch) => {
  return putReferential(
    schema.platformParameters,
    '/api/settings/platform_whitemark',
    data,
  )(dispatch);
};

export const updatePlatformLightParameters = (data: ThemeInput) => (dispatch: AppDispatch) => {
  return putReferential(
    schema.platformParameters,
    '/api/settings/theme/light',
    data,
  )(dispatch);
};

export const updatePlatformDarkParameters = (data: ThemeInput) => (dispatch: AppDispatch) => {
  return putReferential(
    schema.platformParameters,
    '/api/settings/theme/dark',
    data,
  )(dispatch);
};

export const askReset = (username: string, locale: string) => (dispatch: AppDispatch) => {
  const data: LoginData = {
    login: username,
    lang: locale,
  };
  return postReferential(schema.user, '/api/reset', data)(dispatch);
};

export const resetPassword = (token: string, values: ResetValues) => (dispatch: AppDispatch) => {
  const data = {
    password: values.password,
    password_validation: values.password_validation,
  };
  const ref = postReferential(
    schema.user,
    `/api/reset/${token}`,
    data,
  )(dispatch);
  return ref.then((finalData: Record<string, unknown>) => {
    if (finalData[FORM_ERROR]) {
      return finalData;
    }
    return dispatch({
      type: Constants.IDENTITY_LOGIN_SUCCESS,
      payload: finalData,
    });
  });
};

export const validateResetToken = (token: string) => () => {
  return simpleCall(`/api/reset/${token}`);
};

export const askToken = (username: string, password: string) => (dispatch: AppDispatch) => {
  const data: LoginData = {
    login: username,
    password,
  };
  const ref = postReferential(schema.user, '/api/login', data)(dispatch);
  return ref.then((finalData: Record<string, unknown>) => {
    if (finalData[FORM_ERROR]) {
      return finalData;
    }
    return dispatch({
      type: Constants.IDENTITY_LOGIN_SUCCESS,
      payload: finalData,
    });
  });
};

export const checkKerberos = () => (dispatch: AppDispatch) => {
  const ref = getReferential(schema.token, '/api/auth/kerberos')(dispatch);
  return ref.catch(() => {
    dispatch({
      type: Constants.IDENTITY_LOGIN_FAILED,
      payload: { status: 'ERROR' },
    });
  });
};

export const fetchMe = () => (dispatch: AppDispatch) => {
  const ref = getReferential(schema.user, '/api/me')(dispatch);
  return ref.then((data: User) => dispatch({
    type: Constants.IDENTITY_LOGIN_SUCCESS,
    payload: data,
  }));
};

export const logout = () => (dispatch: AppDispatch) => {
  const ref = simpleCall('/logout');
  return ref.then(() => dispatch({ type: Constants.IDENTITY_LOGOUT_SUCCESS }));
};
