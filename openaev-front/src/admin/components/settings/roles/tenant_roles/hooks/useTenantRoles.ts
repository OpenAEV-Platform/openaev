import { useCallback, useState } from 'react';

import { searchRoles } from '../../../../../../actions/roles/roles-actions';
import { type RoleOutput, type SearchPaginationInput } from '../../../../../../utils/api-types';

const useTenantRoles = () => {
  const [roles, setRoles] = useState<RoleOutput[]>([]);
  const [loading, setLoading] = useState(true);

  const setRoleList = useCallback((data: RoleOutput[]) => {
    setRoles(data);
  }, []);

  const fetchRoles = useCallback(
    async (input: SearchPaginationInput) => {
      setLoading(true);
      try {
        return await searchRoles(input);
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  const addRole = useCallback((role: RoleOutput) => {
    setRoles(prev => [role, ...prev]);
  }, []);

  const updateRoleInList = useCallback((role: RoleOutput) => {
    setRoles(prev => prev.map(r => r.role_id === role.role_id ? role : r));
  }, []);

  const removeRole = useCallback((roleId: string) => {
    setRoles(prev => prev.filter(r => r.role_id !== roleId));
  }, []);

  return {
    roles,
    setRoleList,
    loading,
    fetchRoles,
    addRole,
    updateRoleInList,
    removeRole,
  };
};

export default useTenantRoles;
