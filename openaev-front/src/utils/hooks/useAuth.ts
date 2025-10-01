import { createContext, useContext } from 'react';

import { type PlatformSettings, type User } from '../api-types';

export interface UserContextType {
  me: User | undefined;
  settings: PlatformSettings | undefined;
  isXTMHubAccessible: boolean | undefined;
}

const defaultContext = {
  me: undefined,
  settings: undefined,
  isXTMHubAccessible: undefined,
};
export const UserContext = createContext<UserContextType>(defaultContext);

const useAuth = () => {
  const { me, settings, isXTMHubAccessible } = useContext(UserContext);
  if (!me || !settings) {
    throw new Error('Invalid user context !');
  }
  return {
    me,
    settings,
    isXTMHubAccessible,
  };
};

export default useAuth;
