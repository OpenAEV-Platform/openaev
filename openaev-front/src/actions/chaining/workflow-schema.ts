import { schema } from 'normalizr';

export const workflowConfigurationSchema = (workflowId: string) => new schema.Entity(
  'workflowconfigurations',
  {},
  { idAttribute: () => workflowId },
);
