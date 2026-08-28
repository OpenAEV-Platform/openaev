import { type AxiosResponse } from 'axios';
import { createContext } from 'react';
import { type Dispatch } from 'redux';

import { deleteCollector, fetchCollector, fetchCollectorRelatedIds, fetchCollectors } from '../../../../actions/Collector';
import { deleteExecutor, fetchExecutor, fetchExecutorRelatedIds, fetchExecutors } from '../../../../actions/executors/executor-action';
import { deleteInjector, fetchInjector, fetchInjectorRelatedIds, fetchInjectors } from '../../../../actions/injectors/injector-action';
import {
  fetchSecretProvider,
  fetchSecretsProviderRelatedIds,
  fetchSecretsProviders,
} from '../../../../actions/secrets_providers/secrets-providers-action';
import type {
  CatalogConnectorOutput,
  CatalogConnectorSimpleOutput,
  Collector,
  CollectorOutput, ConnectorIds,
  ConnectorInstanceOutput,
  ExecutorOutput,
  InjectorOutput,
  SecretsProviderOutput,
} from '../../../../utils/api-types';
import { buildTenantApiPath } from '../../../../utils/url-helper';

/**
 * Freshest of the available heartbeat signals: an external collector bumps its
 * registration date (~40s ping) while a built-in one stamps its last execution,
 * so taking either one alone can show a healthy connector as down.
 */
const latestOf = (...dates: (string | undefined)[]): string | undefined =>
  dates
    .filter((d): d is string => d != null)
    .sort((a, b) => new Date(b).getTime() - new Date(a).getTime())[0];

export interface ConnectorOutput {
  id: string;
  name: string;
  type: string;
  catalog?: CatalogConnectorSimpleOutput;
  updatedAt?: string;
  connectorInstance?: ConnectorInstanceOutput;
  isExternal?: boolean;
  isExisting?: boolean;
}

/**
 * Support semantics (same as OpenCTI): "Supported by Filigran" comes from the
 * CATALOG's verified flag, and built-in connectors (shipped inside the
 * platform, usually without a catalog entry) are Filigran-supported by
 * definition. The connector output's own `is_verified` field must NOT drive
 * this badge: the backend sets it to "has a connector instance" (see
 * InjectorMapper / CollectorMapper / ExecutorMapper), so any deployed
 * community connector would wrongly appear Filigran-supported.
 */
export const isSupportedByFiligran = (
  connector: Pick<ConnectorOutput, 'isExternal' | 'isExisting'> | undefined,
  catalogVerified: boolean | undefined,
): boolean =>
  catalogVerified === true
  || (connector != null && connector.isExternal !== true && connector.isExisting === true);

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
    /** Deletes the connector entity itself (for manually-registered connectors with no managed instance). */
    deleteSingle: (id: string) => (dispatch: Dispatch) => Promise<unknown>;
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
    deleteSingle: (id: string) => deleteInjector(id),
  },
  routes: {
    list: '/admin/integrations/deployed',
    detail: (id: string) => `/admin/integrations/injectors/${id}`,
  },
  logoUrl: (type: string) => buildTenantApiPath(`/api/injectors/${type}/image`),
  normalizeSingle: data => ({
    id: data?.injector_id,
    name: data?.injector_name,
    type: data?.injector_type,
    catalog: data?.catalog,
    updatedAt: data?.injector_updated_at,
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
    deleteSingle: (id: string) => deleteCollector(id),
  },
  logoUrl: (type: string) => buildTenantApiPath(`/api/collectors/${type}/image`),
  normalizeSingle: data => ({
    id: data?.collector_id,
    name: data?.collector_name,
    type: data?.collector_type,
    catalog: data?.catalog,
    updatedAt: latestOf(data?.collector_last_execution, data?.collector_updated_at),
    connectorInstance: data?.connector_instance,
    isExternal: data?.collector_external,
    isExisting: data?.existing_collector,
  }),
  routes: {
    list: '/admin/integrations/deployed',
    detail: (id: string) => `/admin/integrations/collectors/${id}`,
  },
};

export const executorConfig: ConnectorContextType<ExecutorOutput> = {
  connectorType: 'executor',
  apiRequest: {
    fetchAll: () => fetchExecutors(true),
    fetchSingle: (id: string) => fetchExecutor(id),
    getRelatedIds: (id: string) => fetchExecutorRelatedIds(id),
    deleteSingle: (id: string) => deleteExecutor(id),
  },
  routes: {
    list: '/admin/integrations/deployed',
    detail: (id: string) => `/admin/integrations/executors/${id}`,
  },
  logoUrl: (type: string) => buildTenantApiPath(`/api/images/executors/icons/${type}`),
  normalizeSingle: data => ({
    id: data?.executor_id,
    name: data?.executor_name,
    type: data?.executor_type,
    catalog: data?.catalog,
    updatedAt: data?.executor_updated_at,
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
    deleteSingle: (_id: string) => async (_dispatch: Dispatch) => Promise.reject(new Error('Deleting secrets providers is not supported')),
  },
  routes: {
    list: '/admin/integrations/deployed',
    detail: (id: string) => `/admin/integrations/secrets-providers/${id}`,
  },
  logoUrl: (type: string) => buildTenantApiPath(`/api/secrets_providers/${type}/image`),
  normalizeSingle: data => ({
    id: data?.secrets_provider_id,
    name: data?.secrets_provider_name,
    type: data?.secrets_provider_type,
    catalog: data?.catalog,
    isVerified: data?.is_verified ?? false,
    connectorInstance: data?.connector_instance,
    isExisting: data?.existing_secret_provider,
  }),
};

export const ConnectorContext = createContext<ConnectorContextType<InjectorOutput | CollectorOutput | ExecutorOutput | SecretsProviderOutput>>({
  connectorType: 'collector',
  logoUrl: _type => '',
  apiRequest: {
    fetchAll: () => async (_dispatch: Dispatch) => [],
    fetchSingle: (_id: string) => async (_dispatch: Dispatch) => Promise.resolve({}) as Promise<InjectorOutput | CollectorOutput | ExecutorOutput | SecretsProviderOutput>,
    getRelatedIds: (_id: string) => Promise.resolve({ data: {} }) as Promise<AxiosResponse<ConnectorIds>>,
    deleteSingle: (_id: string) => async (_dispatch: Dispatch) => Promise.resolve(),
  },
  routes: {
    list: '/admin/integrations',
    detail: (_id: string) => '/admin/integrations',
  },
  normalizeSingle: (_data: InjectorOutput | CollectorOutput | ExecutorOutput | SecretsProviderOutput) => ({} as ConnectorOutput),
});
