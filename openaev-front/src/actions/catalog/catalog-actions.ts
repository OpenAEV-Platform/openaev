import type { Dispatch } from 'redux';

import { getReferential, simpleCall } from '../../utils/Action';
import { arrayOfCatalogConnectors, catalogConnector } from './catalog-schema';

const CATALOG_CONNECTORS_URI = '/api/catalog-connector';

export const fetchUndeployedCatalogConnectors = () => (dispatch: Dispatch) => {
  return getReferential(arrayOfCatalogConnectors, `${CATALOG_CONNECTORS_URI}/undeployed`)(dispatch);
};

export const isXtmComposerIsReachable = () => {
  return simpleCall(`/api/xtm-composer/reachable`);
};

export const fetchConnector = (connectorId: string) => (dispatch: Dispatch) => {
  const uri = `${CATALOG_CONNECTORS_URI}/${connectorId}`;
  return getReferential(catalogConnector, uri)(dispatch);
};

export const fetchCatalogConnectorConfigurations = (catalogConnectorId: string) => {
  return simpleCall(`${CATALOG_CONNECTORS_URI}/${catalogConnectorId}/configurations`);
};
