import { Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type SyntheticEvent, useMemo, useState } from 'react';

import { type CollectorHelper } from '../../../../actions/collectors/collector-helper';
import type { ExecutorHelper } from '../../../../actions/executors/executor-helper';
import { type InjectorHelper } from '../../../../actions/injectors/injector-helper';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import type {
  CatalogConnectorOutput,
  Collector,
  CollectorOutput,
  ExecutorOutput,
  InjectorOutput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { type ConnectorItem, type ConnectorItemType } from '../catalog_connectors/catalog-facets';
import ConnectorMarketplace from '../catalog_connectors/ConnectorMarketplace';
import {
  collectorConfig,
  type ConnectorContextType,
  type ConnectorOutput,
  executorConfig,
  injectorConfig,
} from '../common/ConnectorContext';
import ConnectorStatus from '../common/ConnectorStatus';
import MigrateButton from '../common/MigrateButton';
import CreateConnectorInstanceDrawer from '../connector_instance/CreateConnectorInstanceDrawer';

interface DeployedMeta {
  connector: ConnectorOutput;
  type: ConnectorItemType;
}

interface Props {
  catalogConnectors: CatalogConnectorOutput[];
  isXtmComposerUp: boolean;
}

/**
 * The "Deployed" tab of the integrations page: every registered collector,
 * injector and executor of the platform, rendered in the same faceted
 * marketplace as the catalog. Cards link to the connector detail pages and
 * expose the instance status plus the migrate flow for legacy external
 * connectors.
 */
const DeployedConnectors = ({ catalogConnectors, isXtmComposerUp }: Props) => {
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const { t, nsdt } = useFormatter();

  useDataLoader(() => {
    dispatch(injectorConfig.apiRequest.fetchAll());
    dispatch(collectorConfig.apiRequest.fetchAll());
    dispatch(executorConfig.apiRequest.fetchAll());
  });

  const { executors } = useHelper((helper: ExecutorHelper) => ({ executors: helper.getExecutorsIncludingPending() }));
  const { injectors } = useHelper((helper: InjectorHelper) => ({ injectors: helper.getInjectorsIncludingPending() }));
  const { collectors } = useHelper((helper: CollectorHelper) => ({ collectors: helper.getCollectorsIncludingPending() }));

  const { items, metaById } = useMemo(() => {
    const catalogById = new Map(catalogConnectors.map(connector => [connector.catalog_connector_id, connector]));
    const allItems: ConnectorItem[] = [];
    const meta = new Map<string, DeployedMeta>();

    const append = <T,>(
      rawConnectors: T[],
      config: ConnectorContextType<T>,
      type: ConnectorItemType,
    ) => {
      rawConnectors.forEach((raw) => {
        const connector = config.normalizeSingle(raw);
        if (!connector.id) return;
        const catalogMatch = connector.catalog?.catalog_connector_id
          ? catalogById.get(connector.catalog.catalog_connector_id)
          : undefined;
        // Same clickability rule as the legacy per-type pages: collectors and
        // executors without a catalog entry have no detail page.
        const clickable = !(connector.catalog == null && type !== 'INJECTOR');
        let logoSrc: string | undefined;
        if (connector.isExisting) {
          logoSrc = config.logoUrl(connector.type);
        } else if (connector.catalog?.catalog_connector_logo_url) {
          logoSrc = `/api/images/catalog/connectors/logos/${connector.catalog.catalog_connector_logo_url}`;
        }
        allItems.push({
          id: connector.id,
          title: connector.name,
          description: connector.catalog?.catalog_connector_short_description
            ?? catalogMatch?.catalog_connector_short_description,
          type,
          useCases: catalogMatch?.catalog_connector_use_cases ?? [],
          verified: connector.isVerified,
          external: connector.isExternal === true,
          deployedCount: connector.connectorInstance ? 1 : 0,
          logoSrc,
          detailUrl: clickable ? config.routes.detail(connector.id) : undefined,
        });
        meta.set(connector.id, {
          connector,
          type,
        });
      });
    };

    append<InjectorOutput>(injectors, injectorConfig, 'INJECTOR');
    append<CollectorOutput & Collector>(collectors, collectorConfig, 'COLLECTOR');
    append<ExecutorOutput>(executors, executorConfig, 'EXECUTOR');

    return {
      items: allItems,
      metaById: meta,
    };
  }, [injectors, collectors, executors, catalogConnectors]);

  // Migrate flow: converts a manually-deployed external connector into a
  // managed instance (same behavior as the legacy per-type pages).
  const [selectedCatalogConnector, setSelectedCatalogConnector] = useState<CatalogConnectorOutput>();
  const [migrationSource, setMigrationSource] = useState<string>();
  const [openMigrateDrawer, setOpenMigrateDrawer] = useState(false);

  const onMigrateBtnClick = (e: SyntheticEvent, deployed: DeployedMeta) => {
    e.preventDefault();
    e.stopPropagation();
    const catalogConnector = catalogConnectors.find(
      connector => connector.catalog_connector_id === deployed.connector.catalog?.catalog_connector_id,
    );
    setSelectedCatalogConnector(catalogConnector);
    setMigrationSource(deployed.connector.id);
    setOpenMigrateDrawer(true);
  };

  const renderFooterAction = (item: ConnectorItem) => {
    const deployed = metaById.get(item.id);
    if (!deployed) return null;
    const { connector } = deployed;
    const canMigrate = connector.isExternal && connector.connectorInstance == null && isXtmComposerUp;
    const instanceStatus = connector.connectorInstance?.connector_instance_current_status;
    return (
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1),
      }}
      >
        {canMigrate && <MigrateButton onMigrateBtnClick={e => onMigrateBtnClick(e, deployed)} />}
        {connector.connectorInstance != null && <ConnectorStatus variant={instanceStatus} />}
        {connector.connectorInstance == null && (
          <Tooltip title={connector.updatedAt ? `${t('Updated at')} ${nsdt(connector.updatedAt)}` : t('Never updated')}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: theme.spacing(0.75),
            }}
            >
              <span style={{
                width: 10,
                height: 10,
                borderRadius: '50%',
                backgroundColor: connector.updatedAt ? theme.palette.success.main : theme.palette.error.main,
              }}
              />
              {connector.updatedAt && (
                <Typography
                  variant="body2"
                  sx={{
                    fontSize: 11,
                    color: 'text.secondary',
                  }}
                >
                  {nsdt(connector.updatedAt)}
                </Typography>
              )}
            </div>
          </Tooltip>
        )}
      </div>
    );
  };

  return (
    <>
      <ConnectorMarketplace
        items={items}
        renderFooterAction={renderFooterAction}
        searchPlaceholder={`${t('Search deployed integrations')}...`}
      />
      <CreateConnectorInstanceDrawer
        open={openMigrateDrawer}
        catalogConnectorId={selectedCatalogConnector ? selectedCatalogConnector.catalog_connector_id : ''}
        catalogConnectorSlug={selectedCatalogConnector ? selectedCatalogConnector.catalog_connector_slug : ''}
        onClose={() => setOpenMigrateDrawer(false)}
        connectorType={selectedCatalogConnector?.catalog_connector_type}
        disabled={!isXtmComposerUp && selectedCatalogConnector?.catalog_connector_manager_supported}
        migrationSource={migrationSource}
        disabledMessage={t('Deployment of this {catalogType} requires the installation of our Integration Manager.', { catalogType: selectedCatalogConnector ? selectedCatalogConnector.catalog_connector_type.toLowerCase() : '' })}
      />
    </>
  );
};

export default DeployedConnectors;
