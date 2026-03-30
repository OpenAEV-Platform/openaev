import { useCallback, useState } from 'react';

import { searchTenants } from '../../../../../actions/platform/tenants/tenant-action';
import { type SearchPaginationInput, type TenantOutput } from '../../../../../utils/api-types';

const useTenants = () => {
  const [tenants, setTenants] = useState<TenantOutput[]>([]);
  const [loading, setLoading] = useState(true);

  const setTenantList = useCallback((tenants: TenantOutput[]) => {
    setTenants(tenants);
  }, []);

  const fetchTenants = useCallback(
    async (input: SearchPaginationInput) => {
      setLoading(true);
      try {
        return await searchTenants(input);
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  const addTenant = useCallback((tenant: TenantOutput) => {
    setTenants(prev => [tenant, ...prev]);
  }, []);

  const updateTenant = useCallback((tenant: TenantOutput) => {
    setTenants(prev =>
      prev.map(t =>
        t.tenant_id === tenant.tenant_id ? tenant : t,
      ),
    );
  }, []);

  const removeTenant = useCallback((tenantId: string) => {
    setTenants(prev => prev.filter(t => t.tenant_id !== tenantId));
  }, []);

  return {
    tenants,
    setTenantList,
    loading,
    fetchTenants,
    addTenant,
    updateTenant,
    removeTenant,
  };
};

export default useTenants;
