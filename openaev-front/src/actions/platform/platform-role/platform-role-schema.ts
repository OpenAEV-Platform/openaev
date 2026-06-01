import { schema } from 'normalizr';

export const PLATFORM_ROLE_SCHEMA_KEY = 'platform_roles';
export const platformRole = new schema.Entity(PLATFORM_ROLE_SCHEMA_KEY, {}, { idAttribute: 'platform_role_id' });
export const arrayOfPlatformRoles = new schema.Array(platformRole);
