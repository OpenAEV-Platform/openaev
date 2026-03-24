import type { TenantOutput } from '../../utils/api-types';

// -- TYPES --

/**
 * Extended TenantOutput with is_current flag for tenant switcher
 */

export interface UserTenantsResponse { tenants: TenantOutput[] }

// -- ACTIONS --

/**
 * Fetch all tenants the current user has access to
 */
export const fetchUserTenants = async (): Promise<UserTenantsResponse> => {
  // const uri = '/api/user/tenants';
  // const response = await simplePostCall(uri, {});
  // return response as UserTenantsResponse;

  // TODO multi-tenancy: Remove mock data once backend endpoints are implemented
  // Mock data for development:
  return {
    tenants: [
      {
        tenant_id: '2cffad3a-0001-4078-b0e2-ef74274022c3', // DEFAULT_TENANT_UUID
        tenant_name: 'Default Tenant',
        tenant_description: 'First default tenant auto created',
      },
      {
        tenant_id: 'uuid-2',
        tenant_name: 'Beta Industries',
        tenant_description: 'Secondary tenant for testing',
      },
    ],
  };
};
