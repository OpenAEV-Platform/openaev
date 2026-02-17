import { schema } from 'normalizr';

export const catalogConnector = new schema.Entity(
  'catalog_connectors',
  {},
  { idAttribute: 'catalog_connector_id' },
);
export const arrayOfCatalogConnectors = new schema.Array(catalogConnector);
