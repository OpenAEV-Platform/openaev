import { capitalize } from '@mui/material';
import { useContext, useEffect, useState } from 'react';
import { Outlet, useParams } from 'react-router';

import { fetchConnector, isXtmComposerIsReachable } from '../../../../actions/catalog/catalog-actions';
import type { CatalogConnectorsHelper } from '../../../../actions/catalog/catalog-helper';
import { type CollectorHelper } from '../../../../actions/collectors/collector-helper';
import { fetchConnectorInstance } from '../../../../actions/connector_instances/connector-instance-actions';
import type { ConnectorInstanceHelper } from '../../../../actions/connector_instances/connector-instance-helper';
import type { ExecutorHelper } from '../../../../actions/executors/executor-helper';
import { type InjectorHelper } from '../../../../actions/injectors/injector-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type store, useHelper } from '../../../../store';
import type {
  CatalogConnectorOutput,
  ConnectorIds,
  ConnectorInstanceOutput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { ConnectorContext, type ConnectorOutput } from './ConnectorContext';

export type ConnectorContextLayoutType = {
  connector: ConnectorOutput;
  instance: ConnectorInstanceOutput;
  catalogConnector: CatalogConnectorOutput;
  isXtmComposerUp: boolean;
};

const ConnectorLayout = () => {
  const params = useParams();
  const { connectorType, apiRequest, routes, normalizeSingle } = useContext(ConnectorContext);
  const connectorId = params[`${connectorType}Id`];

  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const [loading, setLoading] = useState<boolean>(true);
  const [relatedIds, setRelatedIds] = useState<ConnectorIds>();
  const [isRelatedIdsLoaded, setIsRelatedIdsLoaded] = useState<boolean>(false);
  const [isXtmComposerUp, setIsXtmComposerUp] = useState<boolean>(false);

  const getConnectorHelper = () => {
    switch (connectorType) {
      case 'executor':
        return useHelper((helper: ExecutorHelper) => ({ connector: helper.getExecutor(connectorId ?? '') }));
      case 'injector':
        return useHelper((helper: InjectorHelper) => ({ connector: helper.getInjector(connectorId ?? '') }));
      case 'collector':
        return useHelper((helper: CollectorHelper) => ({ connector: helper.getCollector(connectorId ?? '') }));
      default:
        return {};
    }
  };

  const { connector } = getConnectorHelper();

  const { connector: catalogConnector } = useHelper((helper: CatalogConnectorsHelper) => ({ connector: helper.getCatalogConnector(relatedIds?.catalog_connector_id ?? '') }));
  const { instance } = useHelper((helper: ConnectorInstanceHelper) => ({ instance: helper.getConnectorInstance(relatedIds?.connector_instance_id ?? '') }));

  useEffect(() => {
    isXtmComposerIsReachable().then(({ data }) => setIsXtmComposerUp(data));
    if (!connectorId) {
      setLoading(false);
      return;
    }
    setIsRelatedIdsLoaded(false);
    apiRequest.getRelatedIds(connectorId).then(({ data }: { data: ConnectorIds }) => {
      setRelatedIds(data);
      setIsRelatedIdsLoaded(true);
    });
  }, [connectorId]);

  useDataLoader(() => {
    if (!isRelatedIdsLoaded || !connectorId) {
      return;
    }
    const promises: Promise<typeof store.dispatch>[] = [
      dispatch(apiRequest.fetchSingle(connectorId)),
    ];
    if (relatedIds?.catalog_connector_id) {
      promises.push(dispatch(fetchConnector(relatedIds.catalog_connector_id)));
    }
    if (relatedIds?.connector_instance_id) {
      promises.push(dispatch(fetchConnectorInstance(relatedIds.connector_instance_id)));
    }
    Promise.all(promises).finally(() => setLoading(false));
  }, [isRelatedIdsLoaded]);

  const breadcrumbElements = connectorId
    ? [
        { label: t('Integrations') },
        {
          label: capitalize(t(`${connectorType}s`)),
          link: routes.list,
        },
        {
          label: connector?.[`${connectorType}_name`] || catalogConnector?.catalog_connector_title || 'Loading...',
          current: true,
        },
      ]
    : [
        { label: t('Integrations') },
        {
          label: capitalize(t(`${connectorType}s`)),
          current: true,
        },
      ];

  return (
    <>
      <Breadcrumbs variant="list" elements={breadcrumbElements} />
      {loading && <Loader />}
      {!loading && (
        <Outlet context={{
          connector: normalizeSingle(connector),
          catalogConnector,
          instance,
          isXtmComposerUp,
        }}
        />
      )}
    </>
  );
};

export default ConnectorLayout;
