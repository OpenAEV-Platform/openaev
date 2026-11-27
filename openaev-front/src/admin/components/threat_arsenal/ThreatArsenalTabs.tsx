import { Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { type FunctionComponent, useMemo } from 'react';
import { Link } from 'react-router';

import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../components/i18n';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import useApprovalDesignVariant from './approval/useApprovalDesignVariant';
import { THREAT_ARSENAL_MY_CHANGES_PATH, THREAT_ARSENAL_TO_REVIEW_PATH } from './threatArsenalDeepLink';
import useThreatArsenalFacetCounts from './useThreatArsenalFacetCounts';

export type ThreatArsenalTab = 'installed' | 'available' | 'to-review' | 'my-changes';

interface Props { active: ThreatArsenalTab }

const ACTIVE_PATH = '/admin/threat-arsenal';

/**
 * The routed tabs of the Threat Arsenal: Active, Available and the review tab of the role ("To review"
 * for holders of "Approve content", "My changes" for people who edit without approving).
 */
const ThreatArsenalTabs: FunctionComponent<Props> = ({ active }) => {
  const { t } = useFormatter();
  const ability = useAbility();
  const variant = useApprovalDesignVariant();
  const canApprove = ability.can(ACTIONS.APPROVE, SUBJECTS.THREAT_ARSENALS);
  const canSubmit = !canApprove && ability.can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  const reviewMode: 'to-review' | 'my-changes' | null = (() => {
    if (variant !== 'queue') return null;
    if (canApprove) return 'to-review';
    return canSubmit ? 'my-changes' : null;
  })();
  const reviewPath = reviewMode === 'to-review' ? THREAT_ARSENAL_TO_REVIEW_PATH : THREAT_ARSENAL_MY_CHANGES_PATH;

  // Counts over the whole arsenal, never the current page or filters.
  const allActions = useMemo(() => buildSearchPagination({}), []);
  const facetCounts = useThreatArsenalFacetCounts(allActions);
  const pendingCount = facetCounts?.approvals?.PENDING ?? 0;

  const tab = (tabValue: string, label: string, to: string) => (
    <TabsTrigger key={tabValue} value={tabValue} asChild>
      <Link to={to} aria-current={active === tabValue ? 'page' : undefined}>{label}</Link>
    </TabsTrigger>
  );

  return (
    <Tabs value={active} panels="external">
      <TabsList>
        {tab('installed', t('Active'), ACTIVE_PATH)}
        {tab('available', t('Available'), '/admin/threat-arsenal/available')}
        {reviewMode === 'to-review' && tab('to-review', pendingCount > 0 ? `${t('To review')} (${pendingCount})` : t('To review'), reviewPath)}
        {reviewMode === 'my-changes' && tab('my-changes', t('My changes'), reviewPath)}
      </TabsList>
    </Tabs>
  );
};

export default ThreatArsenalTabs;
