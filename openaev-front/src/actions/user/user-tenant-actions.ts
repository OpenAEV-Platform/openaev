import type { TenantOutput } from '../../utils/api-types';

// -- TYPES --

/**
 * Extended TenantOutput with is_current flag for tenant switcher
 */
export interface UserTenantOutput extends TenantOutput { tenant_is_current?: boolean }

export interface UserTenantsResponse { tenants: UserTenantOutput[] }

export interface SwitchTenantInput { tenant_id: string }

export interface SwitchTenantResponse { token?: string }

// -- ACTIONS --

/**
 * Fetch all tenants the current user has access to
 */
export const fetchUserTenants = async (): Promise<UserTenantsResponse> => {
  // const uri = '/api/user/tenants';
  // const response = await simplePostCall(uri, {});
  // return response as UserTenantsResponse;

  // TODO: Remove mock data once backend endpoints are implemented
  // Mock data for development:
  return {
    tenants: [
      {
        tenant_id: '2cffad3a-0001-4078-b0e2-ef74274022c3', // DEFAULT_TENANT_UUID
        tenant_name: 'Default Tenant',
        tenant_description: 'First default tenant auto created',
        tenant_is_current: true,
      },
      {
        tenant_id: 'uuid-2',
        tenant_name: 'Beta Industries',
        tenant_description: 'Secondary tenant for testing',
        tenant_is_current: false,
      },
    ],
  };
};
