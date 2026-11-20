import { Button, Switch } from '@filigran/design-system';
import { CategoryOutlined } from '@mui/icons-material';
import { type ReactNode, useCallback, useEffect, useState } from 'react';

import {
  fetchAvailableActions,
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
import { type AvailableActionOutput, type AvailablePayloadActionOutput, type CatalogSettingsOutput } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { DOMAIN_ICON_MAP } from '../../common/domains/DomainsIcons';
import ThreatArsenalHero from '../page/ThreatArsenalHero';
import ThreatArsenalTabs from '../page/ThreatArsenalTabs';
import ThreatArsenalEmptyState from '../ThreatArsenalEmptyState';
import {
  type AvailableActionFilters,
  type AvailableActionSort,
  byKind,
  type FacetKey,
  filterActions,
  hasFilters,
  NO_FILTERS,
  type SortField,
  useActionName,
} from './availableActions';
import ThreatArsenalCatalogCard from './ThreatArsenalCatalogCard';
import ThreatArsenalCatalogDrawer from './ThreatArsenalCatalogDrawer';
import ThreatArsenalCatalogSidebar from './ThreatArsenalCatalogSidebar';
import ThreatArsenalContractCatalogCard from './ThreatArsenalContractCatalogCard';

const SORT_OPTIONS = [
  {
    field: 'name',
    label: 'Name',
  },
  {
    field: 'publishedAt',
    label: 'Published',
  },
];

// The catalogue actions the tenant does not have yet, filtered and sorted in memory: the catalogue is small.
const ThreatArsenalAvailable = () => {
  const { t } = useFormatter();
  const nameOf = useActionName();
  const ability = useAbility();
  const canInstall = ability.can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  const canChangeSettings = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  const [actions, setActions] = useState<AvailableActionOutput[]>([]);
  const [loading, setLoading] = useState(true);
  const [settings, setSettings] = useState<CatalogSettingsOutput | null>(null);
  const [filters, setFilters] = useState<AvailableActionFilters>(NO_FILTERS);
  const [sort, setSort] = useState<AvailableActionSort>({
    field: 'name',
    asc: true,
  });
  const [installedIds, setInstalledIds] = useState<Set<string>>(new Set());
  const [installingIds, setInstallingIds] = useState<Set<string>>(new Set());
  const [installingAll, setInstallingAll] = useState(false);
  const [opened, setOpened] = useState<AvailablePayloadActionOutput | null>(null);

  const loadActions = useCallback(() => fetchAvailableActions()
    .then((loaded) => {
      setActions(loaded);
      setInstalledIds(new Set());
    })
    .catch(() => undefined)
    .finally(() => setLoading(false)), []);

  useEffect(() => {
    loadActions();
    fetchCatalogSettings().then(setSettings).catch(() => undefined);
  }, [loadActions]);

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

  const markInstalled = (action: AvailablePayloadActionOutput, version: string) => {
    setInstalledIds(previous => new Set([...previous, action.action_id]));
    MESSAGING$.notifySuccess(t('{name} v{version} installed', {
      name: nameOf(action),
      version,
    }));
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
  const installAll = () => {
    setInstallingAll(true);
    installAllCatalogPayloads()
      .then(({ installed_ids }) => {
        MESSAGING$.notifySuccess(t('{count} actions installed', { count: installed_ids?.length ?? 0 }));
        return loadActions();
      })
      .catch(() => undefined)
      .finally(() => setInstallingAll(false));
  };
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
  const shown = filterActions(actions, filters, sort, nameOf);
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
        sidebar={<ThreatArsenalCatalogSidebar actions={actions} filters={filters} onToggle={toggleFacet} />}
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
            <MarketplaceSortSelect sortHelpers={sortHelpers} options={SORT_OPTIONS} descendingFields={['publishedAt']} />
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
            sectionKey: action => action.action_domain,
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
          renderItem={card}
        />
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
