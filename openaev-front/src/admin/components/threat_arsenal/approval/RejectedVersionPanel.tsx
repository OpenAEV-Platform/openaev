import { Text } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionOutput } from '../../../../utils/api-types';
import PayloadVersionComparison from './PayloadVersionComparison';
import useVersionChanges from './useVersionChanges';
import useVersionDetails from './useVersionDetails';

interface Props {
  version: PayloadVersionOutput;
  /** The approved version it was compared with, when there is one. */
  baseline?: PayloadVersionOutput;
}

/**
 * A rejected version in the Versions tab: who rejected it and when, the reason (required when rejecting),
 * and what was proposed, with the same Before / After as a version waiting for approval.
 */
const RejectedVersionPanel: FunctionComponent<Props> = ({ version, baseline }) => {
  const { t, nsdt } = useFormatter();
  const details = useVersionDetails(version.payload_action_id);
  const changes = useVersionChanges(version.payload_action_id, baseline?.payload_action_id);
  // The latest rejection of this version (history newest first).
  const rejection = details.history.find(entry => entry.approval_status === 'REJECTED') ?? null;

  const who = [
    details.authorName ? t('Submitted by {name}', { name: details.authorName }) : null,
    rejection ? t('Rejected by {name}', { name: rejection.approval_actor_name ?? t('Platform') }) : null,
    rejection ? nsdt(rejection.approval_created_at) : null,
  ].filter(Boolean).join(' · ');

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 12,
    }}
    >
      {who && <Text variant="content-caption" className="text-default-secondary">{who}</Text>}
      {rejection?.approval_comment && (
        <div style={{
          padding: 12,
          borderRadius: 4,
          border: '1px solid var(--border-elevation-subtle-soft)',
        }}
        >
          <Text variant="content-caption" className="text-default-secondary" style={{ fontWeight: 600 }}>{t('Reason')}</Text>
          <Text variant="content-base">{rejection.approval_comment}</Text>
        </div>
      )}
      {/* What was proposed: against the version in use, or, for a first version, everything as added. */}
      {changes && (
        <PayloadVersionComparison
          title={t('What was proposed')}
          changes={changes}
          isNew={!baseline}
          activeNumber={baseline?.payload_version ?? undefined}
          pendingNumber={version.payload_version ?? '-'}
        />
      )}
    </div>
  );
};

export default RejectedVersionPanel;
