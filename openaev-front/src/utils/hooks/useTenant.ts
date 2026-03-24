import { useCallback, useEffect, useState } from 'react';
import { useLocalStorage } from 'usehooks-ts';

import { fetchUserTenants } from '../../actions/user/user-tenant-actions';
import { type TenantOutput, type User } from '../api-types';

/**
 * Hook that manages the full tenant lifecycle:
 * - Fetches the tenants accessible to the current user
 * - Persists the selected tenant in local storage
 * - Provides a switch function to change the active tenant
 */
const useTenant = (me: User | undefined, logged: unknown) => {
  const [userTenants, setUserTenants] = useState<TenantOutput[]>([]);
  const [currentTenantStorage, setCurrentTenantStorage] = useLocalStorage<TenantOutput | null>('current-tenant-storage', null);
  const [currentUserTenant, setCurrentUserTenant] = useState<TenantOutput | null>(null);

  const loadUserTenants = useCallback(async () => {
    if (!me) return;

    const response = await fetchUserTenants();
    const tenants: TenantOutput[] = response.data;

    if (tenants && tenants.length > 0) {
      setUserTenants(tenants);
      // If local storage tenant is still valid use it, otherwise switch to first tenant in list
      const currentTenant = tenants.find(tenant => (tenant.tenant_id === currentTenantStorage?.tenant_id));
      if (currentTenant) {
        setCurrentUserTenant(currentTenant);
        setCurrentTenantStorage(currentTenant);
      } else {
        setCurrentUserTenant(tenants[0]);
        setCurrentTenantStorage(tenants[0]);
      }
    } else {
      setUserTenants([]);
      setCurrentUserTenant(null);
      setCurrentTenantStorage(null);
    }
  }, [me]);

  useEffect(() => {
    if (me && logged) {
      loadUserTenants();
    }
  }, [me, logged, loadUserTenants]);

  // TODO multi-tenancy: Multi executors dev
  // When switching tenants we need to navigate to the new tenant URL prefix and reload tenant-scoped data
  const switchUserTenant = useCallback(async (tenantId: string) => {
    if (tenantId === currentUserTenant?.tenant_id) {
      return;
    }

    const current = userTenants.find(t => (t.tenant_id === tenantId));
    if (current) {
      setCurrentUserTenant(current);
      setCurrentTenantStorage(current);
    }
  }, [currentUserTenant, userTenants, setCurrentTenantStorage]);

  return {
    userTenants,
    currentUserTenant,
    switchUserTenant,
    reloadUserTenants: loadUserTenants,
  };
};

export default useTenant;
