import { Button, Text, Textarea } from '@filigran/design-system';
import { CheckCircleOutlined, DoNotDisturbOnOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';

import { approveThreatArsenalAction, rejectThreatArsenalAction } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import DialogConfirmation from '../../../../components/common/DialogConfirmation';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionOutput, type ThreatArsenalActionFullOutput } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { type Error as ApiError, notifyErrorHandler } from '../../../../utils/error/errorHandlerUtil';
import { fdsLayerClass, layerInputVars, SURFACE_LAYER } from '../../../../utils/fdsLayer';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { APPROVAL_COMMENT_MAX_LENGTH } from './approvalStatusUtils';
import PayloadVersionComparison from './PayloadVersionComparison';

interface Props {
  version: PayloadVersionOutput;
  /** The newest approved version before this one, compared with it; none for a first version. */
  baseline?: PayloadVersionOutput;
  onDecided: (updated: ThreatArsenalActionFullOutput) => void;
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
  const actionId = version.payload_action_id;

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

  // Bound to the content shown here: the server refuses the approval if it changed meanwhile.
  const handleApprove = () => {
    if (!actionId) return undefined;
    return approveThreatArsenalAction(actionId, {
      approval_fingerprint: version.payload_approval_fingerprint ?? '',
      approval_comment: comment.trim() || undefined,
    }, false).then((response) => {
      closeApprove();
      onDecided(response.data as ThreatArsenalActionFullOutput);
      MESSAGING$.notifySuccess(t('The new version has been approved.'));
    }).catch(onError(closeApprove));
  };

  const handleReject = (resetLoading?: () => void) => {
    const trimmed = reason.trim();
    if (!trimmed || !actionId) {
      setReasonError(t('A reason is required to reject a version.'));
      resetLoading?.();
      return undefined;
    }
    return rejectThreatArsenalAction(actionId, { approval_reason: trimmed }, false).then((response) => {
      closeReject();
      onDecided(response.data as ThreatArsenalActionFullOutput);
      MESSAGING$.notifySuccess(t('The new version has been rejected.'));
    }).catch(onError(closeReject));
  };

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(1.5),
    }}
    >
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1),
      }}
      >
        <Text variant="content-compact" className="text-default-secondary" style={{ marginRight: 'auto' }}>
          {[
            version.payload_author_name ? t('Submitted by {name}', { name: version.payload_author_name }) : t('Submitted by a collector'),
            version.payload_created_at ? nsdt(version.payload_created_at) : null,
          ].filter(Boolean).join(' · ')}
        </Text>
        {canApprove && (
          <>
            <Button size="sm" variant="destructive" priority="secondary" startIcon={<DoNotDisturbOnOutlined fontSize="small" />} onClick={() => setRejectOpen(true)}>
              {t('Reject')}
            </Button>
            <Button size="sm" startIcon={<CheckCircleOutlined fontSize="small" />} onClick={() => setApproveOpen(true)}>
              {t('Approve')}
            </Button>
          </>
        )}
      </div>
      {!canApprove && (
        <Text variant="content-compact" className="text-default-secondary">
          {baseline
            ? t('v{version} keeps running until a person with "Approve content" approves this version.', { version: baseline.payload_version ?? '-' })
            : t('This version can be used once a person with "Approve content" approves it.')}
        </Text>
      )}
      {baseline?.payload_content && version.payload_content && (
        <PayloadVersionComparison
          active={baseline.payload_content}
          pending={version.payload_content}
          activeNumber={baseline.payload_version ?? '-'}
          pendingNumber={version.payload_version ?? '-'}
        />
      )}
      <DialogConfirmation
        open={approveOpen}
        handleClose={closeApprove}
        handleSubmit={handleApprove}
        text={t('Approve v{version}? It can then be used, and injects can move to it.', { version: version.payload_version ?? '-' })}
        submitLabel={t('Approve')}
        extraContent={(
          <div
            className={fdsLayerClass(SURFACE_LAYER)}
            style={{
              ...layerInputVars,
              marginTop: theme.spacing(2),
            }}
          >
            <Textarea
              label={t('Comment')}
              placeholder={t('Optional comment kept in the approval history')}
              value={comment}
              onChange={event => setComment(event.target.value)}
              maxLength={APPROVAL_COMMENT_MAX_LENGTH}
              rows={3}
              resize="vertical"
            />
          </div>
        )}
      />
      <DialogConfirmation
        open={rejectOpen}
        handleClose={closeReject}
        handleSubmit={handleReject}
        text={t('Reject v{version}? The approved version stays in use.', { version: version.payload_version ?? '-' })}
        submitLabel={t('Reject')}
        submitColor="error"
        extraContent={(
          <div
            className={fdsLayerClass(SURFACE_LAYER)}
            style={{
              ...layerInputVars,
              marginTop: theme.spacing(2),
            }}
          >
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
          </div>
        )}
      />
    </div>
  );
};

export default VersionApprovalPanel;
