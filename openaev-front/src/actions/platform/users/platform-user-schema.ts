import { schema } from 'normalizr';

export const PLATFORM_USER_SCHEMA_KEY = 'platform_users';
export const platformUser = new schema.Entity(PLATFORM_USER_SCHEMA_KEY, {}, { idAttribute: 'user_id' });
export const arrayOfPlatformUsers = new schema.Array(platformUser);
