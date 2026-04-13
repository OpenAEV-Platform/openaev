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
      } else {
        // Resolve tenant from URL (per-tab, multi-tab safe).
        // Falls back to the first tenant in the list (post-login / public pages).
        const urlTenantId = extractTenantFromUrl();
        const currentTenant = urlTenantId
          ? tenants.find(tenant => tenant.tenant_id === urlTenantId)
          : undefined;
        setTenant(currentTenant ?? tenants[0]);
      }
    } else {
      setUserTenants([]);
      setTenant(null);
    }
  }, [me]);

  useEffect(() => {
    if (me && logged) {
      // On page load / hard refresh the URL is the source of truth.
      // Pass the URL tenant ID so loadUserTenants selects the right one.
      const urlTenantId = extractTenantFromUrl() ?? undefined;
      loadUserTenants(urlTenantId);
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
      window.location.href = buildTenantUrl(tenantId, location.pathname, location.search, location.hash);
    }
  }, [currentUserTenant, userTenants, setTenant, location]);

  return {
    userTenants,
    currentUserTenant,
    switchUserTenant,
    reloadUserTenants: loadUserTenants,
  };
};

export default useTenant;
