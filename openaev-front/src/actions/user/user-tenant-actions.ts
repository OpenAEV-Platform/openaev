import { simpleCall } from '../../utils/Action';

const ME_TENANTS_URI = '/api/me/tenants';

export const fetchUserTenants = () => {
  return simpleCall(ME_TENANTS_URI);
};
