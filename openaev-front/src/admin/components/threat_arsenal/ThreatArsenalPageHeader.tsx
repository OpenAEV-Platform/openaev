import { DoNotDisturbOnOutlined, GetAppOutlined, HourglassEmptyOutlined, LayersOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import * as qs from 'qs';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import { fetchAvailableActions } from '../../../actions/catalog/injector-contract-catalog-actions';
import { searchThreatArsenalActions } from '../../../actions/threat_arsenals/threatArsenal-actions';
import { HeroStat, HeroStats } from '../../../components/common/detail/EntityDetailCommon';
import MarketplaceHero from '../../../components/common/marketplace/MarketplaceHero';
import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../components/i18n';
import { type ThreatArsenalAction } from '../../../utils/api-types';
import { Can, useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import useApprovalDesignVariant from './approval/useApprovalDesignVariant';
import CreateThreatArsenalAction from './CreateThreatArsenalAction';
import { THREAT_ARSENAL_MY_CHANGES_PATH, THREAT_ARSENAL_TO_REVIEW_PATH } from './threatArsenalDeepLink';
import ThreatArsenalTabs from './ThreatArsenalTabs';
import useThreatArsenalFacetCounts from './useThreatArsenalFacetCounts';

type ApprovalTileStatus = 'PENDING' | 'REJECTED';

interface StatsProps {
  /** On the list itself, a tile filters it in place instead of navigating. */
  onFilterApproval?: (status: ApprovalTileStatus) => void;
}

interface HeroProps extends StatsProps {
  /** After Create: e.g. the Active list adds the new action on top. */
  onCreate?: (action: ThreatArsenalAction) => void;
}

interface Props extends HeroProps { active: 'to-review' | 'my-changes' }

// The list page reads its filters from the `query` parameter it writes itself (key "threat-arsenal"),
// so a tile on another page opens the list already filtered.
const filteredList = (status: ApprovalTileStatus) => {
  const input = buildSearchPagination({
    filterGroup: {
      mode: 'and',
      filters: [{
        id: generateFilterId(),
        key: 'action_payload_approval_status',
        operator: 'eq',
        mode: 'and',
        values: [status],
      }],
    },
    sorts: [{
      property: 'action_updated_at',
      direction: 'DESC',
    }],
  });
  return `/admin/threat-arsenal?query=${encodeURIComponent(btoa(qs.stringify({
    ...input,
    key: 'threat-arsenal',
  }, { allowEmptyArrays: true })))}`;
};

const tileButtonStyle = {
  all: 'unset',
  cursor: 'pointer',
  display: 'flex',
} as const;

/**
 * Tiles of the Threat Arsenal Hero, the same on every tab: what is active, what the catalogue still
 * offers, and the work waiting for approval. Each is a link to the tab (and filter) it counts; on the
 * Active list the approval tiles filter that list in place.
 */
export const ThreatArsenalHeroStats: FunctionComponent<StatsProps> = ({ onFilterApproval }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const ability = useAbility();
  const variant = useApprovalDesignVariant();
  const canApprove = ability.can(ACTIONS.APPROVE, SUBJECTS.THREAT_ARSENALS);
  const canSubmit = !canApprove && ability.can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  const withTabs = variant === 'queue' && (canApprove || canSubmit);

  // Counts over the whole arsenal, never the current page or filters.
  const allActions = useMemo(() => buildSearchPagination({}), []);
  const facetCounts = useThreatArsenalFacetCounts(allActions);
  const pendingCount = facetCounts?.approvals?.PENDING ?? 0;
  const rejectedCount = facetCounts?.approvals?.REJECTED ?? 0;
  const [activeCount, setActiveCount] = useState(0);
  const [notInstalledCount, setNotInstalledCount] = useState(0);
  useEffect(() => {
    searchThreatArsenalActions(buildSearchPagination({ size: 1 }))
      .then(({ data }: { data: { totalElements?: number } }) => setActiveCount(data?.totalElements ?? 0))
      .catch(() => setActiveCount(0));
    fetchAvailableActions()
      .then(items => setNotInstalledCount(items.length))
      .catch(() => setNotInstalledCount(0));
  }, []);

  return (
    <HeroStats>
      <HeroStat icon={LayersOutlined} label={t('Active')} value={activeCount} to="/admin/threat-arsenal" />
      <HeroStat icon={GetAppOutlined} label={t('Not installed')} value={notInstalledCount} to="/admin/threat-arsenal/available" />
      {[
        {
          status: 'PENDING' as const,
          label: t('Pending approval'),
          value: pendingCount,
          color: theme.palette.warning.main,
          icon: HourglassEmptyOutlined,
          // With the queue, an approver's pending work is the To review tab.
          to: withTabs && canApprove ? THREAT_ARSENAL_TO_REVIEW_PATH : undefined,
        },
        {
          status: 'REJECTED' as const,
          label: t('Rejected'),
          value: rejectedCount,
          color: theme.palette.error.main,
          icon: DoNotDisturbOnOutlined,
          to: withTabs && canSubmit ? THREAT_ARSENAL_MY_CHANGES_PATH : undefined,
        },
      ].map((tile) => {
        if (!tile.to && onFilterApproval) {
          return (
            <button
              key={tile.status}
              type="button"
              style={tileButtonStyle}
              aria-label={`${tile.value} ${tile.label}: ${t('show them in the list')}`}
              onClick={() => onFilterApproval(tile.status)}
            >
              <HeroStat icon={tile.icon} label={tile.label} value={tile.value} color={tile.color} />
            </button>
          );
        }
        return (
          <HeroStat
            key={tile.status}
            icon={tile.icon}
            label={tile.label}
            value={tile.value}
            color={tile.color}
            to={tile.to ?? filteredList(tile.status)}
          />
        );
      })}
    </HeroStats>
  );
};

/**
 * The Threat Arsenal Hero, the same on every tab (design system Hero: identity and the one section
 * action in the header, the summary tiles in the body). What acts on one tab's list (export, import,
 * install all) sits in that tab's toolbar instead, beside the list.
 */
export const ThreatArsenalHero: FunctionComponent<HeroProps> = ({ onFilterApproval, onCreate }) => {
  const { t } = useFormatter();
  return (
    <MarketplaceHero
      title={t('Threat Arsenal')}
      subtitle={t('Browse, install and version the actions your simulations run.')}
      bottomSlot={<ThreatArsenalHeroStats onFilterApproval={onFilterApproval} />}
      rightSlot={(
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.THREAT_ARSENALS}>
          <CreateThreatArsenalAction onCreate={onCreate} />
        </Can>
      )}
    />
  );
};

/** Header of the approval pages of the Threat Arsenal (To review, My changes): the Hero, then the tabs. */
const ThreatArsenalPageHeader: FunctionComponent<Props> = ({ active, onCreate }) => {
  const theme = useTheme();

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(2),
    }}
    >
      <ThreatArsenalHero onCreate={onCreate} />
      <ThreatArsenalTabs active={active} />
    </div>
  );
};

export default ThreatArsenalPageHeader;
