import type { Dispatch } from 'redux';

import { getReferential, simpleCall } from '../../utils/Action';
import * as schema from '../Schema';
import { fetchConnectorInstanceConfigurations } from "../connector_instances/connector-instance-actions";

const EXECUTOR_URI = '/api/executors';

export const fetchExecutors = (isNextIncluded = false) => (dispatch: Dispatch) => {
  const uri = `${EXECUTOR_URI}?include_next=${isNextIncluded}`;
  return getReferential(schema.arrayOfExecutors, uri)(dispatch);
};

export const fetchExecutor = (executorId: string) => (dispatch: Dispatch) => {
  const uri = `${EXECUTOR_URI}/${executorId}`;
  return getReferential(schema.executor, uri)(dispatch);
};

export const fetchExecutorRelatedIds = (executorId: string) => {
  return simpleCall(`${EXECUTOR_URI}/${executorId}/related-ids`);
};

export const fetchConfigurationValueForExecutorType = (executorType: string, configurationKey: string) => {
  const uri = `${EXECUTOR_URI}?include_next=true`;
  return simpleCall(uri)
    .then((response) => fetchExecutorRelatedIds(response.data.find(executor => executor.executor_type === executorType).executor_id))
    .then((response) => fetchConnectorInstanceConfigurations(response.data.connector_instance_id))
    .then((response) =>  response.data.find((configuration) => configuration.connector_instance_configuration_key === configurationKey).connector_instance_configuration_value,
    );
};
