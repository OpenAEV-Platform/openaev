import { type CatalogConnectorOutput } from '../../utils/api-types';

export interface CatalogConnectorsHelper {
  getCatalogConnectors: () => CatalogConnectorOutput[];
  getCatalogConnector: (connectorId: string) => CatalogConnectorOutput;
  getUnDeployedCatalogConnectors: () => CatalogConnectorOutput[];
}
