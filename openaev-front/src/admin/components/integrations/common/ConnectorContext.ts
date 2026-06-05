import { type AxiosResponse } from 'axios';
import { createContext } from 'react';
import { type Dispatch } from 'redux';

import { fetchCollector, fetchCollectorRelatedIds, fetchCollectors } from '../../../../actions/Collector';
import { fetchExecutor, fetchExecutorRelatedIds, fetchExecutors } from '../../../../actions/executors/executor-action';
import { fetchInjector, fetchInjectorRelatedIds, fetchInjectors } from '../../../../actions/injectors/injector-action';
import type {
  CatalogConnectorOutput, CatalogConnectorSimpleOutput,
  Collector,
  CollectorOutput, ConnectorIds,
  ConnectorInstanceOutput,
  ExecutorOutput,
  InjectorOutput, SecretsProviderOutput,
} from '../../../../utils/api-types';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import {
  fetchSecretProvider, fetchSecretsProviderRelatedIds,
  fetchSecretsProviders
} from "../../../../actions/secrets_providers/secrets-providers-action";

export interface ConnectorOutput {
  id: string;
  name: string;
  type: string;
  catalog?: CatalogConnectorSimpleOutput;
  updatedAt?: string;
  isVerified: boolean;
  connectorInstance?: ConnectorInstanceOutput;
  isExternal?: boolean;
  isExisting?: boolean;
}

export interface ConnectorContextType<T> {
  connectorType: 'collector' | 'injector' | 'executor' | 'secrets_provider';
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
  logoUrl: (type: string) => buildTenantApiPath(`/api/images/injectors/${type}`),
  normalizeSingle: data => ({
    id: data?.injector_id,
    name: data?.injector_name,
    type: data?.injector_type,
    catalog: data?.catalog,
    updatedAt: data?.injector_updated_at,
    isVerified: data?.is_verified ?? false,
    connectorInstance: data?.connector_instance,
    isExternal: data?.injector_external,
    isExisting: data?.existing_injector,
  }),
};

export const collectorConfig: ConnectorContextType<CollectorOutput & Collector> = {
  connectorType: 'collector',
  apiRequest: {
    fetchAll: () => fetchCollectors(true),
    fetchSingle: (id: string) => fetchCollector(id),
    getRelatedIds: (id: string) => fetchCollectorRelatedIds(id),
  },
  logoUrl: (type: string) => buildTenantApiPath(`/api/images/collectors/${type}`),
  normalizeSingle: data => ({
    id: data?.collector_id,
    name: data?.collector_name,
    type: data?.collector_type,
    catalog: data?.catalog,
    updatedAt: data?.collector_last_execution || data?.collector_updated_at,
    isVerified: data?.is_verified ?? false,
    connectorInstance: data?.connector_instance,
    isExternal: data?.collector_external,
    isExisting: data?.existing_collector,
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
  logoUrl: (type: string) => buildTenantApiPath(`/api/images/executors/icons/${type}`),
  normalizeSingle: data => ({
    id: data?.executor_id,
    name: data?.executor_name,
    type: data?.executor_type,
    catalog: data?.catalog,
    updatedAt: data?.executor_updated_at,
    isVerified: data?.is_verified ?? false,
    connectorInstance: data?.connector_instance,
    isExisting: data?.existing_executor,
  }),
};

export const secretsProviderConfig: ConnectorContextType<SecretsProviderOutput> = {
  connectorType: 'secrets_provider',
  apiRequest: {
    fetchAll: () => fetchSecretsProviders(true),
    fetchSingle: (id: string) => fetchSecretProvider(id),
    getRelatedIds: (id: string) => fetchSecretsProviderRelatedIds(id),
  },
  routes: {
    list: '/admin/integrations/secrets_providers',
    detail: (id: string) => `/admin/integrations/secrets_providers/${id}`,
  },
  logoUrl: (type: string) => buildTenantApiPath(`/api/images/secrets_providers/icons/${type}`),
  normalizeSingle: data => ({
    id: data?.secrets_provider_id,
    name: data?.secrets_provider_name,
    type: data?.secrets_provider_type,
    catalog: data?.catalog,
    isVerified: data?.is_verified ?? false,
    connectorInstance: data?.connector_instance,
  }),
};

export const ConnectorContext = createContext<ConnectorContextType<InjectorOutput | CollectorOutput | ExecutorOutput | SecretsProviderOutput>>({
  connectorType: 'collector',
  logoUrl: _type => '',
  apiRequest: {
    fetchAll: () => async (_dispatch: Dispatch) => [],
    fetchSingle: (_id: string) => async (_dispatch: Dispatch) => Promise.resolve({}) as Promise<InjectorOutput | CollectorOutput | ExecutorOutput | SecretsProviderOutput>,
    getRelatedIds: (_id: string) => Promise.resolve({ data: {} }) as Promise<AxiosResponse<ConnectorIds>>,
  },
  routes: {
    list: '/admin/integrations',
    detail: (_id: string) => '/admin/integrations',
  },
  normalizeSingle: (_data: InjectorOutput | CollectorOutput | ExecutorOutput | SecretsProviderOutput) => ({} as ConnectorOutput),
});
