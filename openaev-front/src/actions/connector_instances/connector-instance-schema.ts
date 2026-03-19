import { schema } from 'normalizr';

export const connectorInstance = new schema.Entity(
  'connectorinstances',
  {},
  { idAttribute: 'connector_instance_id' },
);
export const arrayOfConnectorInstance = new schema.Array(connectorInstance);
