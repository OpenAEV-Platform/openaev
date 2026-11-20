import { GetAppOutlined, RouteOutlined, UpdateOutlined } from '@mui/icons-material';
import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchCatalogScenarios } from '../../../actions/catalog/scenario-catalog-actions';
import { searchScenarios } from '../../../actions/scenarios/scenario-actions';
import { HeroStat, HeroStats } from '../../../components/common/detail/EntityDetailCommon';
import MarketplaceHero from '../../../components/common/marketplace/MarketplaceHero';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../components/i18n';
import useAuth from '../../../utils/hooks/useAuth';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import ImportFromHubButton from '../common/ImportFromHubButton';
import ScenarioCreation from './ScenarioCreation';

/**
 * The Scenarios Hero, the same on Active and Available (design system Hero: identity and the
 * section actions in the header, the summary tiles in the body), as in the Threat Arsenal. What acts
 * on one tab's list (export, import a file) sits in that tab's toolbar instead.
 */
const ScenariosHero: FunctionComponent = () => {
  const { t } = useFormatter();
  const { isXTMHubAccessible } = useAuth();
  const [stats, setStats] = useState({
    active: 0,
    notInstalled: 0,
    updates: 0,
  });
  useEffect(() => {
    Promise.all([
      searchScenarios(buildSearchPagination({ size: 1 }))
        .then(({ data }: { data: { totalElements?: number } }) => data?.totalElements ?? 0)
        .catch(() => 0),
      fetchCatalogScenarios().catch(() => []),
    ]).then(([active, catalog]) => {
      const installed = catalog.filter(item => (item.catalog_item_installed_scenarios ?? []).length > 0);
      setStats({
        active,
        notInstalled: catalog.length - installed.length,
        updates: installed.filter(item => !(item.catalog_item_installed_versions ?? []).includes(item.catalog_item_version)).length,
      });
    });
  }, []);

  return (
    <MarketplaceHero
      title={t('Scenarios')}
      subtitle={t('Browse, install and version ready-to-run scenarios.')}
      bottomSlot={(
        <HeroStats>
          <HeroStat icon={RouteOutlined} label={t('Active')} value={stats.active} to="/admin/scenarios" />
          <HeroStat icon={GetAppOutlined} label={t('Not installed')} value={stats.notInstalled} to="/admin/scenarios/available" />
          <HeroStat icon={UpdateOutlined} label={t('Updates available')} value={stats.updates} to="/admin/scenarios/available" />
        </HeroStats>
      )}
      rightSlot={(
        <>
          {isXTMHubAccessible && (
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
              <ImportFromHubButton serviceIdentifier="openaev_scenarios" />
            </Can>
          )}
          <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
            <ScenarioCreation />
          </Can>
        </>
      )}
    />
  );
};

export default ScenariosHero;
