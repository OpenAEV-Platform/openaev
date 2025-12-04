import type { Dispatch } from 'redux';

import { getReferential, simplePostCall } from '../../utils/Action';
import { type ConnectorInstance, type CreateConnectorInstanceInput } from '../../utils/api-types';
import { connectorInstance } from './connector-instance-schema';

const CONNECTOR_INSTANCE_URI = '/api/connector-instances';

export const createConnectorInstance = (input: CreateConnectorInstanceInput): Promise<{ data: ConnectorInstance }> => {
  return simplePostCall(CONNECTOR_INSTANCE_URI, input);
};

export const fetchConnectorInstance = (instanceId: string) => (dispatch: Dispatch) => {
  const uri = `${CONNECTOR_INSTANCE_URI}/${instanceId}`;
  return getReferential(connectorInstance, uri)(dispatch);
};
