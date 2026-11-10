import { Avatar, Button, Dialog, DialogBody, DialogContent, DialogFooter, DialogTitle, Text, Textarea } from '@filigran/design-system';
import { CheckCircleOutlined, DoNotDisturbOnOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';

import { approveThreatArsenalAction, rejectThreatArsenalAction } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionOutput, type ThreatArsenalApprovalDecisionOutput } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { type Error as ApiError, notifyErrorHandler } from '../../../../utils/error/errorHandlerUtil';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { APPROVAL_COMMENT_MAX_LENGTH } from './approvalStatusUtils';
import PayloadVersionComparison from './PayloadVersionComparison';
import useVersionDetails from './useVersionDetails';

interface Props {
  version: PayloadVersionOutput;
  /** The newest approved version before this one, compared with it; none for a first version. */
  baseline?: PayloadVersionOutput;
  onDecided: (decision: ThreatArsenalApprovalDecisionOutput) => void;
}

/**
 * The decision on a version waiting for approval, inside its row of the Versions tab (combined
 * demo): who submitted it, what it changes compared with the last approved version, and Approve /
 * Reject for holders of "Approve content". The approved version keeps running until then.
 */
const VersionApprovalPanel: FunctionComponent<Props> = ({ version, baseline, onDecided }) => {
  const { t, nsdt } = useFormatter();
  const theme = useTheme();
  const canApprove = useAbility().can(ACTIONS.APPROVE, SUBJECTS.THREAT_ARSENALS);
  const [approveOpen, setApproveOpen] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [comment, setComment] = useState('');
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState<string | undefined>(undefined);
  const [sending, setSending] = useState(false);
  const actionId = version.payload_action_id;
  const details = useVersionDetails(actionId);
  const submittedBy = (() => {
    if (details.authorName) return t('Submitted by {name}', { name: details.authorName });
    return details.fromCollector ? t('Submitted by a collector') : null;
  })();
  const baselineDetails = useVersionDetails(baseline?.payload_action_id, false);

  const closeApprove = () => {
    setApproveOpen(false);
    setComment('');
  };
  const closeReject = () => {
    setRejectOpen(false);
    setReason('');
    setReasonError(undefined);
  };
  const onError = (close: () => void) => (error: ApiError) => {
    close();
    notifyErrorHandler(error);
  };

  // A version never changes once created, so the decision covers exactly the content shown.
  const handleApprove = () => {
    if (!actionId) return;
    setSending(true);
    approveThreatArsenalAction(actionId, { approval_comment: comment.trim() || undefined }, false).then((response) => {
      closeApprove();
      onDecided(response.data as ThreatArsenalApprovalDecisionOutput);
      MESSAGING$.notifySuccess(t('The new version has been approved.'));
    }).catch(onError(closeApprove)).finally(() => setSending(false));
  };

  const handleReject = () => {
    const trimmed = reason.trim();
    if (!trimmed || !actionId) {
      setReasonError(t('A reason is required to reject a version.'));
      return;
    }
    setSending(true);
    rejectThreatArsenalAction(actionId, { approval_reason: trimmed }, false).then((response) => {
      closeReject();
      onDecided(response.data as ThreatArsenalApprovalDecisionOutput);
      MESSAGING$.notifySuccess(t('The new version has been rejected.'));
    }).catch(onError(closeReject)).finally(() => setSending(false));
  };

  // What approving changes, said where the decision is taken.
  const inUse = baseline?.payload_usage_count ?? 0;
  const consequence = (() => {
    if (!canApprove) {
      return baseline
        ? t('v{version} keeps running until a person with "Approve content" approves this version.', { version: baseline.payload_version ?? '-' })
        : t('This version can be used once a person with "Approve content" approves it.');
    }
    if (!baseline) return t('Approving makes this action usable in atomic testings, scenarios and simulations.');
    return inUse > 0
      ? t('Approving makes v{version} usable. v{current} keeps running in {count, plural, one {# inject until you move it} other {# injects until you move them}}.', {
          version: version.payload_version ?? '-',
          current: baseline.payload_version ?? '-',
          count: String(inUse),
        })
      : t('Approving makes v{version} usable. Nothing runs v{current} today.', {
          version: version.payload_version ?? '-',
          current: baseline.payload_version ?? '-',
        });
  })();

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(1.5),
    }}
    >
      {/* 1. Who and when, right under the version and its status (Law of Proximity): the person first
          (design system Avatar, recognised at a glance, NN/g H6), the date as their second line. */}
      {details.authorName
        ? <Avatar size="md" name={details.authorName} showName description={[t('Submitted'), version.payload_created_at ? nsdt(version.payload_created_at) : null].filter(Boolean).join(' · ')} />
        : (
            <Text variant="content-base" className="text-default-secondary">
              {[submittedBy, version.payload_created_at ? nsdt(version.payload_created_at) : null].filter(Boolean).join(' · ')}
            </Text>
          )}
      {/* 2. The evidence: what this version changes, read before deciding. */}
      {baseline && baselineDetails.content && details.content && (
        <PayloadVersionComparison
          active={baselineDetails.content}
          pending={details.content}
          activeNumber={baseline.payload_version ?? '-'}
          pendingNumber={version.payload_version ?? '-'}
        />
      )}
      {/* 3. The decision, at the end of the reading path, with what it does (NN/g H1, H5). */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: theme.spacing(1),
        paddingTop: theme.spacing(1.5),
        borderTop: '1px solid var(--border-elevation-subtle-soft)',
      }}
      >
        <Text
          variant="content-base"
          style={{
            flex: 1,
            minWidth: 220,
          }}
        >
          {consequence}
        </Text>
        {canApprove && (
          <>
            <Button variant="destructive" priority="secondary" startIcon={<DoNotDisturbOnOutlined fontSize="small" />} onClick={() => setRejectOpen(true)}>
              {t('Reject')}
            </Button>
            <Button startIcon={<CheckCircleOutlined fontSize="small" />} onClick={() => setApproveOpen(true)}>
              {t('Approve')}
            </Button>
          </>
        )}
      </div>
      {/* Same design system dialog as the views' Rename and Delete: title, ×, the message, Cancel and the action. */}
      <Dialog open={approveOpen} onOpenChange={open => !open && closeApprove()}>
        <DialogContent size="sm">
          <DialogTitle>{t('Approve v{version}', { version: version.payload_version ?? '-' })}</DialogTitle>
          <DialogBody style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 16,
          }}
          >
            <Text variant="content-base">{consequence}</Text>
            <Textarea
              label={t('Comment')}
              placeholder={t('Optional comment kept in the approval history')}
              value={comment}
              onChange={event => setComment(event.target.value)}
              maxLength={APPROVAL_COMMENT_MAX_LENGTH}
              rows={3}
              resize="vertical"
            />
          </DialogBody>
          <DialogFooter>
            <Button priority="secondary" onClick={closeApprove}>{t('Cancel')}</Button>
            <Button loading={sending} onClick={handleApprove}>{t('Approve')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <Dialog open={rejectOpen} onOpenChange={open => !open && closeReject()}>
        <DialogContent size="sm">
          <DialogTitle>{t('Reject v{version}', { version: version.payload_version ?? '-' })}</DialogTitle>
          <DialogBody style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 16,
          }}
          >
            <Text variant="content-base">
              {baseline
                ? t('v{version} stays in use. The author sees your reason and can submit a new version.', { version: baseline.payload_version ?? '-' })
                : t('The action stays unusable. The author sees your reason and can submit a new version.')}
            </Text>
            <Textarea
              label={t('Reason')}
              required
              placeholder={t('Why this version is rejected, shown to its author')}
              value={reason}
              onChange={(event) => {
                setReason(event.target.value);
                setReasonError(undefined);
              }}
              error={reasonError}
              maxLength={APPROVAL_COMMENT_MAX_LENGTH}
              rows={4}
              resize="vertical"
            />
          </DialogBody>
          <DialogFooter>
            <Button priority="secondary" onClick={closeReject}>{t('Cancel')}</Button>
            <Button variant="destructive" loading={sending} onClick={handleReject}>{t('Reject')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default VersionApprovalPanel;
