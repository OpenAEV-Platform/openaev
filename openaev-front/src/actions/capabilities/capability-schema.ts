import { schema } from 'normalizr';

export const platformCapability = new schema.Entity('platform_capabilities', {}, { idAttribute: 'capability_value' });
export const arrayOfPlatformCapabilities = new schema.Array(platformCapability);

export const tenantCapability = new schema.Entity('tenant_capabilities', {}, { idAttribute: 'capability_value' });
export const arrayOfTenantCapabilities = new schema.Array(tenantCapability);
