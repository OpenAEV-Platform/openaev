import { schema } from 'normalizr';

const workflowConfigurationSchema = (workflowId: string) => new schema.Entity(
  'workflowconfigurations',
  {},
  { idAttribute: () => workflowId },
);

export default workflowConfigurationSchema;
