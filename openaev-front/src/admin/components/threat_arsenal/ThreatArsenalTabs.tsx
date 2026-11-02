import { type FunctionComponent, useMemo } from 'react';

import MarketplaceTabs, { type MarketplaceTab } from '../../../components/common/marketplace/MarketplaceTabs';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../components/i18n';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import useApprovalDesignVariant from './approval/useApprovalDesignVariant';
import { THREAT_ARSENAL_MY_CHANGES_PATH, THREAT_ARSENAL_TO_REVIEW_PATH } from './threatArsenalDeepLink';
import useThreatArsenalFacetCounts from './useThreatArsenalFacetCounts';

export type ThreatArsenalTab = 'installed' | 'available' | 'to-review' | 'my-changes';

interface Props { active: ThreatArsenalTab }

/**
 * The one set of routed tabs of the Threat Arsenal: Active and Available (Scenario Factory), then
 * the approval queue of the role: "To review" for holders of "Approve content", "My changes" for
 * people who edit without approving. In the lighter "filter" option there is no approval tab.
 */
const ThreatArsenalTabs: FunctionComponent<Props> = ({ active }) => {
  const { t } = useFormatter();
  const ability = useAbility();
  const variant = useApprovalDesignVariant();
  const canApprove = ability.can(ACTIONS.APPROVE, SUBJECTS.THREAT_ARSENALS);
  const canSubmit = !canApprove && ability.can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);

  // Counts over the whole arsenal, never the current page or filters.
  const allActions = useMemo(() => buildSearchPagination({}), []);
  const facetCounts = useThreatArsenalFacetCounts(allActions);
  const pendingCount = facetCounts?.approvals?.PENDING ?? 0;

  const tabs: MarketplaceTab[] = [
    {
      value: 'installed',
      label: t('Active'),
      to: '/admin/threat-arsenal',
    },
    {
      value: 'available',
      label: t('Available'),
      to: '/admin/threat-arsenal/available',
    },
  ];
  if (variant === 'queue' && canApprove) {
    tabs.push({
      value: 'to-review',
      label: pendingCount > 0 ? `${t('To review')} (${pendingCount})` : t('To review'),
      to: THREAT_ARSENAL_TO_REVIEW_PATH,
    });
  } else if (variant === 'queue' && canSubmit) {
    tabs.push({
      value: 'my-changes',
      label: t('My changes'),
      to: THREAT_ARSENAL_MY_CHANGES_PATH,
    });
  }

  return <MarketplaceTabs active={active} tabs={tabs} />;
};

export default ThreatArsenalTabs;
