import { useEffect, useState } from 'react';
import { Outlet, useParams } from 'react-router';

import { fetchConnector, isXtmComposerIsReachable } from '../../../../actions/catalog/catalog-actions';
import type { CatalogConnectorsHelper } from '../../../../actions/catalog/catalog-helper';
import { fetchConnectorInstance } from '../../../../actions/connector_instances/connector-instance-actions';
import type { ConnectorInstanceHelper } from '../../../../actions/connector_instances/connector-instance-helper';
import { fetchInjector, fetchInjectorRelatedIds, fetchInjectors } from '../../../../actions/injectors/injector-action';
import { type InjectorHelper } from '../../../../actions/injectors/injector-helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { useHelper } from '../../../../store';
import {
  type CatalogConnectorOutput,
  type ConnectorIds,
  type ConnectorInstance, type Injector,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';

export type InjectorsContextType = {
  injector: Injector;
  instance: ConnectorInstance;
  catalogConnector: CatalogConnectorOutput;
  isXtmComposerUp: boolean;
};

const InjectorsLayout = () => {
  const { injectorId } = useParams() as { injectorId: string };
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const [loading, setLoading] = useState<boolean>(true);
  const [relatedIds, setRelatedIds] = useState<ConnectorIds>();
  const [isXtmComposerUp, setIsXtmComposerUp] = useState<boolean>(false);

  useEffect(() => {
    isXtmComposerIsReachable().then(({ data }) => setIsXtmComposerUp(data));
    if (!injectorId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    fetchInjectorRelatedIds(injectorId).then(({ data }: { data: ConnectorIds }) => {
      setRelatedIds(data);
      setLoading(false);
    });
  }, [injectorId]);

  const { connector: catalogConnector } = useHelper((helper: CatalogConnectorsHelper) => ({ connector: helper.getCatalogConnector(relatedIds?.catalog_connector_id ?? '') }));
  const { instance } = useHelper((helper: ConnectorInstanceHelper) => ({ instance: helper.getConnectorInstance(relatedIds?.connector_instance_id ?? '') }));
  const { injector } = useHelper((helper: InjectorHelper) => ({ injector: helper.getInjector(injectorId ?? '') }));

  useDataLoader(() => {
    dispatch(fetchInjectors(true));

    if (relatedIds === undefined) return;
    dispatch(fetchInjector(injectorId));

    if (relatedIds?.catalog_connector_id) {
      dispatch(fetchConnector(relatedIds.catalog_connector_id)).finally(() => setLoading(false));
    }
    if (relatedIds?.connector_instance_id) {
      dispatch(fetchConnectorInstance(relatedIds.connector_instance_id));
    }
  }, [relatedIds?.connector_instance_id, relatedIds?.connector_instance_id]);

  const breadcrumbElements = injectorId
    ? [
        { label: t('Integrations') },
        {
          label: t('Injectors'),
          link: '/admin/integrations/injectors',
        },
        {
          label: injector?.injector_name || catalogConnector?.catalog_connector_title || 'Loading...',
          current: true,
        },
      ]
    : [
        { label: t('Integrations') },
        {
          label: t('Injectors'),
          current: true,
        },
      ];

  return (
    <>
      <Breadcrumbs variant="list" elements={breadcrumbElements} />
      {loading && <Loader />}
      {!loading && (
        <Outlet context={{
          injector,
          catalogConnector,
          instance,
          isXtmComposerUp,
        }}
        />
      )}
    </>
  );
};

export default InjectorsLayout;
