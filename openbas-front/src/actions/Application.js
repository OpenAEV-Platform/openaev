import { FORM_ERROR } from 'final-form';

import * as Constants from '../constants/ActionTypes';
import { getReferential, postReferential, putReferential, simpleCall } from '../utils/Action';
import * as schema from './Schema';

export const fetchPlatformParameters = () => (dispatch) => {
  return getReferential(schema.platformParameters, '/api/settings')(dispatch);
};

export const updatePlatformParameters = data => (dispatch) => {
  return putReferential(
    schema.platformParameters,
    '/api/settings',
    data,
  )(dispatch);
};

export const updatePlatformPolicies = data => (dispatch) => {
  return putReferential(
    schema.platformParameters,
    '/api/settings/policies',
    data,
  )(dispatch);
};

export const updatePlatformEnterpriseEditionParameters = data => (dispatch) => {
  return putReferential(
    schema.platformParameters,
    '/api/settings/enterprise-edition',
    data,
  )(dispatch);
};

export const updatePlatformWhitemarkParameters = data => (dispatch) => {
  return putReferential(
    schema.platformParameters,
    '/api/settings/platform_whitemark',
    data,
  )(dispatch);
};

export const updatePlatformLightParameters = data => (dispatch) => {
  return putReferential(
    schema.platformParameters,
    '/api/settings/theme/light',
    data,
  )(dispatch);
};

export const updatePlatformDarkParameters = data => (dispatch) => {
  return putReferential(
    schema.platformParameters,
    '/api/settings/theme/dark',
    data,
  )(dispatch);
};

export const askReset = (username, locale) => (dispatch) => {
  const data = {
    login: username,
    lang: locale,
  };
  return postReferential(schema.user, '/api/reset', data)(dispatch);
};

export const resetPassword = (token, values) => (dispatch) => {
  const data = {
    password: values.password,
    password_validation: values.password_validation,
  };
  const ref = postReferential(
    schema.user,
    `/api/reset/${token}`,
    data,
  )(dispatch);
  return ref.then((finalData) => {
    if (finalData[FORM_ERROR]) {
      return finalData;
    }
    return dispatch({
      type: Constants.IDENTITY_LOGIN_SUCCESS,
      payload: finalData,
    });
  });
};

export const validateResetToken = token => (dispatch) => {
  return getReferential(null, `/api/reset/${token}`)(dispatch);
};

export const askToken = (username, password) => (dispatch) => {
  const data = {
    login: username,
    password,
  };
  const ref = postReferential(schema.user, '/api/login', data)(dispatch);
  return ref.then((finalData) => {
    if (finalData[FORM_ERROR]) {
      return finalData;
    }
    return dispatch({
      type: Constants.IDENTITY_LOGIN_SUCCESS,
      payload: finalData,
    });
  });
};

export const checkKerberos = () => (dispatch) => {
  const ref = getReferential(schema.token, '/api/auth/kerberos')(dispatch);
  return ref.catch(() => {
    dispatch({
      type: Constants.IDENTITY_LOGIN_FAILED,
      payload: { status: 'ERROR' },
    });
  });
};

export const fetchMe = () => (dispatch) => {
  const ref = getReferential(schema.user, '/api/me')(dispatch);
  return ref.then(data => dispatch({
    type: Constants.IDENTITY_LOGIN_SUCCESS,
    payload: data,
  }));
};

export const logout = () => (dispatch) => {
  const ref = simpleCall('/logout');
  return ref.then(() => dispatch({ type: Constants.IDENTITY_LOGOUT_SUCCESS }));
};
