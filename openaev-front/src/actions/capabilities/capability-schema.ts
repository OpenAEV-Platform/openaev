import { schema } from 'normalizr';

export const capability = new schema.Entity('capabilities', {}, { idAttribute: 'capability_value' });
export const arrayOfCapabilities = new schema.Array(capability);
