import { Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type PayloadApprovalOutput } from '../../../../utils/api-types';
import ApprovalStatusChip from './ApprovalStatusChip';
import { approvalOriginLabel } from './approvalStatusUtils';

interface Props {
  /** Newest first, as the API returns it. */
  history: PayloadApprovalOutput[];
}

/**
 * Audit trail of an action's approval: one row per event (created, edited, synchronized, approved,
 * rejected) with who, when and the comment or reason. Rows, not a timeline, like the History tab of
 * the Scenario Factory payload drawer, so both read the same way.
 */
const PayloadApprovalHistory: FunctionComponent<Props> = ({ history }) => {
  const { t, nsdt } = useFormatter();
  const theme = useTheme();

  if (history.length === 0) {
    return <Text variant="content-compact" className="text-default-secondary">{t('No history yet')}</Text>;
  }

  return (
    <ol
      aria-label={t('Approval history')}
      style={{
        listStyle: 'none',
        margin: 0,
        padding: 0,
      }}
    >
      {history.map(entry => (
        <li
          key={entry.approval_id}
          style={{
            display: 'grid',
            gridTemplateColumns: 'minmax(0, 1fr) auto',
            gap: theme.spacing(0.5, 1.5),
            padding: theme.spacing(1, 0),
            borderBottom: '1px solid var(--border-elevation-subtle-soft)',
          }}
        >
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: theme.spacing(1),
            minWidth: 0,
          }}
          >
            <ApprovalStatusChip status={entry.approval_status} />
            <Text variant="content-compact">
              {[
                entry.approval_automatic && entry.approval_status === 'APPROVED' ? t('Auto-approved') : undefined,
                t(approvalOriginLabel(entry.approval_origin)),
                entry.approval_actor_name ?? t('the platform'),
              ].filter(Boolean).join(' · ')}
            </Text>
          </div>
          <Text variant="content-compact" className="text-default-secondary">{nsdt(entry.approval_created_at)}</Text>
          {entry.approval_comment && (
            <Text variant="content-compact" className="text-default-secondary" style={{ gridColumn: '1 / -1' }}>
              {entry.approval_comment}
            </Text>
          )}
        </li>
      ))}
    </ol>
  );
};

export default PayloadApprovalHistory;
