import { useCallback, useEffect, useState } from 'react';
import { useLocalStorage } from 'usehooks-ts';

import { fetchUserTenants } from '../../actions/user/user-tenant-actions';
import { type TenantOutput, type User } from '../api-types';

const DEFAULT_TENANT: TenantOutput = {
  tenant_id: '2cffad3a-0001-4078-b0e2-ef74274022c3', // DEFAULT_TENANT_UUID
  tenant_name: 'Default Tenant',
  tenant_description: 'Default tenant auto created',
};

/**
 * Hook that manages the full tenant lifecycle:
 * - Fetches the tenants accessible to the current user
 * - Persists the selected tenant in local storage
 * - Provides a switch function to change the active tenant
 */
const useTenant = (me: User | undefined, logged: unknown) => {
  const [userTenants, setUserTenants] = useState<TenantOutput[]>([]);
  const [currentTenantStorage, setCurrentTenantStorage] = useLocalStorage('current-tenant-storage', DEFAULT_TENANT);
  const [currentUserTenant, setCurrentUserTenant] = useState<TenantOutput>(DEFAULT_TENANT);

  const loadUserTenants = useCallback(async () => {
    if (!me) return;

    const result = await fetchUserTenants();

    if (result && result.tenants) {
      setUserTenants(result.tenants);
      // If local storage tenant is still valid use it, otherwise switch to first tenant in list
      const currentTenant = result.tenants.find(tenant => (tenant.tenant_id === currentTenantStorage.tenant_id));
      if (currentTenant) {
        setCurrentUserTenant(currentTenant);
        setCurrentTenantStorage(currentTenant);
      } else {
        setCurrentUserTenant(result.tenants[0]);
        setCurrentTenantStorage(result.tenants[0]);
      }
    }
  }, [me]);

  useEffect(() => {
    if (me && logged) {
      loadUserTenants();
    }
  }, [me, logged, loadUserTenants]);

  const switchUserTenant = useCallback(async (tenantId: string) => {
    if (tenantId === currentUserTenant?.tenant_id) {
      return;
    }

    setTimeout(() => {
      const current = userTenants.find(t => (t.tenant_id === tenantId));
      if (current) {
        setCurrentUserTenant(current);
      }
      // TODO multi-tenancy: tenant routing
      // window.location.replace(window.location.href);
    }, 0);
  }, [currentUserTenant, userTenants]);

  return {
    userTenants,
    currentUserTenant,
    switchUserTenant,
  };
};

export default useTenant;
