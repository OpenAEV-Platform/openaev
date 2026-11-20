import { Button, Switch } from '@filigran/design-system';
import { CategoryOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type ReactNode, useEffect, useState } from 'react';

import {
  fetchAvailableActionFacetCounts,
  fetchCatalogSettings,
  installAllCatalogPayloads,
  installCatalogPayload,
  searchAvailableActions,
  updateCatalogSettings,
} from '../../../../actions/catalog/payload-catalog-actions';
import MarketplaceCardMeta from '../../../../components/common/marketplace/card/MarketplaceCardMeta';
import {
  CATALOG_ITEM_DEFAULT_SORT,
  CATALOG_ITEM_NAME,
  CATALOG_ITEM_PLATFORM_FACET,
  CATALOG_ITEM_PLATFORMS,
  CATALOG_ITEM_PUBLISHED,
  CATALOG_ITEM_SORT_OPTIONS,
} from '../../../../components/common/marketplace/filter/catalogItemSearch';
import MarketplaceFacetSidebar, { type MarketplaceFacet } from '../../../../components/common/marketplace/filter/MarketplaceFacetSidebar';
import useMarketplaceSearch from '../../../../components/common/marketplace/filter/useMarketplaceSearch';
import useNotifyInstalled from '../../../../components/common/marketplace/install/useNotifyInstalled';
import MarketplaceBrowser from '../../../../components/common/marketplace/page/MarketplaceBrowser';
import MarketplaceCatalogTabs from '../../../../components/common/marketplace/page/MarketplaceCatalogTabs';
import { bySection } from '../../../../components/common/marketplace/results/groupSections';
import MarketplaceList, { type MarketplaceColumn, type MarketplaceRowTarget } from '../../../../components/common/marketplace/results/MarketplaceList';
import MarketplaceLoadMore from '../../../../components/common/marketplace/results/MarketplaceLoadMore';
import MarketplaceSections from '../../../../components/common/marketplace/results/MarketplaceSections';
import MarketplaceSortSelect from '../../../../components/common/marketplace/toolbar/MarketplaceSortSelect';
import MarketplaceViewToggle from '../../../../components/common/marketplace/toolbar/MarketplaceViewToggle';
import useMarketplaceView from '../../../../components/common/marketplace/toolbar/useMarketplaceView';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { useFormatter } from '../../../../components/i18n';
import { type AvailableActionOutput, type AvailablePayloadActionOutput, type CatalogSettingsOutput } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { DOMAIN_ICON_MAP } from '../../common/domains/DomainsIcons';
import ThreatArsenalHero from '../page/ThreatArsenalHero';
import ThreatArsenalEmptyState from '../ThreatArsenalEmptyState';
import AvailableActionIcon from './AvailableActionIcon';
import { byKind, integrationUrl, useActionName } from './availableActions';
import CatalogPayloadInstallAction from './CatalogPayloadInstallAction';
import ThreatArsenalCatalogCard from './ThreatArsenalCatalogCard';
import ThreatArsenalCatalogDrawer from './ThreatArsenalCatalogDrawer';
import ThreatArsenalContractCatalogCard from './ThreatArsenalContractCatalogCard';

const DOMAIN = 'catalog_item_domain';

const FACETS: MarketplaceFacet[] = [
  {
    key: DOMAIN,
    label: 'Domains',
    icon: domain => DOMAIN_ICON_MAP[domain]?.(),
  },
  CATALOG_ITEM_PLATFORM_FACET,
];

const domainOf = (action: AvailableActionOutput) => action.action_domain;

// The catalogue actions the tenant does not have yet: payloads first, then the actions of integrations.
const ThreatArsenalAvailable = () => {
  const { t, nsd } = useFormatter();
  const theme = useTheme();
  const nameOf = useActionName();
  const notifyInstalled = useNotifyInstalled();
  const ability = useAbility();
  const canInstall = ability.can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  const canChangeSettings = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  const { viewMode, handleViewModeChange } = useMarketplaceView('threat-arsenal-catalog:view-mode');
  const [settings, setSettings] = useState<CatalogSettingsOutput | null>(null);
  const [installedIds, setInstalledIds] = useState<Set<string>>(new Set());
  const [installingIds, setInstallingIds] = useState<Set<string>>(new Set());
  const [installingAll, setInstallingAll] = useState(false);
  const [opened, setOpened] = useState<AvailablePayloadActionOutput | null>(null);
  const catalog = useMarketplaceSearch<AvailableActionOutput>({
    storageKey: 'threat-arsenal-catalog',
    defaultSort: CATALOG_ITEM_DEFAULT_SORT,
    search: searchAvailableActions,
    fetchFacetCounts: fetchAvailableActionFacetCounts,
  });
  const { queryableHelpers, searchPaginationInput } = catalog;

  useEffect(() => {
    fetchCatalogSettings().then(setSettings).catch(() => undefined);
  }, []);

  const markInstalled = (action: AvailablePayloadActionOutput, version: string) => {
    setInstalledIds(previous => new Set([...previous, action.action_id]));
    notifyInstalled(nameOf(action), version);
  };
  const install = (action: AvailablePayloadActionOutput) => {
    const id = action.action_id;
    const version = action.action_payload_version;
    setInstallingIds(previous => new Set([...previous, id]));
    installCatalogPayload(id, version)
      .then(() => markInstalled(action, version))
      .catch(() => undefined)
      .finally(() => setInstallingIds(previous => new Set([...previous].filter(other => other !== id))));
  };
  // The server installs every payload never installed, on every page.
  const installAll = () => {
    setInstallingAll(true);
    installAllCatalogPayloads()
      .then(({ installed_ids }) => {
        MESSAGING$.notifySuccess(t('{count} actions installed', { count: installed_ids?.length ?? 0 }));
        setInstalledIds(new Set());
        catalog.reload();
      })
      .catch(() => undefined)
      .finally(() => setInstallingAll(false));
  };
  const changeAutoInstall = (payloadAutoInstall: boolean) => settings && updateCatalogSettings({
    payload_auto_install: payloadAutoInstall,
    scenario_auto_install: settings.scenario_auto_install,
  }).then(setSettings).catch(() => undefined);

  const installAction = (action: AvailablePayloadActionOutput) => (
    <CatalogPayloadInstallAction
      installed={installedIds.has(action.action_id)}
      installing={installingIds.has(action.action_id)}
      canInstall={canInstall}
      onInstall={() => install(action)}
    />
  );
  const card = byKind<ReactNode>({
    PAYLOAD: action => (
      <ThreatArsenalCatalogCard
        key={`${action.action_kind}-${action.action_id}`}
        action={action}
        name={nameOf(action)}
        installed={installedIds.has(action.action_id)}
        installing={installingIds.has(action.action_id)}
        canInstall={canInstall}
        onInstall={() => install(action)}
        onOpen={() => setOpened(action)}
      />
    ),
    INTEGRATION: action => (
      <ThreatArsenalContractCatalogCard
        key={`${action.action_kind}-${action.action_id}`}
        action={action}
        name={nameOf(action)}
      />
    ),
  });
  const columns: MarketplaceColumn<AvailableActionOutput>[] = [
    {
      field: CATALOG_ITEM_NAME,
      label: 'Name',
      sortable: true,
      width: '40%',
      value: nameOf,
    },
    {
      field: DOMAIN,
      label: 'Domain',
      width: '20%',
      value: action => (action.action_domain ? t(action.action_domain) : '–'),
    },
    {
      field: CATALOG_ITEM_PLATFORMS,
      label: 'Platforms',
      width: '20%',
      value: action => <MarketplaceCardMeta platforms={action.action_platforms} />,
    },
    {
      field: CATALOG_ITEM_PUBLISHED,
      label: 'Published',
      sortable: true,
      width: '20%',
      value: byKind<ReactNode>({
        PAYLOAD: action => (action.action_payload_published_at ? nsd(action.action_payload_published_at) : '–'),
        INTEGRATION: () => '–',
      }),
    },
  ];
  const sections = {
    sectionKey: domainOf,
    sectionLabel: (domain: string | undefined) => (domain ? t(domain) : t('Other')),
    sectionIcon: (domain: string | undefined) => (domain && DOMAIN_ICON_MAP[domain]?.()) || <CategoryOutlined />,
  };
  // Each loaded page joins the sections of the previous ones.
  const actions = [...catalog.items].sort(bySection(domainOf));
  const emptyState = (
    <ThreatArsenalEmptyState
      hasFilters={catalog.filtered}
      onResetFilters={catalog.resetFilters}
      empty={{
        title: t('Every catalog action is installed'),
        description: t('New Filigran actions show here as soon as they are published.'),
      }}
    />
  );

  return (
    <>
      <MarketplaceBrowser
        hero={<ThreatArsenalHero />}
        tabs={<MarketplaceCatalogTabs active="available" baseUrl="/admin/threat-arsenal" />}
        sidebar={(
          <MarketplaceFacetSidebar
            facets={FACETS}
            facetCounts={catalog.facetCounts}
            searchPaginationInput={searchPaginationInput}
            filterHelpers={queryableHelpers.filterHelpers}
          />
        )}
        toolbar={(
          <PaginationComponentV2
            {...catalog.pagination}
            entityPrefix="catalog_payload"
            availableFilterNames={[DOMAIN, CATALOG_ITEM_PLATFORMS]}
            filtersEndSlot={viewMode === 'grid'
              ? <MarketplaceSortSelect sortHelpers={queryableHelpers.sortHelpers} options={CATALOG_ITEM_SORT_OPTIONS} descendingFields={[CATALOG_ITEM_PUBLISHED]} />
              : null}
            topBarButtons={(
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: theme.spacing(1),
              }}
              >
                {canChangeSettings && settings && (
                  <Switch
                    label={t('Install new actions automatically')}
                    checked={settings.payload_auto_install}
                    onCheckedChange={changeAutoInstall}
                  />
                )}
                {canInstall && <Button priority="secondary" onClick={installAll} loading={installingAll}>{t('Install all')}</Button>}
                <MarketplaceViewToggle value={viewMode} onChange={handleViewModeChange} />
              </div>
            )}
          />
        )}
      >
        {viewMode === 'grid'
          ? (
              <MarketplaceSections
                items={actions}
                loading={catalog.loadingFirstPage}
                sections={sections}
                emptyState={emptyState}
                renderItem={card}
              />
            )
          : (
              <MarketplaceList
                items={actions}
                loading={catalog.loadingFirstPage}
                sections={sections}
                emptyState={emptyState}
                columns={columns}
                sortHelpers={queryableHelpers.sortHelpers}
                rowKey={action => `${action.action_kind}-${action.action_id}`}
                icon={action => <AvailableActionIcon action={action} />}
                open={byKind<MarketplaceRowTarget>({
                  PAYLOAD: action => ({ onClick: () => setOpened(action) }),
                  INTEGRATION: action => ({ to: integrationUrl(action) }),
                })}
                action={byKind<ReactNode>({
                  PAYLOAD: installAction,
                  INTEGRATION: () => null,
                })}
              />
            )}
        <MarketplaceLoadMore {...catalog.loadMore} />
      </MarketplaceBrowser>
      <ThreatArsenalCatalogDrawer
        action={opened}
        canInstall={canInstall && !(opened && installedIds.has(opened.action_id))}
        onClose={() => setOpened(null)}
        onInstalled={version => opened && markInstalled(opened, version)}
      />
    </>
  );
};

export default ThreatArsenalAvailable;
