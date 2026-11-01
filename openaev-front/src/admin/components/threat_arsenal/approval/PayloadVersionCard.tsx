import { Avatar, Chip, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

import { useFormatter } from '../../../../components/i18n';
import ApprovalStatusChip from './ApprovalStatusChip';
import { type ApprovalStatus } from './approvalStatusUtils';

export interface VersionDecision {
  /** Who decided; undefined when the platform did (built-in content, migration). */
  who?: string;
  when: string;
  /** Approve comment or reject reason. */
  comment?: string;
  automatic?: boolean;
}

interface Props {
  /** Version number, shown as "v2" like the Scenario Factory version label. */
  versionNumber: number;
  status: ApprovalStatus;
  /** The approved version the platform runs today. */
  inUse?: boolean;
  /** Who submitted the version: a person, or a collector when `fromCollector`. */
  authorName?: string;
  fromCollector?: boolean;
  submittedAt?: string;
  /** This version's own decision, never another version's. */
  decision?: VersionDecision;
  /** Approve / Reject, attached to the version they act on. */
  actions?: ReactNode;
  /** Read-only note for people who cannot decide (e.g. "Waiting for an approver"). */
  note?: ReactNode;
  children?: ReactNode;
}

/**
 * One version of an action's payload, as its own object: its number, its status, who submitted it
 * and the decision taken on it. The decision buttons sit on the card of the version they decide, so
 * an approver never reads one version's decision next to another version's status.
 */
const PayloadVersionCard: FunctionComponent<Props> = ({
  versionNumber,
  status,
  inUse,
  authorName,
  fromCollector,
  submittedAt,
  decision,
  actions,
  note,
  children,
}) => {
  const { t, nsdt } = useFormatter();
  const theme = useTheme();

  let decisionLabel: string | undefined;
  if (decision) {
    if (status === 'REJECTED') {
      decisionLabel = t('Rejected by {name}', { name: decision.who ?? t('the platform') });
    } else if (decision.automatic) {
      decisionLabel = decision.who
        ? t('Auto-approved: {name} holds Approve content', { name: decision.who })
        : t('Approved by the platform');
    } else {
      decisionLabel = t('Approved by {name}', { name: decision.who ?? t('the platform') });
    }
  }

  return (
    <article
      aria-label={t('Version {number}', { number: String(versionNumber) })}
      style={{
        border: '1px solid var(--border-elevation-subtle)',
        borderRadius: 'var(--radius-sm)',
        padding: theme.spacing(2),
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(1.5),
      }}
    >
      <header style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1),
        flexWrap: 'wrap',
      }}
      >
        <Text as="h3" variant="content-base-bold" style={{ margin: 0 }}>{`v${versionNumber}`}</Text>
        <ApprovalStatusChip status={status} />
        {inUse && <Chip label={t('In use')} severity="info" />}
        {actions && (
          <div style={{
            marginLeft: 'auto',
            display: 'flex',
            gap: theme.spacing(1),
          }}
          >
            {actions}
          </div>
        )}
      </header>

      {(authorName || fromCollector) && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(1),
        }}
        >
          {fromCollector
            ? <Text variant="content-compact">{t('Synchronized by a collector')}</Text>
            : (
                <Avatar
                  name={authorName}
                  size="sm"
                  showName
                  description={t('Submitted this version')}
                />
              )}
          {submittedAt && (
            <Text variant="content-compact" className="text-default-secondary" style={{ marginLeft: 'auto' }}>
              {nsdt(submittedAt)}
            </Text>
          )}
        </div>
      )}

      {children}

      {decision && decisionLabel && (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(0.5),
          paddingTop: theme.spacing(1.5),
          borderTop: '1px solid var(--border-elevation-subtle-soft)',
        }}
        >
          <div style={{
            display: 'flex',
            gap: theme.spacing(1),
            alignItems: 'baseline',
          }}
          >
            <Text variant="content-compact">{decisionLabel}</Text>
            <Text variant="content-compact" className="text-default-secondary" style={{ marginLeft: 'auto' }}>
              {nsdt(decision.when)}
            </Text>
          </div>
          {/* An automatic approval carries a platform-written comment that repeats the label. */}
          {decision.comment && !decision.automatic && (
            <Text variant="content-base">
              {status === 'REJECTED' ? `${t('Reason')}: ${decision.comment}` : decision.comment}
            </Text>
          )}
        </div>
      )}

      {note && (
        <Text variant="content-compact" className="text-default-secondary">{note}</Text>
      )}
    </article>
  );
};

export default PayloadVersionCard;
