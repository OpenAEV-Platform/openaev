import { type AxiosResponse } from 'axios';
import { createContext } from 'react';
import { type Dispatch } from 'redux';

import { fetchCollector, fetchCollectorRelatedIds, fetchCollectors } from '../../../../actions/Collector';
import { fetchExecutor, fetchExecutorRelatedIds, fetchExecutors } from '../../../../actions/executors/executor-action';
import { fetchInjector, fetchInjectorRelatedIds, fetchInjectors } from '../../../../actions/injectors/injector-action';
import type {
  CatalogConnectorOutput, CatalogConnectorSimpleOutput,
  CollectorOutput, ConnectorIds,
  ConnectorInstanceOutput,
  ExecutorOutput,
  InjectorOutput,
} from '../../../../utils/api-types';

export interface ConnectorOutput {
  id: string;
  name: string;
  type: string;
  catalog?: CatalogConnectorSimpleOutput;
  updatedAt?: string;
  isVerified: boolean;
  currentStatus?: ConnectorInstanceOutput['connector_instance_current_status'];
  isExternal?: boolean;
}

export interface ConnectorContextType<T> {
  connectorType: 'collector' | 'injector' | 'executor';
  connectorCatalog?: CatalogConnectorOutput;
  connector?: ConnectorOutput;
  connectorInstance?: ConnectorInstanceOutput;
  logoUrl: (_type: string) => string;
  apiRequest: {
    fetchAll: () => (dispatch: Dispatch) => Promise<T[]>;
    fetchSingle: (id: string) => (dispatch: Dispatch) => Promise<T>;
    getRelatedIds: (_id: string) => Promise<AxiosResponse<ConnectorIds>>;
  };
  routes: {
    list: string;
    detail: (id: string) => string;
  };
  normalizeSingle: (data: T) => ConnectorOutput;
}

export const injectorConfig: ConnectorContextType<InjectorOutput> = {
  connectorType: 'injector',
  apiRequest: {
    fetchAll: () => fetchInjectors(true),
    fetchSingle: (id: string) => fetchInjector(id),
    getRelatedIds: (id: string) => fetchInjectorRelatedIds(id),
  },
  routes: {
    list: '/admin/integrations/injectors',
    detail: (id: string) => `/admin/integrations/injectors/${id}`,
  },
  logoUrl: (type: string) => `/api/images/injectors/${type}`,
  normalizeSingle: data => ({
    id: data?.injector_id,
    name: data?.injector_name,
    type: data?.injector_type,
    catalog: data?.catalog,
    updatedAt: data?.injector_updated_at,
    isVerified: data?.is_verified ?? false,
    currentStatus: data?.current_status,
    isExternal: data?.injector_external,
  }),
};

export const collectorConfig: ConnectorContextType<CollectorOutput> = {
  connectorType: 'collector',
  apiRequest: {
    fetchAll: () => fetchCollectors(true),
    fetchSingle: (id: string) => fetchCollector(id),
    getRelatedIds: (id: string) => fetchCollectorRelatedIds(id),
  },
  logoUrl: (type: string) => `/api/images/collectors/${type}`,
  normalizeSingle: data => ({
    id: data?.collector_id,
    name: data?.collector_name,
    type: data?.collector_type,
    catalog: data?.catalog,
    updatedAt: data?.collector_last_execution,
    isVerified: data?.is_verified ?? false,
    currentStatus: data?.current_status,
    isExternal: data?.collector_external,
  }),
  routes: {
    list: '/admin/integrations/collectors',
    detail: (id: string) => `/admin/integrations/collectors/${id}`,
  },
};

export const executorConfig: ConnectorContextType<ExecutorOutput> = {
  connectorType: 'executor',
  apiRequest: {
    fetchAll: () => fetchExecutors(true),
    fetchSingle: (id: string) => fetchExecutor(id),
    getRelatedIds: (id: string) => fetchExecutorRelatedIds(id),
  },
  routes: {
    list: '/admin/integrations/executors',
    detail: (id: string) => `/admin/integrations/executors/${id}`,
  },
  logoUrl: (type: string) => `/api/images/executors/icons/${type}`,
  normalizeSingle: data => ({
    id: data?.executor_id,
    name: data?.executor_name,
    type: data?.executor_type,
    catalog: data?.catalog,
    updatedAt: data?.executor_updated_at,
    isVerified: data?.is_verified ?? false,
    currentStatus: data?.current_status,
  }),
};

export const ConnectorContext = createContext<ConnectorContextType<InjectorOutput | CollectorOutput | ExecutorOutput>>({
  connectorType: 'collector',
  logoUrl: _type => '',
  apiRequest: {
    fetchAll: () => async (_dispatch: Dispatch) => [],
    fetchSingle: (_id: string) => async (_dispatch: Dispatch) => Promise.resolve({}) as Promise<InjectorOutput | CollectorOutput | ExecutorOutput>,
    getRelatedIds: (_id: string) => Promise.resolve({ data: {} }) as Promise<AxiosResponse<ConnectorIds>>,
  },
  routes: {
    list: '/admin/integrations',
    detail: (_id: string) => '/admin/integrations',
  },
  normalizeSingle: (_data: InjectorOutput | CollectorOutput | ExecutorOutput) => ({} as ConnectorOutput),
});
