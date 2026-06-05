import { type ReactNode } from 'react';

import type {
  CatalogConnectorOutput,
  CollectorOutput,
  ExecutorOutput,
  InjectorOutput, SecretsProviderOutput,
} from '../../../../utils/api-types';
import {
  collectorConfig,
  ConnectorContext,
  type ConnectorContextType, executorConfig, injectorConfig, secretsProviderConfig,
} from './ConnectorContext';

interface Props {
  children: ReactNode;
  type: CatalogConnectorOutput['catalog_connector_type'];
}

const ConnectorProvider = ({ children, type }: Props) => {
  const config = {
    INJECTOR: injectorConfig,
    COLLECTOR: collectorConfig,
    EXECUTOR: executorConfig,
    SECRETS_PROVIDER: secretsProviderConfig
  };

  return (
    <ConnectorContext.Provider value={config[type] as ConnectorContextType<InjectorOutput | CollectorOutput | ExecutorOutput | SecretsProviderOutput>}>
      {children}
    </ConnectorContext.Provider>
  );
};

export default ConnectorProvider;
