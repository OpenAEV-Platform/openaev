import { type AxiosError, type AxiosRequestConfig } from 'axios';
import { FORM_ERROR } from 'final-form';
import { type Schema } from 'normalizr';
import { type ErrorInfo } from 'react';
import { createIntl, createIntlCache } from 'react-intl';
import { type Dispatch } from 'redux';

import { LANG } from '../components/AppIntlProvider';
import * as Constants from '../constants/ActionTypes';
import { DATA_FETCH_ERROR } from '../constants/ActionTypes';
import { api } from '../network';
import { store } from '../store';
import { type ValidationErrorBag } from './api-types';
import { APP_BASE_PATH, MESSAGING$ } from './Environment';
import { notifyErrorHandler } from './error/errorHandlerUtil';
import { oaevLocaleMap } from './locales';
import { buildTenantApiPath } from './url-helper';

const cache = createIntlCache();

export const buildUri = (uri: string) => `${APP_BASE_PATH}${buildTenantApiPath(uri)}`;

// The interceptor of network.ts rejects with the response body: on a 400, the backend ValidationErrorBag
const buildError = (error: ValidationErrorBag) => {
  const children = error.errors?.children ?? {};
  return {
    ...Object.fromEntries(Object.entries(children).map(([field, child]) => [field, child?.errors?.[0]])),
    [FORM_ERROR]: error.message,
  };
};

const notifySuccess = (message: string) => {
  // Non-default locales are loaded lazily; fall back to the always-bundled english catalog
  // until the active locale chunk is available
  const messages = oaevLocaleMap[LANG as keyof typeof oaevLocaleMap] ?? oaevLocaleMap.en;
  const intl = createIntl({
    locale: LANG,
    messages,
  }, cache);

  if (messages[message]) {
    MESSAGING$.notifySuccess(intl.formatMessage({ id: message }));
  } else {
    MESSAGING$.notifySuccess(message);
  }
};

const checkUnauthorized = (error: AxiosError) => {
  if (error.status === 401) {
    store.dispatch({
      type: DATA_FETCH_ERROR,
      payload: error,
    });
  }
};

const simpleApi = api();

export const simpleCall = (uri: string, config?: AxiosRequestConfig, defaultErrorBehavior: boolean = true) => simpleApi.get(buildUri(uri), config).catch((error) => {
  checkUnauthorized(error);
  if (defaultErrorBehavior) {
    notifyErrorHandler(error);
  }
  throw error;
});
export const simplePostCall = (uri: string, data?: unknown, config?: AxiosRequestConfig, defaultNotifyErrorBehavior: boolean = true, defaultSuccessBehavior: boolean = false) =>
  simpleApi.post(buildUri(uri), data, config)
    .then((response) => {
      if (defaultSuccessBehavior) {
        notifySuccess('The element has been successfully created');
      }
      return response;
    })
    .catch((error) => {
      checkUnauthorized(error);
      if (defaultNotifyErrorBehavior) {
        notifyErrorHandler(error);
      }
      throw error;
    });
export const simplePutCall = (uri: string, data?: unknown, config?: AxiosRequestConfig, defaultNotifyErrorBehavior: boolean = true, defaultSuccessBehavior: boolean = true) =>
  simpleApi.put(buildUri(uri), data, config)
    .then((response) => {
      if (defaultSuccessBehavior) {
        notifySuccess('The element has been successfully updated');
      }
      return response;
    })
    .catch((error) => {
      checkUnauthorized(error);
      if (defaultNotifyErrorBehavior) {
        notifyErrorHandler(error);
      }
      throw error;
    });
export const simpleDelCall = (uri: string, config?: AxiosRequestConfig, defaultNotifyErrorBehavior: boolean = true, defaultSuccessBehavior: boolean = true) =>
  simpleApi.delete(buildUri(uri), config)
    .then((response) => {
      if (defaultSuccessBehavior) {
        notifySuccess('The element has been successfully deleted.');
      }
      return response;
    })
    .catch((error) => {
      checkUnauthorized(error);
      if (defaultNotifyErrorBehavior) {
        notifyErrorHandler(error);
      }
      throw error;
    });

export const getReferential = (schema: Schema, uri: string) => (dispatch: Dispatch) => {
  dispatch({ type: Constants.DATA_FETCH_SUBMITTED });
  return api(schema)
    .get(buildUri(uri))
    .then((response) => {
      dispatch({
        type: Constants.DATA_FETCH_SUCCESS,
        payload: response.data,
      });
      return response.data;
    })
    .catch((error) => {
      dispatch({
        type: Constants.DATA_FETCH_ERROR,
        payload: error,
      });
      notifyErrorHandler(error);
      throw error;
    });
};

export const putReferential = (schema: Schema, uri: string, data: unknown, defaultSuccessBehavior: boolean = true) => (dispatch: Dispatch) => {
  dispatch({ type: Constants.DATA_FETCH_SUBMITTED });
  return api(schema)
    .put(buildUri(uri), data)
    .then((response) => {
      dispatch({
        type: Constants.DATA_FETCH_SUCCESS,
        payload: response.data,
      });
      dispatch({
        type: Constants.DATA_UPDATE_SUCCESS,
        payload: response.data,
      });
      if (defaultSuccessBehavior) {
        notifySuccess('The element has been successfully updated');
      }
      return response.data;
    })
    .catch((error) => {
      dispatch({
        type: Constants.DATA_FETCH_ERROR,
        payload: error,
      });
      notifyErrorHandler(error);
      return buildError(error);
    });
};

export const postReferential = (schema: Schema | null, uri: string, data: unknown, config?: AxiosRequestConfig, defaultSuccessBehavior: boolean = true) => (dispatch: Dispatch) => {
  dispatch({ type: Constants.DATA_FETCH_SUBMITTED });
  return api(schema)
    .post(buildUri(uri), data, config)
    .then((response) => {
      dispatch({
        type: Constants.DATA_FETCH_SUCCESS,
        payload: response.data,
      });
      if (defaultSuccessBehavior) {
        notifySuccess('The element has been successfully created');
      }
      return response.data;
    })
    .catch((error) => {
      dispatch({
        type: Constants.DATA_FETCH_ERROR,
        payload: error,
      });
      notifyErrorHandler(error);
      return buildError(error);
    });
};

export const delSubResourceReferential = (schema: Schema | null, uri: string) => (dispatch: Dispatch) => {
  dispatch({ type: Constants.DATA_FETCH_SUBMITTED });
  return api(schema)
    .delete(buildUri(uri))
    .then((response) => {
      dispatch({
        type: Constants.DATA_FETCH_SUCCESS,
        payload: response.data,
      });
      notifySuccess('The element has been successfully updated');
      return response.data;
    })
    .catch((error) => {
      dispatch({
        type: Constants.DATA_FETCH_ERROR,
        payload: error,
      });
      notifyErrorHandler(error);
      throw error;
    });
};

export const delReferential = (uri: string, type: string, id: string) => (dispatch: Dispatch) => {
  dispatch({ type: Constants.DATA_FETCH_SUBMITTED });
  return api()
    .delete(buildUri(uri))
    .then((response) => {
      dispatch({
        type: Constants.DATA_DELETE_SUCCESS,
        payload: {
          type,
          id,
        },
      });
      notifySuccess('The element has been successfully deleted');
      return response.data;
    })
    .catch((error) => {
      dispatch({
        type: Constants.DATA_FETCH_ERROR,
        payload: error,
      });
      notifyErrorHandler(error);
      throw error;
    });
};

export const bulkDeleteReferential = (uri: string, type: string, data: unknown) => (dispatch: Dispatch) => {
  dispatch({ type: Constants.DATA_FETCH_SUBMITTED });
  return api()
    .delete(buildUri(uri), { data })
    .then((response) => {
      dispatch({
        type: Constants.DATA_DELETE_SUCCESS,
        payload: {
          type,
          data,
        },
      });
      return response.data;
    })
    .catch((error) => {
      dispatch({
        type: Constants.DATA_FETCH_ERROR,
        payload: error,
      });
      notifyErrorHandler(error);
      throw error;
    });
};

const OPENAEV_FRONTEND = '[OPENAEV-FRONTEND]';

export const sendErrorToBackend = async (error: Error, stack: ErrorInfo) => {
  const errorDetails = {
    message: OPENAEV_FRONTEND + error.message,
    stack: stack.componentStack,
    timestamp: new Date().toISOString(),
    level: 'SEVERE',
  };
  await simplePostCall('/api/logs', errorDetails);
};

export const sendErrorMessageToBackend = (message: string) => {
  const errorDetails = {
    message: OPENAEV_FRONTEND + message,
    timestamp: new Date().toISOString(),
    level: 'SEVERE',
  };
  simplePostCall('/api/logs', errorDetails, undefined, false).catch(() => {});
};
