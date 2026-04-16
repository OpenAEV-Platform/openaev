import { useCallback, useEffect, useState } from 'react';
import { useLocation } from 'react-router';

import { fetchUserTenants } from '../../actions/user/user-tenant-actions';
import { TENANT_SWITCH_SUCCESS } from '../../constants/ActionTypes';
import { type TenantOutput, type User } from '../api-types';
import { useAppDispatch } from '../hooks';
import { buildTenantUrl, extractTenantFromUrl } from '../tenant-url-helper';

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
 * - Resolves the current tenant from the URL (per-tab, multi-tab safe)
 * - Provides a switch function that navigates to the new tenant URL
 *
 * After login (when the URL has no tenant segment yet), the hook
 * falls back to the first tenant in the user's tenant list.
 */
const useTenant = (me: User | undefined, logged: unknown) => {
  const [userTenants, setUserTenants] = useState<TenantOutput[]>([]);
  const { currentUserTenant, setTenant } = useTenantState();
  const location = useLocation();

  /**
   * Resolves a tenant by ID from the given list and activates it.
   * When the browser URL doesn't already point to that tenant, triggers
   * a full page navigation (skipping setTenant to avoid a broken intermediate render).
   * Returns true if a matching tenant was found.
   */
  const navigateToTenant = useCallback((tenantId: string, tenants: TenantOutput[]): boolean => {
    const target = tenants.find(t => t.tenant_id === tenantId);
    if (!target) return false;
    if (extractTenantFromUrl() !== target.tenant_id) {
      // Full page navigation — the reload will re-initialise tenant state,
      // so we intentionally skip setTenant to avoid a broken intermediate render.
      window.location.href = buildTenantUrl(target.tenant_id, location.pathname, location.search, location.hash);
    } else {
      setTenant(target);
    }
    return true;
  }, [setTenant, location]);

  const loadUserTenants = useCallback(async (newCurrentTenantId?: string) => {
    if (!me) return;

    const response = await fetchUserTenants();
    const tenants: TenantOutput[] = response.data;

    if (tenants && tenants.length > 0) {
      setUserTenants(tenants);
      // If a preferred tenant is requested, switch to it
      if (newCurrentTenantId && navigateToTenant(newCurrentTenantId, tenants)) {
        return;
      }
      // Resolve tenant from URL (per-tab, multi-tab safe).
      // Falls back to the first tenant in the list (post-login / public pages).
      const urlTenantId = extractTenantFromUrl();
      if (urlTenantId && navigateToTenant(urlTenantId, tenants)) {
        return;
      }
      setTenant(tenants[0]);
    } else {
      setUserTenants([]);
      setTenant(null);
    }
  }, [me, navigateToTenant, setTenant]);

  useEffect(() => {
    if (me && logged) {
      const urlTenantId = extractTenantFromUrl() ?? undefined;
      loadUserTenants(urlTenantId);
    }
  }, [me, logged, loadUserTenants]);

  const switchUserTenant = useCallback(async (tenantId: string) => {
    if (tenantId === currentUserTenant?.tenant_id) {
      return;
    }
    navigateToTenant(tenantId, userTenants);
  }, [currentUserTenant, userTenants, navigateToTenant]);

  return {
    userTenants,
    currentUserTenant,
    switchUserTenant,
    reloadUserTenants: loadUserTenants,
  };
};

export default useTenant;
