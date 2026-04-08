import { useCallback, useEffect, useState } from 'react';
import { useLocation } from 'react-router';
import { useLocalStorage } from 'usehooks-ts';

import { fetchUserTenants } from '../../actions/user/user-tenant-actions';
import { TENANT_SWITCH_SUCCESS } from '../../constants/ActionTypes';
import { type TenantOutput, type User } from '../api-types';
import { useAppDispatch } from '../hooks';
import { buildTenantUrl, TENANT_STORAGE_KEY } from '../tenant-url-helper';

/**
 * Internal hook that encapsulates the current-tenant state and
 * dispatches TENANT_SWITCH_SUCCESS when the tenant actually changes.
 */
const useTenantState = () => {
  const dispatch = useAppDispatch();
  const [currentUserTenant, setCurrentUserTenant] = useState<TenantOutput | null>(null);

  const setTenant = useCallback((tenant: TenantOutput | null) => {
    setCurrentUserTenant((prev) => {
      if (tenant?.tenant_id && tenant.tenant_id !== prev?.tenant_id) {
        dispatch({
          type: TENANT_SWITCH_SUCCESS,
          payload: { tenantId: tenant.tenant_id },
        });
      }
      return tenant;
    });
  }, [dispatch]);

  return {
    currentUserTenant,
    setTenant,
  };
};

/**
 * Hook that manages the full tenant lifecycle:
 * - Fetches the tenants accessible to the current user
 * - Persists the selected tenant in local storage
 * - Provides a switch function to change the active tenant
 */
const useTenant = (me: User | undefined, logged: unknown) => {
  const [userTenants, setUserTenants] = useState<TenantOutput[]>([]);
  const [currentTenantStorage, setCurrentTenantStorage] = useLocalStorage<TenantOutput | null>(TENANT_STORAGE_KEY, null);
  const { currentUserTenant, setTenant } = useTenantState();
  const location = useLocation();

  const loadUserTenants = useCallback(async (newCurrentTenantId?: string) => {
    if (!me) return;

    const response = await fetchUserTenants();
    const tenants: TenantOutput[] = response.data;

    if (tenants && tenants.length > 0) {
      setUserTenants(tenants);
      // If a preferred tenant is requested and exists in the list, select it
      const newCurrentTenant = newCurrentTenantId
        ? tenants.find(tenant => tenant.tenant_id === newCurrentTenantId)
        : undefined;
      if (newCurrentTenant) {
        setTenant(newCurrentTenant);
        setCurrentTenantStorage(newCurrentTenant);
      } else {
        // Otherwise, if local storage tenant is still valid use it, otherwise switch to first tenant in list
        const currentTenant = tenants.find(tenant => (tenant.tenant_id === currentTenantStorage?.tenant_id));
        if (currentTenant) {
          setTenant(currentTenant);
          setCurrentTenantStorage(currentTenant);
        } else {
          setTenant(tenants[0]);
          setCurrentTenantStorage(tenants[0]);
        }
      }
    } else {
      setUserTenants([]);
      setTenant(null);
      setCurrentTenantStorage(null);
    }
  }, [me]);

  useEffect(() => {
    if (me && logged) {
      loadUserTenants();
    }
  }, [me, logged, loadUserTenants]);

  // When switching tenants, navigate to the new tenant URL prefix.
  // location.pathname is tenant-free (basename strips it), so we rebuild
  // the full URL with the new tenant segment. The full page navigation
  // triggers a reload where BrowserRouter picks up the new basename.
  const switchUserTenant = useCallback(async (tenantId: string) => {
    if (tenantId === currentUserTenant?.tenant_id) {
      return;
    }

    const current = userTenants.find(t => (t.tenant_id === tenantId));
    if (current) {
      setTenant(current);
      setCurrentTenantStorage(current);
      window.location.href = buildTenantUrl(tenantId, location.pathname, location.search, location.hash);
    }
  }, [currentUserTenant, userTenants, setCurrentTenantStorage, setTenant, location]);

  return {
    userTenants,
    currentUserTenant,
    switchUserTenant,
    reloadUserTenants: loadUserTenants,
  };
};

export default useTenant;
