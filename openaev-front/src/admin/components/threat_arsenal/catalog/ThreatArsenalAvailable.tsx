import { Button, Switch } from '@filigran/design-system';
import { CategoryOutlined } from '@mui/icons-material';
import { useCallback, useEffect, useState } from 'react';

import {
  fetchCatalogPayloads,
  fetchCatalogSettings,
  installAllCatalogPayloads,
  installCatalogPayload,
  updateCatalogSettings,
} from '../../../../actions/catalog/payload-catalog-actions';
import MarketplaceBrowser from '../../../../components/common/marketplace/page/MarketplaceBrowser';
import MarketplaceSections from '../../../../components/common/marketplace/results/MarketplaceSections';
import MarketplaceResultsCount from '../../../../components/common/marketplace/toolbar/MarketplaceResultsCount';
import MarketplaceSortSelect from '../../../../components/common/marketplace/toolbar/MarketplaceSortSelect';
import { type SortHelpers } from '../../../../components/common/queryable/sort/SortHelpers';
import { useFormatter } from '../../../../components/i18n';
import SearchFilter from '../../../../components/SearchFilter';
import { type CatalogPayloadOutput, type CatalogSettingsOutput } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { DOMAIN_ICON_MAP } from '../../common/domains/DomainsIcons';
import ThreatArsenalHero from '../page/ThreatArsenalHero';
import ThreatArsenalTabs from '../page/ThreatArsenalTabs';
import ThreatArsenalEmptyState from '../ThreatArsenalEmptyState';
import {
  type CatalogPayloadFilters,
  type CatalogPayloadSort,
  type FacetKey,
  filterPayloads,
  hasFilters,
  NO_FILTERS,
  type SortField,
} from './catalogPayloadFilters';
import ThreatArsenalCatalogCard from './ThreatArsenalCatalogCard';
import ThreatArsenalCatalogDrawer from './ThreatArsenalCatalogDrawer';
import ThreatArsenalCatalogSidebar from './ThreatArsenalCatalogSidebar';

const SORT_OPTIONS = [
  {
    field: 'catalog_payload_name',
    label: 'Name',
  },
  {
    field: 'catalog_payload_published_at',
    label: 'Published',
  },
];

// The catalogue payloads the tenant has not installed, filtered and sorted in memory: the catalogue is small.
const ThreatArsenalAvailable = () => {
  const { t } = useFormatter();
  const ability = useAbility();
  const canInstall = ability.can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  const canChangeSettings = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  const [payloads, setPayloads] = useState<CatalogPayloadOutput[]>([]);
  const [loading, setLoading] = useState(true);
  const [settings, setSettings] = useState<CatalogSettingsOutput | null>(null);
  const [filters, setFilters] = useState<CatalogPayloadFilters>(NO_FILTERS);
  const [sort, setSort] = useState<CatalogPayloadSort>({
    field: 'catalog_payload_name',
    asc: true,
  });
  const [installedIds, setInstalledIds] = useState<Set<string>>(new Set());
  const [installingIds, setInstallingIds] = useState<Set<string>>(new Set());
  const [installingAll, setInstallingAll] = useState(false);
  const [opened, setOpened] = useState<CatalogPayloadOutput | null>(null);

  const loadPayloads = useCallback(() => fetchCatalogPayloads()
    .then((loaded) => {
      setPayloads(loaded);
      setInstalledIds(new Set());
    })
    .catch(() => undefined)
    .finally(() => setLoading(false)), []);

  useEffect(() => {
    loadPayloads();
    fetchCatalogSettings().then(setSettings).catch(() => undefined);
  }, [loadPayloads]);

  const sortHelpers: SortHelpers = {
    getSortBy: () => sort.field,
    getSortAsc: () => sort.asc,
    handleDirectedSort: (field, asc) => setSort({
      field: field as SortField,
      asc,
    }),
    handleSort: field => setSort({
      field: field as SortField,
      asc: field === sort.field ? !sort.asc : true,
    }),
  };
  const toggleFacet = (key: FacetKey, value: string) => setFilters(previous => ({
    ...previous,
    [key]: previous[key].includes(value) ? previous[key].filter(v => v !== value) : [...previous[key], value],
  }));
  const shown = filterPayloads(payloads, filters, sort);

  const markInstalled = (version: CatalogPayloadOutput) => {
    setInstalledIds(previous => new Set([...previous, version.catalog_payload_external_id]));
    MESSAGING$.notifySuccess(t('{name} v{version} installed', {
      name: version.catalog_payload_name ?? version.catalog_payload_external_id,
      version: version.catalog_payload_version,
    }));
  };
  const install = (payload: CatalogPayloadOutput) => {
    const id = payload.catalog_payload_external_id;
    setInstallingIds(previous => new Set([...previous, id]));
    installCatalogPayload(id, payload.catalog_payload_version)
      .then(() => markInstalled(payload))
      .catch(() => undefined)
      .finally(() => setInstallingIds(previous => new Set([...previous].filter(other => other !== id))));
  };
  const installAll = () => {
    setInstallingAll(true);
    installAllCatalogPayloads()
      .then(({ installed_ids }) => {
        MESSAGING$.notifySuccess(t('{count} actions installed', { count: installed_ids?.length ?? 0 }));
        return loadPayloads();
      })
      .catch(() => undefined)
      .finally(() => setInstallingAll(false));
  };
  const changeAutoInstall = (payloadAutoInstall: boolean) => settings && updateCatalogSettings({
    payload_auto_install: payloadAutoInstall,
    scenario_auto_install: settings.scenario_auto_install,
  }).then(setSettings).catch(() => undefined);

  return (
    <>
      <MarketplaceBrowser
        hero={(
          <ThreatArsenalHero action={(
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: 16,
            }}
            >
              {canChangeSettings && settings && (
                <Switch
                  label={t('Install new actions automatically')}
                  checked={settings.payload_auto_install}
                  onCheckedChange={changeAutoInstall}
                />
              )}
              {canInstall && <Button onClick={installAll} loading={installingAll}>{t('Install all')}</Button>}
            </div>
          )}
          />
        )}
        tabs={<ThreatArsenalTabs active="available" />}
        sidebar={<ThreatArsenalCatalogSidebar payloads={payloads} filters={filters} onToggle={toggleFacet} />}
        toolbar={(
          <div style={{
            display: 'flex',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 12,
          }}
          >
            <SearchFilter
              variant="small"
              keyword={filters.keyword}
              onChange={keyword => setFilters(previous => ({
                ...previous,
                keyword: keyword ?? '',
              }))}
            />
            <MarketplaceSortSelect sortHelpers={sortHelpers} options={SORT_OPTIONS} descendingFields={['catalog_payload_published_at']} />
            <div style={{ marginLeft: 'auto' }}>
              <MarketplaceResultsCount count={shown.length} />
            </div>
          </div>
        )}
      >
        <MarketplaceSections
          items={shown}
          loading={loading}
          sections={{
            sectionKey: payload => payload.catalog_payload_domain,
            sectionLabel: domain => (domain ? t(domain) : t('Other')),
            sectionIcon: domain => (domain && DOMAIN_ICON_MAP[domain]?.()) || <CategoryOutlined />,
          }}
          emptyState={(
            <ThreatArsenalEmptyState
              hasFilters={hasFilters(filters)}
              onResetFilters={() => setFilters(NO_FILTERS)}
              empty={{
                title: t('Every catalog action is installed'),
                description: t('New Filigran actions show here as soon as they are published.'),
              }}
            />
          )}
          renderItem={payload => (
            <ThreatArsenalCatalogCard
              key={payload.catalog_payload_external_id}
              payload={payload}
              installed={installedIds.has(payload.catalog_payload_external_id)}
              installing={installingIds.has(payload.catalog_payload_external_id)}
              canInstall={canInstall}
              onInstall={() => install(payload)}
              onOpen={() => setOpened(payload)}
            />
          )}
        />
      </MarketplaceBrowser>
      <ThreatArsenalCatalogDrawer
        payload={opened}
        canInstall={canInstall && !(opened && installedIds.has(opened.catalog_payload_external_id))}
        onClose={() => setOpened(null)}
        onInstalled={markInstalled}
      />
    </>
  );
};

export default ThreatArsenalAvailable;
