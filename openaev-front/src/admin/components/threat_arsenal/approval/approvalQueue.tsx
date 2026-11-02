import { Avatar, Button, Card, CardHeader, CardTitle, Chip, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type ComponentType, type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import ApprovalStatusChip from './ApprovalStatusChip';
import { displayedApprovalStatus } from './approvalStatusUtils';
import { isFromCollector, type QueueItem } from './useApprovalQueue';

interface RowProps {
  item: QueueItem;
  /** Label of the row's button, e.g. "Review" for an approver, "Open" for an author. */
  actionLabel: string;
  /** For an author's own changes: show the status and, once rejected, the reason. */
  showDecision?: boolean;
  onOpen: () => void;
}

/**
 * One version waiting for (or having had) a decision, as the same row everywhere it is listed:
 * what it is (new action or new version), who submitted it, when, and the action that opens it.
 */
export const ApprovalQueueRow: FunctionComponent<RowProps> = ({ item, actionLabel, showDecision, onOpen }) => {
  const { t, nsdt, tPick } = useFormatter();
  const theme = useTheme();
  const pendingVersion = item.full?.action_pending_version;
  const isNewVersion = !!pendingVersion || !!item.action.action_payload?.payload_pending_version;
  const latest = item.full?.action_approval_latest;
  const author = pendingVersion?.version_author_name ?? latest?.approval_actor_name;
  const submittedAt = pendingVersion?.version_created_at ?? latest?.approval_created_at ?? item.action.injector_contract_updated_at;
  const status = displayedApprovalStatus(item.full?.action_approval_status ?? item.action.action_payload?.payload_approval_status, isNewVersion);
  // Scenario Factory versions (combined demo): a later version of an installed action is its own row.
  const payload = item.action.action_payload;
  const isLaterVersion = (payload?.payload_installed_versions ?? 0) > 1;
  let versionLabel: string | undefined;
  if (pendingVersion?.version_number) versionLabel = `v${pendingVersion.version_number}`;
  else if (isLaterVersion && payload?.payload_version) versionLabel = `v${payload.payload_version}`;
  const shownAsVersion = isNewVersion || isLaterVersion;
  const rejectionReason = status === 'REJECTED' && latest?.approval_status === 'REJECTED' ? latest.approval_comment : undefined;

  return (
    <Card padding={16} gap={8} onClick={onOpen}>
      <CardHeader action={<Button size="sm" priority="secondary" onClick={onOpen}>{actionLabel}</Button>}>
        <CardTitle as="h3">{tPick(item.action.action_labels)}</CardTitle>
      </CardHeader>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1),
        flexWrap: 'wrap',
      }}
      >
        {showDecision && <ApprovalStatusChip status={status} />}
        <Chip
          label={shownAsVersion
            ? t('New version {version}', { version: versionLabel ?? '' }).trim()
            : t('New action')}
          severity={showDecision ? undefined : 'medium'}
        />
        {shownAsVersion && status === 'PENDING' && (
          <Text variant="content-compact" className="text-default-secondary">
            {showDecision ? t('The approved version keeps running meanwhile') : t('The approved version keeps running until you decide')}
          </Text>
        )}
        <span style={{
          marginLeft: 'auto',
          display: 'inline-flex',
          alignItems: 'center',
          gap: theme.spacing(1),
        }}
        >
          {isFromCollector(item)
            ? <Text variant="content-compact">{t('Synchronized by a collector')}</Text>
            : <Avatar name={author} size="xs" showName />}
          {submittedAt && (
            <Text variant="content-compact" className="text-default-secondary">{nsdt(submittedAt)}</Text>
          )}
        </span>
      </div>
      {rejectionReason && (
        <Text variant="content-compact">{`${t('Reason')}: ${rejectionReason}`}</Text>
      )}
    </Card>
  );
};

interface GroupProps {
  title: string;
  icon: ComponentType<{ fontSize?: 'small' }>;
  items: QueueItem[];
  actionLabel: string;
  showDecision?: boolean;
  onOpen: (item: QueueItem) => void;
}

export const ApprovalQueueGroup: FunctionComponent<GroupProps> = ({ title, icon: Icon, items, actionLabel, showDecision, onOpen }) => {
  const theme = useTheme();
  if (items.length === 0) return null;
  return (
    <section
      aria-label={title}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(1),
      }}
    >
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1),
      }}
      >
        <Icon fontSize="small" />
        <Text as="h2" variant="title-xs" style={{ margin: 0 }}>{`${title} (${items.length})`}</Text>
      </div>
      {items.map(item => (
        <ApprovalQueueRow
          key={item.action.injector_contract_id}
          item={item}
          actionLabel={actionLabel}
          showDecision={showDecision}
          onOpen={() => onOpen(item)}
        />
      ))}
    </section>
  );
};
