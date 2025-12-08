import { useEffect, useState } from 'react';
import { Outlet, useParams } from 'react-router';

import { fetchConnector, isXtmComposerIsReachable } from '../../../../actions/catalog/catalog-actions';
import type { CatalogConnectorsHelper } from '../../../../actions/catalog/catalog-helper';
import { fetchCollectorRelatedIds } from '../../../../actions/Collector';
import { fetchConnectorInstance } from '../../../../actions/connector_instances/connector-instance-actions';
import type { ConnectorInstanceHelper } from '../../../../actions/connector_instances/connector-instance-helper';
import { fetchExecutor } from '../../../../actions/executors/executor-action';
import type { ExecutorHelper } from '../../../../actions/executors/executor-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { useHelper } from '../../../../store';
import {
  type CatalogConnectorOutput,
  type ConnectorIds,
  type ConnectorInstance, type Executor,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';

export type ExecutorsContextType = {
  executor: Executor;
  instance: ConnectorInstance;
  catalogConnector: CatalogConnectorOutput;
  isXtmComposerUp: boolean;
};

const ExecutorsLayout = () => {
  const { executorId } = useParams() as { executorId: string };
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const [loading, setLoading] = useState<boolean>(true);
  const [relatedIds, setRelatedIds] = useState<ConnectorIds>();
  const [isXtmComposerUp, setIsXtmComposerUp] = useState<boolean>(false);

  useEffect(() => {
    isXtmComposerIsReachable().then(({ data }) => setIsXtmComposerUp(data));
    if (!executorId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    fetchCollectorRelatedIds(executorId).then(({ data }: { data: ConnectorIds }) => {
      setRelatedIds(data);
      if (data.catalog_connector_id) {
        setLoading(false);
      }
    });
  }, [executorId]);

  const { connector: catalogConnector } = useHelper((helper: CatalogConnectorsHelper) => ({ connector: helper.getCatalogConnector(relatedIds?.catalog_connector_id ?? '') }));
  const { instance } = useHelper((helper: ConnectorInstanceHelper) => ({ instance: helper.getConnectorInstance(relatedIds?.connector_instance_id ?? '') }));
  const { executor } = useHelper((helper: ExecutorHelper) => ({ executors: helper.getExecutor(executorId) }));

  useDataLoader(() => {
    if (relatedIds === undefined) return;
    dispatch(fetchExecutor(executorId));
    if (relatedIds?.catalog_connector_id) {
      dispatch(fetchConnector(relatedIds.catalog_connector_id)).finally(() => setLoading(false));
    }
    if (relatedIds?.connector_instance_id) {
      dispatch(fetchConnectorInstance(relatedIds.connector_instance_id));
    }
  }, [relatedIds?.connector_instance_id, relatedIds?.connector_instance_id]);

  const breadcrumbElements = executorId
    ? [
        { label: t('Integrations') },
        {
          label: t('Executors'),
          link: '/admin/integrations/executors',
        },
        {
          label: executor?.executor_name || catalogConnector?.catalog_connector_title || 'Loading...',
          current: true,
        },
      ]
    : [
        { label: t('Integrations') },
        {
          label: t('Executors'),
          current: true,
        },
      ];

  return (
    <>
      <Breadcrumbs variant="list" elements={breadcrumbElements} />
      {loading && <Loader />}
      {!loading && (
        <Outlet context={{
          executor,
          catalogConnector,
          instance,
          isXtmComposerUp,
        }}
        />
      )}
    </>
  );
};

export default ExecutorsLayout;
