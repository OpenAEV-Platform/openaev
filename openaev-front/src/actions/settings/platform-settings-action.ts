import type { Dispatch } from 'redux';

import { getReferential } from '../../utils/Action';
import publicPlatformParameters from './platform-settings-schema';

const SETTINGS_URI = '/api/settings';

// -- PUBLIC (unauthenticated) --

const fetchPublicPlatformParameters = () => (dispatch: Dispatch) => {
  return getReferential(publicPlatformParameters, `${SETTINGS_URI}/public`)(dispatch);
};

export default fetchPublicPlatformParameters;
