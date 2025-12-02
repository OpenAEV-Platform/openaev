import type { Dispatch } from 'redux';

import { getReferential } from '../../utils/Action';
import { arrayOfCatalogConnectors, catalogConnector } from '../schemas';

const CATALOG_CONNECTORS_URI = '/api/catalog-connector';

export const fetchCatalogConnectors = () => (dispatch: Dispatch) => {
  return getReferential(arrayOfCatalogConnectors, CATALOG_CONNECTORS_URI)(dispatch);
};

export const fetchConnector = (connectorId: string) => (dispatch: Dispatch) => {
  const uri = `${CATALOG_CONNECTORS_URI}/${connectorId}`;
  return getReferential(catalogConnector, uri)(dispatch);
};
