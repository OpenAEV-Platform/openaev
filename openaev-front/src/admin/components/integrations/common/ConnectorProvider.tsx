import { type ReactNode } from 'react';

import type { CatalogConnectorOutput } from '../../../../utils/api-types';
import {
  collectorConfig,
  ConnectorContext,
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
    <ConnectorContext.Provider value={config[type]}>
      {children}
    </ConnectorContext.Provider>
  );
};

export default ConnectorProvider;
