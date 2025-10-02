import { schema } from 'normalizr';

export const agent = new schema.Entity(
  'agents',
  {},
  { idAttribute: 'agent_id' },
);
export const arrayOfAgents = new schema.Array(agent);
