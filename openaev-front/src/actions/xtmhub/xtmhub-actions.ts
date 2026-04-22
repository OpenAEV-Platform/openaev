import { normalize } from 'normalizr';
import type { Dispatch } from 'redux';

import * as Constants from '../../constants/ActionTypes';
import { store } from '../../store';
import { putReferential, simpleCall, simplePostCall, simplePutCall } from '../../utils/Action';
import * as schema from '../Schema';

const XTM_HUB_URI = '/api/xtmhub';

const clearStaleRegistrations = (dispatch: Dispatch) => {
  const stale = store.getState().referential.getIn(['entities', 'tenantXtmHubRegistrations']);
  if (stale) {
    stale.keySeq().forEach((id: string) => {
      dispatch({
        type: Constants.DATA_DELETE_SUCCESS,
        payload: {
          type: 'tenantXtmHubRegistrations',
          id,
        },
      });
    });
  }
};

const handleRegistrationResponse = (dispatch: Dispatch) => (response: {
  status: number;
  data: unknown;
}) => {
  if (response.status === 204 || !response.data) {
    clearStaleRegistrations(dispatch);
  } else {
    dispatch({
      type: Constants.DATA_FETCH_SUCCESS,
      payload: normalize(response.data, schema.tenantXtmHubRegistration),
    });
  }
};

const handleRegistrationError = (dispatch: Dispatch) => (error: unknown) => {
  dispatch({
    type: Constants.DATA_FETCH_ERROR,
    payload: error,
  });
};

export const fetchXtmHubRegistration = () => (dispatch: Dispatch) => {
  const uri = `${XTM_HUB_URI}/registration`;
  dispatch({ type: Constants.DATA_FETCH_SUBMITTED });
  return simpleCall(uri, undefined, false)
    .then(handleRegistrationResponse(dispatch))
    .catch(handleRegistrationError(dispatch));
};

export const registerPlatform = (token: string) => (dispatch: Dispatch) => {
  const uri = `${XTM_HUB_URI}/register`;
  return putReferential(
    schema.tenantXtmHubRegistration,
    uri,
    { token },
    false,
  )(dispatch);
};

export const unregisterPlatform = (registrationId: string) => (dispatch: Dispatch) => {
  const uri = `${XTM_HUB_URI}/unregister`;
  return simplePutCall(uri, {}, undefined, false, false).then(() => {
    dispatch({
      type: Constants.DATA_DELETE_SUCCESS,
      payload: {
        type: 'tenantXtmHubRegistrations',
        id: registrationId,
      },
    });
  });
};

export const refreshConnectivity = () => (dispatch: Dispatch) => {
  const uri = `${XTM_HUB_URI}/refresh-connectivity`;
  dispatch({ type: Constants.DATA_FETCH_SUBMITTED });
  return simplePostCall(uri, {}, undefined, true, false)
    .then(handleRegistrationResponse(dispatch))
    .catch(handleRegistrationError(dispatch));
};
