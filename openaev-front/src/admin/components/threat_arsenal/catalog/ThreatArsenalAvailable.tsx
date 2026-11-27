import { CategoryOutlined } from '@mui/icons-material';
import { type ReactNode, useMemo, useState } from 'react';

import { fetchAvailableActionFacetCounts, installAllCatalogPayloads, searchAvailableActions } from '../../../../actions/catalog/payload-catalog-actions';
import MarketplaceCardMeta from '../../../../components/common/marketplace/card/MarketplaceCardMeta';
import {
  CATALOG_ITEM_DEFAULT_SORT,
  CATALOG_ITEM_DESCRIPTION,
  CATALOG_ITEM_NAME,
  CATALOG_ITEM_PLATFORM_FACET,
  CATALOG_ITEM_PLATFORMS,
  CATALOG_ITEM_PUBLISHED,
} from '../../../../components/common/marketplace/filter/catalogItemSearch';
import MarketplaceFacetSidebar, { type MarketplaceFacet } from '../../../../components/common/marketplace/filter/MarketplaceFacetSidebar';
import useMarketplaceSearch from '../../../../components/common/marketplace/filter/useMarketplaceSearch';
import MarketplaceBrowser from '../../../../components/common/marketplace/page/MarketplaceBrowser';
import MarketplaceCatalogTabs from '../../../../components/common/marketplace/page/MarketplaceCatalogTabs';
import { bySection } from '../../../../components/common/marketplace/results/groupSections';
import MarketplaceList, { type MarketplaceColumn, type MarketplaceRowTarget } from '../../../../components/common/marketplace/results/MarketplaceList';
import MarketplaceLoadMore from '../../../../components/common/marketplace/results/MarketplaceLoadMore';
import MarketplaceSections from '../../../../components/common/marketplace/results/MarketplaceSections';
import MarketplaceCatalogToolbar from '../../../../components/common/marketplace/toolbar/MarketplaceCatalogToolbar';
import useMarketplaceView from '../../../../components/common/marketplace/toolbar/useMarketplaceView';
import { useFormatter } from '../../../../components/i18n';
import { THREAT_ARSENAL_BASE_URL } from '../../../../constants/BaseUrls';
import { type AvailableActionOutput, type AvailablePayloadActionOutput } from '../../../../utils/api-types';
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
import usePayloadInstall from './usePayloadInstall';

const DOMAIN = 'catalog_item_domain';
const AUTHOR = 'catalog_item_author';

const FACETS: MarketplaceFacet[] = [
  {
    key: DOMAIN,
    label: 'Domains',
    icon: domain => DOMAIN_ICON_MAP[domain]?.(),
  },
  CATALOG_ITEM_PLATFORM_FACET,
  {
    key: AUTHOR,
    label: 'Provider',
  },
];

const domainOf = (action: AvailableActionOutput) => action.action_domain;

// The catalogue actions the tenant does not have yet: payloads first, then the actions of integrations.
const ThreatArsenalAvailable = () => {
  const { t, nsd } = useFormatter();
  const nameOf = useActionName();
  const canInstall = useAbility().can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  const view = useMarketplaceView('threat-arsenal-catalog:view-mode');
  const payloads = usePayloadInstall();
  const [opened, setOpened] = useState<AvailablePayloadActionOutput | null>(null);
  const catalog = useMarketplaceSearch<AvailableActionOutput>({
    storageKey: 'threat-arsenal-catalog',
    defaultSort: CATALOG_ITEM_DEFAULT_SORT,
    search: searchAvailableActions,
    fetchFacetCounts: fetchAvailableActionFacetCounts,
  });
  const { queryableHelpers, searchPaginationInput } = catalog;

  const installAction = (action: AvailablePayloadActionOutput) => (
    <CatalogPayloadInstallAction
      installed={payloads.isInstalled(action)}
      installing={payloads.isInstalling(action)}
      canInstall={canInstall}
      onInstall={() => payloads.install(action)}
      size="sm"
    />
  );
  const card = byKind<ReactNode>({
    PAYLOAD: action => (
      <ThreatArsenalCatalogCard
        key={`${action.action_kind}-${action.action_id}`}
        action={action}
        name={nameOf(action)}
        installed={payloads.isInstalled(action)}
        installing={payloads.isInstalling(action)}
        canInstall={canInstall}
        onInstall={() => payloads.install(action)}
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
      width: '25%',
      value: nameOf,
    },
    {
      field: DOMAIN,
      label: 'Domain',
      width: '15%',
      value: action => (action.action_domain ? t(action.action_domain) : '–'),
    },
    {
      field: CATALOG_ITEM_DESCRIPTION,
      label: 'Description',
      width: '30%',
      value: byKind<ReactNode>({
        PAYLOAD: action => action.action_payload_description || '–',
        INTEGRATION: () => '–',
      }),
    },
    {
      field: CATALOG_ITEM_PLATFORMS,
      label: 'Platforms',
      width: '15%',
      value: action => <MarketplaceCardMeta platforms={action.action_platforms} />,
    },
    {
      field: CATALOG_ITEM_PUBLISHED,
      label: 'Published',
      sortable: true,
      width: '15%',
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
  const actions = useMemo(() => [...catalog.items].sort(bySection(domainOf)), [catalog.items]);
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
        tabs={<MarketplaceCatalogTabs active="available" baseUrl={THREAT_ARSENAL_BASE_URL} />}
        sidebar={(
          <MarketplaceFacetSidebar
            facets={FACETS}
            facetCounts={catalog.facetCounts}
            searchPaginationInput={searchPaginationInput}
            filterHelpers={queryableHelpers.filterHelpers}
          />
        )}
        toolbar={(
          <MarketplaceCatalogToolbar
            catalog={catalog}
            view={view}
            entityPrefix="catalog_payload"
            filterNames={[DOMAIN, CATALOG_ITEM_PLATFORMS, AUTHOR]}
            install={{
              autoInstallSetting: 'payload_auto_install',
              autoInstallHelp: t('Installs each new Filigran action as soon as it is published.'),
              canInstall,
              installAll: installAllCatalogPayloads,
              installedMessage: count => t('{count} actions installed', { count }),
              onInstalledAll: () => {
                payloads.forgetInstalled();
                catalog.reload();
              },
            }}
          />
        )}
      >
        {view.viewMode === 'grid'
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
      {opened && (
        <ThreatArsenalCatalogDrawer
          key={opened.action_id}
          action={opened}
          name={nameOf(opened)}
          installed={payloads.isInstalled(opened)}
          installing={payloads.isInstalling(opened)}
          canInstall={canInstall}
          onClose={() => setOpened(null)}
          onInstall={version => payloads.install(opened, version).then(installed => installed && setOpened(null))}
        />
      )}
    </>
  );
};

export default ThreatArsenalAvailable;
