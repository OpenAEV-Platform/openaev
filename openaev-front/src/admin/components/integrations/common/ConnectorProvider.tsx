import { type ReactNode, useMemo } from 'react';

import type {
  CatalogConnectorOutput,
  CollectorOutput,
  ExecutorOutput,
  InjectorOutput,
} from '../../../../utils/api-types';
import useAuth from '../../../../utils/hooks/useAuth';
import {
  ConnectorContext,
  type ConnectorContextType,
  createCollectorConfig,
  createExecutorConfig,
  createInjectorConfig,
} from './ConnectorContext';

interface Props {
  children: ReactNode;
  type: CatalogConnectorOutput['catalog_connector_type'];
}

const ConnectorProvider = ({ children, type }: Props) => {
  const { currentUserTenant } = useAuth();
  const tenantId = currentUserTenant?.tenant_id;
  const value = useMemo(() => {
    const config = {
      INJECTOR: createInjectorConfig(tenantId),
      COLLECTOR: createCollectorConfig(tenantId),
      EXECUTOR: createExecutorConfig(tenantId),
    };
    return config[type] as ConnectorContextType<
      InjectorOutput | CollectorOutput | ExecutorOutput
    >;
  }, [tenantId, type]);

  return (
    <ConnectorContext.Provider value={value}>
      {children}
    </ConnectorContext.Provider>
  );
};

export default ConnectorProvider;
