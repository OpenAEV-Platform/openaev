import { simpleCall } from '../../utils/Action';

const ME_TENANTS_URI = '/api/me/tenants';

// eslint-disable-next-line import/prefer-default-export
export const fetchUserTenants = () => {
  return simpleCall(ME_TENANTS_URI);
};
