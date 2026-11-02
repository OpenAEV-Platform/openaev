import { DoNotDisturbOnOutlined, HourglassEmptyOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import * as qs from 'qs';
import { type FunctionComponent, useMemo } from 'react';

import { HeroStat, HeroStats } from '../../../components/common/detail/EntityDetailCommon';
import MarketplaceHero from '../../../components/common/marketplace/MarketplaceHero';
import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../components/i18n';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import ApprovalRolesNote from './approval/ApprovalRolesNote';
import useApprovalDesignVariant from './approval/useApprovalDesignVariant';
import { THREAT_ARSENAL_MY_CHANGES_PATH, THREAT_ARSENAL_TO_REVIEW_PATH } from './threatArsenalDeepLink';
import ThreatArsenalTabs from './ThreatArsenalTabs';
import useThreatArsenalFacetCounts from './useThreatArsenalFacetCounts';

type ApprovalTileStatus = 'PENDING' | 'REJECTED';

interface StatsProps {
  /** On the list itself, a tile filters it in place instead of navigating. */
  onFilterApproval?: (status: ApprovalTileStatus) => void;
}

interface Props { active: 'to-review' | 'my-changes' }

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
 * Approval tiles of the Threat Arsenal Hero: the work waiting for approval, each a link (one click
 * from anywhere in the arsenal) or, on the list itself, a filter of that list.
 */
export const ThreatArsenalApprovalStats: FunctionComponent<StatsProps> = ({ onFilterApproval }) => {
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

  return (
    <HeroStats>
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
 * Header of the approval pages of the Threat Arsenal (To review, My changes), on the design system
 * Hero shared with the Scenario Factory marketplaces, above the one Threat Arsenal tab bar.
 */
const ThreatArsenalPageHeader: FunctionComponent<Props> = ({ active }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(2),
    }}
    >
      <MarketplaceHero
        title={t('Threat Arsenal')}
        subtitle={t('Browse, install and version the actions your simulations run.')}
        bottomSlot={<ThreatArsenalApprovalStats />}
      />
      <ThreatArsenalTabs active={active} />
      <ApprovalRolesNote />
    </div>
  );
};

export default ThreatArsenalPageHeader;
