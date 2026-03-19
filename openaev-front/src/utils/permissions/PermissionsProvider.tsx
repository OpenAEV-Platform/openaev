import { type ReactNode, useMemo } from 'react';

import { defineAbility } from './ability';
import { AbilityContext } from './permissionsContext';

type PermissionsProviderProps = {
  capabilities: string[];
  grants: Record<string, string>;
  isAdmin: boolean;
  children: ReactNode;
};

// TODO : Delete isAdmin when we remove this logic
const PermissionsProvider = ({ capabilities, grants, isAdmin, children }: PermissionsProviderProps) => {
  const ability = useMemo(() => defineAbility(capabilities, grants, isAdmin), [capabilities, isAdmin]);
  return (
    <AbilityContext.Provider value={ability}>
      {children}
    </AbilityContext.Provider>
  );
};

export default PermissionsProvider;
