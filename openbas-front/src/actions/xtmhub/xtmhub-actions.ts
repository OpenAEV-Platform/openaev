import type { Dispatch } from 'redux';

import { postReferential, putReferential } from '../../utils/Action';
import * as schema from '../Schema';

const XTM_HUB_URI = '/api/xtmhub';

export const registerPlatform = (token: string) => (dispatch: Dispatch) => {
  const uri = `${XTM_HUB_URI}/register`;
  return putReferential(
    schema.platformParameters,
    uri,
    { token },
    false,
  )(dispatch);
};

export const unregisterPlatform = () => (dispatch: Dispatch) => {
  const uri = `${XTM_HUB_URI}/unregister`;
  return putReferential(
    schema.platformParameters,
    uri,
    {},
    false,
  )(dispatch);
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
