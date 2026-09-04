import { schema } from 'normalizr';

export const ROLE_SCHEMA_KEY = 'roles';
export const role = new schema.Entity(ROLE_SCHEMA_KEY, {}, { idAttribute: 'role_id' });
export const arrayOfRoles = new schema.Array(role);
