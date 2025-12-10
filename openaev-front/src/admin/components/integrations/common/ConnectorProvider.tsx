import { type ReactNode } from 'react';

import type {
  CatalogConnectorOutput,
  CollectorOutput,
  ExecutorOutput,
  InjectorOutput,
} from '../../../../utils/api-types';
import {
  collectorConfig,
  ConnectorContext, type ConnectorContextType,
  executorConfig,
  injectorConfig,
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
  };

  return (
    <ConnectorContext.Provider value={config[type] as ConnectorContextType<InjectorOutput | CollectorOutput | ExecutorOutput>}>
      {children}
    </ConnectorContext.Provider>
  );
};

export default ConnectorProvider;
