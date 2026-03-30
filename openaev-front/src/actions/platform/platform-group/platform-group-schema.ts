import { schema } from 'normalizr';

export const PLATFORM_GROUP_SCHEMA_KEY = 'platform_groups';
export const platformGroup = new schema.Entity(PLATFORM_GROUP_SCHEMA_KEY, {}, { idAttribute: 'platform_group_id' });
export const arrayOfPlatformGroups = new schema.Array(platformGroup);
