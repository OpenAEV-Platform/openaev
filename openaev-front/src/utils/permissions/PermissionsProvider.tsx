import { type ReactNode, useMemo } from 'react';

import { type UserGrant } from '../api-types';
import { defineAbility } from './ability';
import { AbilityProvider } from './permissionsContext';

type PermissionsProviderProps = {
  capabilities: string[];
  grants: Record<string, UserGrant>;
  isAdmin: boolean;
  children: ReactNode;
};

// TODO : Delete isAdmin when we remove this logic
const PermissionsProvider = ({ capabilities, grants, isAdmin, children }: PermissionsProviderProps) => {
  const ability = useMemo(() => defineAbility(capabilities, grants, isAdmin), [capabilities, isAdmin]);
  return (
    <AbilityProvider value={ability}>
      {children}
    </AbilityProvider>
  );
};

export default PermissionsProvider;
