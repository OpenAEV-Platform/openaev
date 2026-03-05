import { useCallback, useState } from 'react';

import { DEFAULT_TENANT_UUID, searchTenantUsers } from '../../../../../actions/tenant/users/user-tenant-action';
import type { SearchPaginationInput, UserOutput } from '../../../../../utils/api-types';

const useTenantUsers = (tenantId: string = DEFAULT_TENANT_UUID) => {
  const [tenantUsers, setTenantUsers] = useState<UserOutput[]>([]);
  const [loading, setLoading] = useState(true);

  const setTenantUserList = useCallback((users: UserOutput[]) => {
    setTenantUsers(users);
  }, []);

  const fetchTenantUsers = useCallback(
    async (input: SearchPaginationInput) => {
      setLoading(true);
      try {
        return await searchTenantUsers(tenantId, input);
      } finally {
        setLoading(false);
      }
    },
    [tenantId],
  );

  const addTenantUser = useCallback((user: UserOutput) => {
    setTenantUsers(prev => [user, ...prev]);
  }, []);

  const updateTenantUserInList = useCallback((user: UserOutput) => {
    setTenantUsers(prev =>
      prev.map(u => u.user_id === user.user_id ? user : u),
    );
  }, []);

  const removeTenantUser = useCallback((userId: string) => {
    setTenantUsers(prev => prev.filter(u => u.user_id !== userId));
  }, []);

  return {
    tenantUsers,
    setTenantUserList,
    loading,
    fetchTenantUsers,
    addTenantUser,
    updateTenantUserInList,
    removeTenantUser,
  };
};

export default useTenantUsers;

