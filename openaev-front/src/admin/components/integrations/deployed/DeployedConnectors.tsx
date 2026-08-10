import { Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import moment from 'moment-timezone';
import { type SyntheticEvent, useMemo, useState } from 'react';

import { type CollectorHelper } from '../../../../actions/collectors/collector-helper';
import type { ExecutorHelper } from '../../../../actions/executors/executor-helper';
import { type InjectorHelper } from '../../../../actions/injectors/injector-helper';
import { type SecretsProviderHelper } from '../../../../actions/secrets_providers/secrets-provider-helper';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import type {
  CatalogConnectorOutput,
  Collector,
  CollectorOutput,
  ExecutorOutput,
  InjectorOutput, SecretsProviderOutput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { isFeatureEnabled } from '../../../../utils/utils';
import { type ConnectorItem, type ConnectorItemType } from '../catalog_connectors/catalog-facets';
import ConnectorMarketplace from '../catalog_connectors/ConnectorMarketplace';
import builtinConnectorDescription from '../common/builtinConnectorDescriptions';
import { isConnectorAlive } from '../common/connector-liveliness';
import {
  collectorConfig,
  type ConnectorContextType,
  type ConnectorOutput,
  executorConfig,
  injectorConfig,
  secretsProviderConfig,
} from '../common/ConnectorContext';
import ConnectorStatus from '../common/ConnectorStatus';
import MigrateButton from '../common/MigrateButton';
import CreateConnectorInstanceDrawer from '../connector_instance/CreateConnectorInstanceDrawer';

interface DeployedMeta {
  connector: ConnectorOutput;
  type: ConnectorItemType;
  /** The resolved catalog entry, when the connector's catalog ref matches one. */
  catalogConnector?: CatalogConnectorOutput;
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
  const { t, nsdt, locale } = useFormatter();
  const isCredentialAssetEnabled = isFeatureEnabled('CREDENTIAL_ASSET');

  useDataLoader(() => {
    dispatch(injectorConfig.apiRequest.fetchAll());
    dispatch(collectorConfig.apiRequest.fetchAll());
    dispatch(executorConfig.apiRequest.fetchAll());
    if (isCredentialAssetEnabled) {
      dispatch(secretsProviderConfig.apiRequest.fetchAll());
    }
  });

  const { executors } = useHelper((helper: ExecutorHelper) => ({ executors: helper.getExecutorsIncludingPending() }));
  const { injectors } = useHelper((helper: InjectorHelper) => ({ injectors: helper.getInjectorsIncludingPending() }));
  const { collectors } = useHelper((helper: CollectorHelper) => ({ collectors: helper.getCollectorsIncludingPending() }));
  const { secretsProviders } = useHelper((helper: SecretsProviderHelper) => ({ secretsProviders: helper.getSecretsProvidersIncludingPending() }));

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
        // Every registered connector gets a detail page, even without a catalog
        // entry: the detail page is the only place offering the delete action, so
        // gating it on the catalog stranded custom or renamed-slug connectors
        // (no way to delete or manage them at all). Only pending, instance-only
        // entries keep the legacy rule.
        const clickable = connector.canRead;
        let logoSrc: string | undefined;

        if (connector.catalog?.catalog_connector_logo_url) {
          logoSrc = `/api/images/catalog/connectors/logos/${connector.catalog.catalog_connector_logo_url}`;
        } else {
          logoSrc = config.logoUrl(connector.type);
        }
        allItems.push({
          id: connector.id,
          title: connector.name,
          // Prefer the full catalog description (the embedded catalog ref only
          // carries the short one-liner); fall back to short, then built-in.
          description: catalogMatch?.catalog_connector_description
            ?? connector.catalog?.catalog_connector_short_description
            ?? catalogMatch?.catalog_connector_short_description
            ?? (() => {
              const builtin = builtinConnectorDescription(connector.type);
              return builtin ? t(builtin) : undefined;
            })(),
          type,
          useCases: catalogMatch?.catalog_connector_use_cases ?? [],
          // Support badge: catalog verified flag (or built-in = Filigran), never
          // the output's is_verified which only means "has an instance".
          verified: connector.isFiligranVerified,
          deployedCount: connector.connectorInstance ? 1 : 0,
          logoSrc,
          detailUrl: clickable ? config.routes.detail(connector.id) : undefined,
          external: connector.isExternal,
        });
        meta.set(connector.id, {
          connector,
          type,
          catalogConnector: catalogMatch,
        });
      });
    };

    append<InjectorOutput>(injectors, injectorConfig, 'INJECTOR');
    append<CollectorOutput & Collector>(collectors, collectorConfig, 'COLLECTOR');
    append<ExecutorOutput>(executors, executorConfig, 'EXECUTOR');
    if (isCredentialAssetEnabled) {
      append<SecretsProviderOutput>(secretsProviders, secretsProviderConfig, 'SECRETS_PROVIDER');
    }

    return {
      items: allItems,
      metaById: meta,
    };
    // `t` is a new function every render (see useFormatter); `locale` is the
    // stable signal that the built-in descriptions it produces have changed.
  }, [
    injectors,
    collectors,
    executors,
    secretsProviders,
    catalogConnectors,
    locale,
    isCredentialAssetEnabled,
  ]);

  // Migrate flow: converts a manually-deployed external connector into a
  // managed instance (same behavior as the legacy per-type pages).
  const [selectedCatalogConnector, setSelectedCatalogConnector] = useState<CatalogConnectorOutput>();
  const [migrationSource, setMigrationSource] = useState<string>();
  const [openMigrateDrawer, setOpenMigrateDrawer] = useState(false);

  const onMigrateBtnClick = (e: SyntheticEvent, deployed: DeployedMeta) => {
    e.preventDefault();
    e.stopPropagation();
    // The resolved catalog entry gates the button's visibility (canMigrate), so a
    // rendered button always has one - the guard only narrows the type.
    if (!deployed.catalogConnector) return;
    setSelectedCatalogConnector(deployed.catalogConnector);
    setMigrationSource(deployed.connector.id);
    setOpenMigrateDrawer(true);
  };

  // Uniform status on every card: a glowing health disk (green = started and
  // heartbeat within the 2-minute threshold, red otherwise), the last-seen
  // date in standard color, and a compact Started / Stopped chip.
  const renderFooterAction = (item: ConnectorItem) => {
    const deployed = metaById.get(item.id);
    if (!deployed) return null;
    const { connector } = deployed;
    // A resolvable catalog entry is required to migrate: the drawer needs the
    // catalog id and configuration schema to build the managed instance. Key the
    // visibility on the entry actually resolved from the catalog list (not just
    // the connector's embedded catalog ref) so a rendered button can never be a
    // no-op click.
    const canMigrate = connector.connectorInstance == null
      && isXtmComposerUp && deployed.catalogConnector != null;
    const started = isConnectorAlive(connector);
    const diskColor = started ? theme.palette.success.main : theme.palette.error.main;
    let diskTooltip: string;

    if (!connector.isExternal) {
      diskTooltip = t('Runs inside the platform');
    } else if (connector.lastSeen) {
      diskTooltip = `${t('Last Seen')}: ${nsdt(connector.lastSeen)}`;
    } else {
      diskTooltip = t('Never updated');
    }

    return (
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1),
        minWidth: 0,
      }}
      >
        {canMigrate && <MigrateButton onMigrateBtnClick={e => onMigrateBtnClick(e, deployed)} />}
        <Tooltip title={diskTooltip}>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: theme.spacing(0.75),
            minWidth: 0,
          }}
          >
            <span style={{
              width: 8,
              height: 8,
              flexShrink: 0,
              borderRadius: '50%',
              backgroundColor: diskColor,
              boxShadow: `0 0 6px ${diskColor}`,
            }}
            />
            {connector.lastSeen && (
              <Typography
                variant="body2"
                sx={{
                  fontSize: 11,
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  color: 'text.secondary',
                }}
              >
                {moment(connector.lastSeen).fromNow()}
              </Typography>
            )}
          </div>
        </Tooltip>
        <ConnectorStatus variant={started ? 'started' : 'stopped'} />
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
        connectorTitle={selectedCatalogConnector?.catalog_connector_title}
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
