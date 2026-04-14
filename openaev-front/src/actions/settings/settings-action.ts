import { simpleCall } from '../../utils/Action';

export const SETTINGS_URI = '/api/settings';

export const fetchCalderaSettings = () => {
  return simpleCall(`${SETTINGS_URI}/caldera`);
};
