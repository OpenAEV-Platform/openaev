import { useEffect, useState } from 'react';
import { Outlet, useParams } from 'react-router';

import { fetchConnector } from '../../../../actions/catalog/catalog-actions';
import type { CatalogConnectorsHelper } from '../../../../actions/catalog/catalog-helper';
import { fetchCollector, fetchCollectorRelatedIds } from '../../../../actions/Collector';
import { type CollectorHelper } from '../../../../actions/collectors/collector-helper';
import { fetchConnectorInstance } from '../../../../actions/connector_instances/connector-instance-actions';
import type { ConnectorInstanceHelper } from '../../../../actions/connector_instances/connector-instance-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { useHelper } from '../../../../store';
import { type ConnectorIds } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';

const CollectorsLayout = () => {
  const { collectorId } = useParams() as { collectorId: string };
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const [loading, setLoading] = useState<boolean>(true);
  const [relatedIds, setRelatedIds] = useState<ConnectorIds>();

  useEffect(() => {
    if (!collectorId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    fetchCollectorRelatedIds(collectorId).then(({ data }: { data: ConnectorIds }) => {
      setRelatedIds(data);
      if (data.catalog_connector_id) {
        setLoading(false);
      }
    });
  }, [collectorId]);

  const { connector: catalogConnector } = useHelper((helper: CatalogConnectorsHelper) => ({ connector: helper.getCatalogConnector(relatedIds?.catalog_connector_id ?? '') }));
  const { instance } = useHelper((helper: ConnectorInstanceHelper) => ({ instance: helper.getConnectorInstance(relatedIds?.connector_instance_id ?? '') }));
  const { collector } = useHelper((helper: CollectorHelper) => ({ collector: helper.getCollector(collectorId ?? '') }));

  useDataLoader(() => {
    if (relatedIds === undefined) return;
    dispatch(fetchCollector(collectorId)).catch((error: Error) => {
      console.error(error); // TODO remove that
    });
    if (relatedIds?.catalog_connector_id) {
      dispatch(fetchConnector(relatedIds.catalog_connector_id)).finally(() => setLoading(false));
    }
    if (relatedIds?.connector_instance_id) {
      dispatch(fetchConnectorInstance(relatedIds.connector_instance_id));
    }
  }, [relatedIds?.connector_instance_id, relatedIds?.connector_instance_id]);

  const breadcrumbElements = collectorId
    ? [
        { label: t('Integrations') },
        {
          label: t('Collectors'),
          link: '/admin/integrations/collectors',
        },
        {
          label: collector?.collector_name || catalogConnector?.catalog_connector_title || 'Loading...',
          current: true,
        },
      ]
    : [
        { label: t('Integrations') },
        {
          label: t('Collectors'),
          current: true,
        },
      ];

  return (
    <>
      <Breadcrumbs variant="list" elements={breadcrumbElements} />
      {loading && <Loader />}
      {!loading && (
        <Outlet context={{
          collector,
          catalogConnector,
          instance,
        }}
        />
        // <Outlet context={{
        //   collectorId: collectorId,
        //   instanceId: relatedIds?.connector_instance_id,
        //   catalogConnectorId: relatedIds?.catalog_connector_id,
        // }}
        // />
      )}
    </>
  );
};

export default CollectorsLayout;
