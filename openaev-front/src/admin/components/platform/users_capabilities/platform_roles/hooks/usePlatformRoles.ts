import { useCallback, useState } from 'react';

import { searchPlatformRoles } from '../../../../../../actions/platform/platform-role/platform-role-action';
import type { PlatformRoleOutput, SearchPaginationInput } from '../../../../../../utils/api-types';

const usePlatformRoles = () => {
  const [platformRoles, setPlatformRoles] = useState<PlatformRoleOutput[]>([]);
  const [loading, setLoading] = useState(true);

  const setPlatformRoleList = useCallback((roles: PlatformRoleOutput[]) => {
    setPlatformRoles(roles);
  }, []);

  const fetchPlatformRoles = useCallback(
    async (input: SearchPaginationInput) => {
      setLoading(true);
      try {
        return await searchPlatformRoles(input);
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  const addPlatformRole = useCallback((role: PlatformRoleOutput) => {
    setPlatformRoles(prev => [role, ...prev]);
  }, []);

  const updatePlatformRoleInList = useCallback((role: PlatformRoleOutput) => {
    setPlatformRoles(prev =>
      prev.map(r =>
        r.platform_role_id === role.platform_role_id ? role : r,
      ),
    );
  }, []);

  const removePlatformRole = useCallback((roleId: string) => {
    setPlatformRoles(prev => prev.filter(r => r.platform_role_id !== roleId));
  }, []);

  return {
    platformRoles,
    setPlatformRoleList,
    loading,
    fetchPlatformRoles,
    addPlatformRole,
    updatePlatformRoleInList,
    removePlatformRole,
  };
};

export default usePlatformRoles;
