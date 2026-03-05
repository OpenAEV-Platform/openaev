import { schema } from 'normalizr';

export const TENANT_USER_SCHEMA_KEY = 'users';
export const tenantUser = new schema.Entity(TENANT_USER_SCHEMA_KEY, {}, { idAttribute: 'user_id' });
export const arrayOfTenantUsers = new schema.Array(tenantUser);

