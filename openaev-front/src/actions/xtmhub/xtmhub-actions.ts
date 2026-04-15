import type { Dispatch } from 'redux';

import * as Constants from '../../constants/ActionTypes';
import { store } from '../../store';
import { getReferential, postReferential, putReferential, simplePutCall } from '../../utils/Action';
import * as schema from '../Schema';

const XTM_HUB_URI = '/api/xtmhub';

export const fetchXtmHubRegistration = () => (dispatch: Dispatch) => {
  const uri = `${XTM_HUB_URI}/registration`;
  return getReferential(schema.tenantXtmHubRegistration, uri)(dispatch)
    .then((data) => {
      if (!data) {
        // Not registered — clear any stale entries left in the store
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
      }
    })
    .catch(() => {
      // error already dispatched and notified by getReferential
    });
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
  return postReferential(
    schema.platformParameters,
    uri,
    {},
    undefined,
    false,
  )(dispatch);
};
