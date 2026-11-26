import { type CatalogConnectorOutput } from '../../utils/api-types';

export interface CatalogConnectorsHelper {
  getCatalogConnectors: () => CatalogConnectorOutput[];
  getCatalogConnector: (connectorId: string) => CatalogConnectorOutput;
}

// Value counts of each facet of an Available tab, by filter key.
export type CatalogFacetCounts = Record<string, Record<string, number>>;
