import { type ReactNode } from 'react';

import type { CatalogConnectorOutput } from '../../../../utils/api-types';
import {
  collectorConfig,
  ConnectorContext,
  type ConnectorContextType,
  executorConfig,
  injectorConfig,
} from './ConnectorContext';

interface Props {
  children: ReactNode;
  type: CatalogConnectorOutput['catalog_connector_type'];
}

const ConnectorProvider = ({ children, type }: Props) => {
  const config = {
    INJECTOR: injectorConfig as ConnectorContextType<any>,
    COLLECTOR: collectorConfig as ConnectorContextType<any>,
    EXECUTOR: executorConfig as ConnectorContextType<any>,
  };

  return (
    <ConnectorContext.Provider value={config[type]}>
      {children}
    </ConnectorContext.Provider>
  );
};

export default ConnectorProvider;
