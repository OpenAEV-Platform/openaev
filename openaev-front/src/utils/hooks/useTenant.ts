import { useCallback, useEffect, useState } from 'react';
import { useLocation } from 'react-router';

import { fetchUserTenants } from '../../actions/user/user-tenant-actions';
import { TENANT_SWITCH_SUCCESS } from '../../constants/ActionTypes';
import { type TenantOutput, type User } from '../api-types';
import { useAppDispatch } from '../hooks';
import { buildTenantUrl, extractTenantFromUrl, stripDetailSegments } from '../url-helper';

const lastTenantKey = (userId?: string) => `lastTenantId:${userId}`;

// Storage can be unavailable (disabled, private mode): the app then just opens the first tenant
const readLastTenantId = (userId?: string): string | null => {
  try {
    return localStorage.getItem(lastTenantKey(userId));
  } catch {
    return null;
  }
};

const saveLastTenantId = (userId: string | undefined, tenantId: string) => {
  try {
    localStorage.setItem(lastTenantKey(userId), tenantId);
  } catch {
    // ignored, see readLastTenantId
  }
};

/**
 * Tenant to open when the URL has none: the last one the user opened, else the first.
 */
export const pickDefaultTenantId = (tenants: TenantOutput[], userId?: string): string | undefined => {
  const lastTenantId = readLastTenantId(userId);
  return (tenants.find(t => t.tenant_id === lastTenantId) ?? tenants[0])?.tenant_id;
};

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
 * falls back to the last tenant the user opened, else the first one.
 */
const useTenant = (me: User | undefined, logged: unknown) => {
  const [userTenants, setUserTenants] = useState<TenantOutput[] | undefined>(undefined);
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
      // Switching to a different tenant — strip detail segments so we land on
      // the list page instead of a detail page for a resource that may not exist.
      const safePath = stripDetailSegments(location.pathname);
      // Full page navigation — the reload will re-initialise tenant state,
      // so we intentionally skip setTenant to avoid a broken intermediate render.
      window.location.href = buildTenantUrl(target.tenant_id, safePath);
    } else {
      setTenant(target);
      saveLastTenantId(me?.user_id, target.tenant_id);
    }
    return true;
  }, [me?.user_id, setTenant, location]);

  const loadUserTenants = useCallback(async (newCurrentTenantId?: string) => {
    if (!me) return;

    try {
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
        // URL tenant missing or not in the user's list
        const defaultTenantId = pickDefaultTenantId(tenants, me.user_id);
        if (defaultTenantId) navigateToTenant(defaultTenantId, tenants);
      } else {
        setUserTenants([]);
        setTenant(null);
      }
    } catch {
      // If tenant fetch fails (network error, 500, etc.), set empty list
      // so the app doesn't stay stuck on a loading spinner indefinitely.
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
    navigateToTenant(tenantId, userTenants ?? []);
  }, [currentUserTenant, userTenants, navigateToTenant]);

  return {
    userTenants,
    currentUserTenant,
    switchUserTenant,
    reloadUserTenants: loadUserTenants,
  };
};

export default useTenant;
