import { Button, Switch } from '@filigran/design-system';
import { CategoryOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type ReactNode, useCallback, useEffect, useState } from 'react';

import {
  fetchAvailableActions,
  fetchCatalogSettings,
  installAllCatalogPayloads,
  installCatalogPayload,
  updateCatalogSettings,
} from '../../../../actions/catalog/payload-catalog-actions';
import { FACET_PLATFORMS, platformFacetIcon } from '../../../../components/common/facets/facetPlatforms';
import MarketplaceFacetSidebar, { type MarketplaceFacet } from '../../../../components/common/marketplace/filter/MarketplaceFacetSidebar';
import useMarketplaceFilters from '../../../../components/common/marketplace/filter/useMarketplaceFilters';
import useNotifyInstalled from '../../../../components/common/marketplace/install/useNotifyInstalled';
import MarketplaceBrowser from '../../../../components/common/marketplace/page/MarketplaceBrowser';
import MarketplaceCatalogTabs from '../../../../components/common/marketplace/page/MarketplaceCatalogTabs';
import MarketplaceSections from '../../../../components/common/marketplace/results/MarketplaceSections';
import MarketplaceToolbar from '../../../../components/common/marketplace/toolbar/MarketplaceToolbar';
import { useFormatter } from '../../../../components/i18n';
import { type AvailableActionOutput, type AvailablePayloadActionOutput, type CatalogSettingsOutput } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { DOMAIN_ICON_MAP } from '../../common/domains/DomainsIcons';
import ThreatArsenalHero from '../page/ThreatArsenalHero';
import ThreatArsenalEmptyState from '../ThreatArsenalEmptyState';
import { availableActionFilter, availableActionSorts, byKind, useActionName } from './availableActions';
import ThreatArsenalCatalogCard from './ThreatArsenalCatalogCard';
import ThreatArsenalCatalogDrawer from './ThreatArsenalCatalogDrawer';
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

const FACETS: MarketplaceFacet<'domains' | 'platforms'>[] = [
  {
    key: 'domains',
    label: 'Domains',
    icon: domain => DOMAIN_ICON_MAP[domain]?.(),
  },
  {
    key: 'platforms',
    label: 'Platform',
    values: FACET_PLATFORMS,
    icon: platformFacetIcon,
  },
];

const domainOf = (action: AvailableActionOutput) => action.action_domain;

// The catalogue actions the tenant does not have yet.
const ThreatArsenalAvailable = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const nameOf = useActionName();
  const notifyInstalled = useNotifyInstalled();
  const ability = useAbility();
  const canInstall = ability.can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  const canChangeSettings = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  const [actions, setActions] = useState<AvailableActionOutput[]>([]);
  const [loading, setLoading] = useState(true);
  const [settings, setSettings] = useState<CatalogSettingsOutput | null>(null);
  const [installedIds, setInstalledIds] = useState<Set<string>>(new Set());
  const [installingIds, setInstallingIds] = useState<Set<string>>(new Set());
  const [installingAll, setInstallingAll] = useState(false);
  const [opened, setOpened] = useState<AvailablePayloadActionOutput | null>(null);
  const { shown, filtered, resetFilters, facetState, toolbar } = useMarketplaceFilters({
    items: actions,
    facetFilter: availableActionFilter,
    sortValues: availableActionSorts(nameOf),
    defaultSort: 'name',
    groupKey: domainOf,
  });

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
  const changeAutoInstall = (payloadAutoInstall: boolean) => settings && updateCatalogSettings({
    payload_auto_install: payloadAutoInstall,
    scenario_auto_install: settings.scenario_auto_install,
  }).then(setSettings).catch(() => undefined);
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

  return (
    <>
      <MarketplaceBrowser
        hero={<ThreatArsenalHero />}
        tabs={<MarketplaceCatalogTabs active="available" baseUrl="/admin/threat-arsenal" />}
        sidebar={<MarketplaceFacetSidebar facets={FACETS} {...facetState} />}
        toolbar={(
          <MarketplaceToolbar
            {...toolbar}
            sortOptions={SORT_OPTIONS}
            descendingFields={['publishedAt']}
            actions={(
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
              </div>
            )}
          />
        )}
      >
        <MarketplaceSections
          items={shown}
          loading={loading}
          sections={{
            sectionKey: domainOf,
            sectionLabel: domain => (domain ? t(domain) : t('Other')),
            sectionIcon: domain => (domain && DOMAIN_ICON_MAP[domain]?.()) || <CategoryOutlined />,
          }}
          emptyState={(
            <ThreatArsenalEmptyState
              hasFilters={filtered}
              onResetFilters={resetFilters}
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
