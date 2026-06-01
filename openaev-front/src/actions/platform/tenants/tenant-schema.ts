import { schema } from 'normalizr';

export const tenant = new schema.Entity('tenants', {}, { idAttribute: 'tenant_id' });
export const arrayOfTenants = new schema.Array(tenant);
