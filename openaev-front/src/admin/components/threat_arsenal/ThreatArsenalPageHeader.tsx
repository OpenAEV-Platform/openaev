import { DoNotDisturbOnOutlined, HourglassEmptyOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useMemo } from 'react';

import MarketplaceHero from '../../../components/common/marketplace/MarketplaceHero';
import MarketplaceTabs from '../../../components/common/marketplace/MarketplaceTabs';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../components/i18n';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { THREAT_ARSENAL_MY_CHANGES_PATH, THREAT_ARSENAL_TO_REVIEW_PATH } from './threatArsenalDeepLink';
import useThreatArsenalFacetCounts from './useThreatArsenalFacetCounts';

interface Props {
  active: 'all' | 'to-review' | 'my-changes';
  totalElements?: number;
  rightSlot?: ReactNode;
}

/**
 * Header of the Threat Arsenal pages, on the design system Hero shared with the Scenario Factory
 * marketplaces: the title, the work waiting for approval as stat tiles (visible as soon as the page
 * opens, without filtering), and the tabs. Holders of "Approve content" get "To review" (what waits
 * for their decision); people who edit without approving get "My changes" (what they submitted and
 * what was decided). Tabs a role cannot use are hidden, not greyed out.
 */
const ThreatArsenalPageHeader: FunctionComponent<Props> = ({ active, totalElements, rightSlot }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const ability = useAbility();
  const canApprove = ability.can(ACTIONS.APPROVE, SUBJECTS.THREAT_ARSENALS);
  const canSubmit = !canApprove && ability.can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);

  // Counts over the whole arsenal, never the current page or filters.
  const allActions = useMemo(() => buildSearchPagination({}), []);
  const facetCounts = useThreatArsenalFacetCounts(allActions);
  const pendingCount = facetCounts?.approvals?.PENDING ?? 0;
  const rejectedCount = facetCounts?.approvals?.REJECTED ?? 0;

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(2),
    }}
    >
      <MarketplaceHero
        title={t('Threat Arsenal')}
        totalLabel={totalElements === undefined ? undefined : t('total actions')}
        totalElements={totalElements}
        stats={[
          {
            id: 'pending',
            label: t('Pending approval'),
            value: pendingCount,
            color: theme.palette.warning.main,
            icon: HourglassEmptyOutlined,
          },
          {
            id: 'rejected',
            label: t('Rejected'),
            value: rejectedCount,
            color: theme.palette.error.main,
            icon: DoNotDisturbOnOutlined,
          },
        ]}
        rightSlot={rightSlot}
      />
      {(canApprove || canSubmit) && (
        <MarketplaceTabs
          active={active}
          tabs={[
            {
              value: 'all',
              label: t('All actions'),
              to: '/admin/threat-arsenal',
            },
            canApprove
              ? {
                  value: 'to-review',
                  label: pendingCount > 0 ? `${t('To review')} (${pendingCount})` : t('To review'),
                  to: THREAT_ARSENAL_TO_REVIEW_PATH,
                }
              : {
                  value: 'my-changes',
                  label: t('My changes'),
                  to: THREAT_ARSENAL_MY_CHANGES_PATH,
                },
          ]}
        />
      )}
    </div>
  );
};

export default ThreatArsenalPageHeader;
