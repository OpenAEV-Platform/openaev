import { capitalize } from '@mui/material';
import { useCallback, useContext, useState } from 'react';
import { Outlet, useNavigate, useParams } from 'react-router';

import { fetchConnector, isXtmComposerIsReachable } from '../../../../actions/catalog/catalog-actions';
import type { CatalogConnectorsHelper } from '../../../../actions/catalog/catalog-helper';
import { type CollectorHelper } from '../../../../actions/collectors/collector-helper';
import { fetchConnectorInstance } from '../../../../actions/connector_instances/connector-instance-actions';
import type { ConnectorInstanceHelper } from '../../../../actions/connector_instances/connector-instance-helper';
import type { ExecutorHelper } from '../../../../actions/executors/executor-helper';
import { type InjectorHelper } from '../../../../actions/injectors/injector-helper';
import { type SecretsProviderHelper } from '../../../../actions/secrets_providers/secrets-provider-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type store, useHelper } from '../../../../store';
import type {
  CatalogConnectorOutput,
  ConnectorIds,
  ConnectorInstanceOutput,
} from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { ConnectorContext, type ConnectorOutput } from './ConnectorContext';

export type ConnectorContextLayoutType = {
  connector: ConnectorOutput;
  instance: ConnectorInstanceOutput;
  catalogConnector: CatalogConnectorOutput;
  isXtmComposerUp: boolean;
  refreshConnector: () => void;
};

const ConnectorLayout = () => {
  const params = useParams();
  const navigate = useNavigate();
  const { connectorType, apiRequest, routes, normalizeSingle } = useContext(ConnectorContext);
  const connectorId = params[`${connectorType}Id`];

  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const [loading, setLoading] = useState<boolean>(true);
  const [relatedIds, setRelatedIds] = useState<ConnectorIds>();
  const [isXtmComposerUp, setIsXtmComposerUp] = useState<boolean>(false);

  const getConnectorHelper = () => {
    switch (connectorType) {
      case 'executor':
        return useHelper((helper: ExecutorHelper) => ({ connector: helper.getExecutor(connectorId ?? '') }));
      case 'injector':
        return useHelper((helper: InjectorHelper) => ({ connector: helper.getInjector(connectorId ?? '') }));
      case 'collector':
        return useHelper((helper: CollectorHelper) => ({ connector: helper.getCollector(connectorId ?? '') }));
      case 'secrets_provider':
        return useHelper((helper: SecretsProviderHelper) => ({ connector: helper.getSecretsProvider(connectorId ?? '') }));
      default:
        return {};
    }
  };

  const { connector } = getConnectorHelper();

  const { connector: catalogConnector } = useHelper((helper: CatalogConnectorsHelper) => ({ connector: helper.getCatalogConnector(relatedIds?.catalog_connector_id ?? '') }));
  const { instance } = useHelper((helper: ConnectorInstanceHelper) => ({ instance: helper.getConnectorInstance(relatedIds?.connector_instance_id ?? '') }));

  const loadConnectorData = useCallback(() => {
    isXtmComposerIsReachable().then(({ data }) => setIsXtmComposerUp(data));

    if (!connectorId) {
      setLoading(false);
      setRelatedIds(undefined);
      return;
    }
    setLoading(true);
    apiRequest.getRelatedIds(connectorId).then(({ data }: { data: ConnectorIds }) => {
      if (!data) {
        setLoading(false);
      } else {
        setRelatedIds(data);
        const promises: Promise<typeof store.dispatch>[] = [];
        if (data?.connector_registered) {
          promises.push(dispatch(apiRequest.fetchSingle(connectorId)));
        }
        if (data?.catalog_connector_id) {
          promises.push(dispatch(fetchConnector(data.catalog_connector_id)));
        }
        if (data?.connector_instance_id) {
          promises.push(dispatch(fetchConnectorInstance(data.connector_instance_id)));
        }
        Promise.all(promises).finally(() => setLoading(false));
      }
    }).catch((error) => {
      setLoading(false);
      // The related-ids lookup returns 404 both when the connector genuinely
      // does not exist and when it belongs to another tenant
      if (error?.status === 404) {
        MESSAGING$.notifyError(t('This item does not exist or you are not allowed to view it.'));
        navigate(routes.list);
      }
    });
    // Deps are intentionally narrow: this callback is registered as an SSE reload listener by
    // useDataLoader, so its identity must only change when the fetched data actually changes.
    // t (new closure every render) and navigate (changes on navigation) would re-register and
    // refetch on every render; both behave correctly when captured once for this layout's lifetime.
  }, [connectorId, apiRequest, dispatch]);

  useDataLoader(() => {
    loadConnectorData();
  }, [loadConnectorData]);

  // Keep the trail short: "Integrations / <connector>" - the connector-type
  // segment (Injectors / Collectors / Executors) is not a navigable page of
  // its own, so it only adds noise between the list and the detail.
  const breadcrumbElements = connectorId
    ? [
        {
          label: t('Integrations'),
          link: routes.list,
        },
        {
          label: connector?.[`${connectorType}_name`] || catalogConnector?.catalog_connector_title || 'Loading...',
          current: true,
        },
      ]
    : [
        {
          label: t('Integrations'),
          link: routes.list,
        },
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
          refreshConnector: loadConnectorData,
        }}
        />
      )}
    </>
  );
};

export default ConnectorLayout;
