import { schema } from 'normalizr';

export const PLATFORM_USER_SCHEMA_KEY = 'users';
export const user = new schema.Entity(PLATFORM_USER_SCHEMA_KEY, {}, { idAttribute: 'user_id' });
export const arrayOfUsers = new schema.Array(user);

