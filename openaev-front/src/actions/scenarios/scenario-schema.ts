import { schema } from 'normalizr';

export const scenario = new schema.Entity(
  'scenarios',
  {},
  { idAttribute: 'scenario_id' },
);
export const arrayOfScenarios = new schema.Array(scenario);
