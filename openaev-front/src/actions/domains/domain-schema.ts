import { schema } from 'normalizr';

export const domain = new schema.Entity('domain', {}, { idAttribute: 'domain_id' });

export const arrayOfDomains = new schema.Array(domain);
