import { GetAppOutlined, LayersOutlined } from '@mui/icons-material';
import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchAvailableActions } from '../../../actions/catalog/injector-contract-catalog-actions';
import { searchThreatArsenalActions } from '../../../actions/threat_arsenals/threatArsenal-actions';
import { HeroStat, HeroStats } from '../../../components/common/detail/EntityDetailCommon';
import MarketplaceHero from '../../../components/common/marketplace/MarketplaceHero';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../components/i18n';
import { type ThreatArsenalAction } from '../../../utils/api-types';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import CreateThreatArsenalAction from './CreateThreatArsenalAction';

interface Props {
  /** After Create: e.g. the Active list adds the new action on top. */
  onCreate?: (action: ThreatArsenalAction) => void;
}

/**
 * The Threat Arsenal Hero, the same on Active and Available (design system Hero: identity and the
 * section action in the header, the summary tiles in the body), as on the Scenarios page. What acts on
 * one tab's list (export, import, install all) sits in that tab's toolbar instead, beside the list.
 */
const ThreatArsenalHero: FunctionComponent<Props> = ({ onCreate }) => {
  const { t } = useFormatter();
  // Counts over the whole arsenal and catalogue, never the current page or filters.
  const [stats, setStats] = useState({
    active: 0,
    notInstalled: 0,
  });
  useEffect(() => {
    Promise.all([
      searchThreatArsenalActions(buildSearchPagination({ size: 1 }))
        .then(({ data }: { data: { totalElements?: number } }) => data?.totalElements ?? 0)
        .catch(() => 0),
      fetchAvailableActions()
        .then(items => items.length)
        .catch(() => 0),
    ]).then(([active, notInstalled]) => setStats({
      active,
      notInstalled,
    }));
  }, []);

  return (
    <MarketplaceHero
      title={t('Threat Arsenal')}
      subtitle={t('Browse, install and version the actions your simulations run.')}
      bottomSlot={(
        <HeroStats>
          <HeroStat icon={LayersOutlined} label={t('Active')} value={stats.active} to="/admin/threat-arsenal" />
          <HeroStat icon={GetAppOutlined} label={t('Not installed')} value={stats.notInstalled} to="/admin/threat-arsenal/available" />
        </HeroStats>
      )}
      rightSlot={(
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.THREAT_ARSENALS}>
          <CreateThreatArsenalAction onCreate={onCreate} />
        </Can>
      )}
    />
  );
};

export default ThreatArsenalHero;
