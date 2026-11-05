import { Text } from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchThreatArsenalActionApprovals } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadApprovalOutput, type PayloadVersionOutput } from '../../../../utils/api-types';
import { tint } from '../../../../utils/tint';
import PayloadVersionComparison from './PayloadVersionComparison';

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
  const [rejection, setRejection] = useState<PayloadApprovalOutput | null>(null);

  useEffect(() => {
    if (!version.payload_action_id) return undefined;
    let cancelled = false;
    fetchThreatArsenalActionApprovals(version.payload_action_id)
      .then((response) => {
        const entries = (response.data as PayloadApprovalOutput[]).filter(entry => entry.approval_status === 'REJECTED');
        // Newest first from the API; sorted again so the latest rejection is the one shown.
        entries.sort((a, b) => b.approval_created_at.localeCompare(a.approval_created_at));
        if (!cancelled) setRejection(entries[0] ?? null);
      })
      .catch(() => {
        if (!cancelled) setRejection(null);
      });
    return () => {
      cancelled = true;
    };
  }, [version.payload_action_id]);

  const who = [
    version.payload_author_name ? t('Submitted by {name}', { name: version.payload_author_name }) : null,
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
          background: tint('var(--text-default-primary)', 4),
        }}
        >
          <Text variant="content-caption" className="text-default-secondary" style={{ fontWeight: 600 }}>{t('Reason')}</Text>
          <Text variant="content-base">{rejection.approval_comment}</Text>
        </div>
      )}
      {baseline?.payload_content && version.payload_content && (
        <PayloadVersionComparison
          title={t('What was proposed')}
          active={baseline.payload_content}
          pending={version.payload_content}
          activeNumber={baseline.payload_version ?? '-'}
          pendingNumber={version.payload_version ?? '-'}
        />
      )}
      {!baseline && version.payload_content?.content && (
        <div>
          <Text variant="content-compact-bold">{t('First version — what it runs')}</Text>
          <pre style={{
            margin: '6px 0 0',
            padding: 12,
            borderRadius: 4,
            border: '1px solid var(--border-elevation-subtle-soft)',
            background: tint('var(--text-default-primary)', 4),
            fontFamily: 'monospace',
            fontSize: 12,
            whiteSpace: 'pre-wrap',
            overflowWrap: 'anywhere',
          }}
          >
            {version.payload_content.content}
          </pre>
        </div>
      )}
    </div>
  );
};

export default RejectedVersionPanel;
