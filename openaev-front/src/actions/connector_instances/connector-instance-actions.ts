import type { Dispatch } from 'redux';

import { delReferential, getReferential, putReferential, simpleCall, simplePostCall } from '../../utils/Action';
import {
  type ConnectorInstance,
  type CreateConnectorInstanceInput,
  type UpdateConnectorInstanceRequestedStatus,
} from '../../utils/api-types';
import { connectorInstance } from './connector-instance-schema';

const CONNECTOR_INSTANCE_URI = '/api/connector-instances';

export const createConnectorInstance = (input: CreateConnectorInstanceInput): Promise<{ data: ConnectorInstance }> => {
  return simplePostCall(CONNECTOR_INSTANCE_URI, input, undefined, false);
};

export const fetchConnectorInstance = (instanceId: string) => (dispatch: Dispatch) => {
  const uri = `${CONNECTOR_INSTANCE_URI}/${instanceId}`;
  return getReferential(connectorInstance, uri)(dispatch);
};

export const updateRequestedStatus = (instanceId: string, data: UpdateConnectorInstanceRequestedStatus) => (dispatch: Dispatch) => {
  const uri = `${CONNECTOR_INSTANCE_URI}/${instanceId}/requested-status`;
  return putReferential(connectorInstance, uri, data)(dispatch);
};

export const deleteConnectorInstance = (instanceId: string) => (dispatch: Dispatch) => {
  const uri = `${CONNECTOR_INSTANCE_URI}/${instanceId}`;
  return delReferential(uri, connectorInstance.key, instanceId)(dispatch);
};

export const fetchConnectorInstanceLogs = (instanceId: string) => () => {
  const uri = `${CONNECTOR_INSTANCE_URI}/${instanceId}/logs`;
  return simpleCall(uri);
};
