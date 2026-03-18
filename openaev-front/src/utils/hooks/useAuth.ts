import { createContext, useContext } from 'react';

import { type PlatformSettings, type TenantOutput, type User } from '../api-types';

export interface UserContextType {
  me: User | undefined;
  settings: PlatformSettings | undefined;
  isXTMHubAccessible: boolean | undefined;
  userTenants: TenantOutput[];
  currentUserTenant: TenantOutput | null;
  switchUserTenant: (tenantId: string) => Promise<void>;
}

const defaultContext: UserContextType = {
  me: undefined,
  settings: undefined,
  isXTMHubAccessible: undefined,
  userTenants: [],
  currentUserTenant: null,
  switchUserTenant: async (_tenantId: string) => {},
};
export const UserContext = createContext<UserContextType>(defaultContext);

const useAuth = () => {
  const { me, settings, isXTMHubAccessible, userTenants, currentUserTenant, switchUserTenant } = useContext(UserContext);
  if (!me || !settings) {
    throw new Error('Invalid user context !');
  }
  return {
    me,
    settings,
    isXTMHubAccessible,
    userTenants,
    currentUserTenant,
    switchUserTenant,
  };
};

export default useAuth;
