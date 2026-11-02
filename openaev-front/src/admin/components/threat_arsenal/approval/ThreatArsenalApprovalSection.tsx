import { Button, Text, Textarea, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { CheckCircleOutlined, DoNotDisturbOnOutlined, FactCheckOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactElement, useEffect, useState } from 'react';

import { fetchMe } from '../../../../actions/Application';
import {
  approveThreatArsenalAction,
  fetchThreatArsenalActionUsage,
  rejectThreatArsenalAction,
} from '../../../../actions/threat_arsenals/threatArsenal-actions';
import DialogConfirmation from '../../../../components/common/DialogConfirmation';
import Field from '../../../../components/common/overview/Field';
import Section from '../../../../components/common/overview/Section';
import { useFormatter } from '../../../../components/i18n';
import {
  type PayloadApprovalOutput,
  type ThreatArsenalActionFullOutput,
  type ThreatArsenalActionUsageOutput,
  type ThreatArsenalApprovalDecisionOutput,
} from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { type Error as ApiError, notifyErrorHandler } from '../../../../utils/error/errorHandlerUtil';
import { fdsLayerClass, layerInputVars, SURFACE_LAYER } from '../../../../utils/fdsLayer';
import { useAppDispatch } from '../../../../utils/hooks';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, PERMISSION_REQUIRED, SUBJECTS } from '../../../../utils/permissions/types';
import ApprovalStatusChip from './ApprovalStatusChip';
import { APPROVAL_COMMENT_MAX_LENGTH, approvalOriginLabel, isPayloadUsed } from './approvalStatusUtils';
import PayloadUsage from './PayloadUsage';

interface Props {
  action: ThreatArsenalActionFullOutput;
  /** Called with the decision (new status and, after an approval, the move offer). */
  onDecided: (decision: ThreatArsenalApprovalDecisionOutput) => void;
}

/**
 * Approval block of an action drawer, for the payload version shown: its status, the latest
 * decision (who approved or rejected, with the comment) and the Approve / Reject actions for holders
 * of "Approve content". A version never changes once created, so the decision covers exactly what it
 * runs; the History tab shows what changed since the previous version. No history list here: the
 * full history stays available through the API. Injects stay on their version: after an approval,
 * the drawer asks the approver whether to move the injects of the version it replaces.
 */
const ThreatArsenalApprovalSection: FunctionComponent<Props> = ({ action, onDecided }) => {
  const { t, nsdt } = useFormatter();
  const theme = useTheme();
  const ability = useAbility();
  const dispatch = useAppDispatch();
  const canApprove = ability.can(ACTIONS.APPROVE, SUBJECTS.THREAT_ARSENALS);

  const [approveOpen, setApproveOpen] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [comment, setComment] = useState('');
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState<string | undefined>(undefined);
  const [usage, setUsage] = useState<ThreatArsenalActionUsageOutput | undefined>(undefined);

  const status = action.action_approval_status;
  const latest = action.action_approval_latest;
  const version = action.action_payload_version;
  const isPending = status === 'PENDING';

  // Capabilities are loaded at app start: refresh them before offering a decision, so a user whose
  // Approve content was removed meanwhile sees the buttons disabled (the server refuses anyway).
  useEffect(() => {
    if (isPending) {
      dispatch(fetchMe());
    }
  }, [action.action_id, isPending]);

  const onDecisionError = (error: ApiError, close: () => void) => {
    if (error?.status === 403) {
      close();
      dispatch(fetchMe());
      MESSAGING$.notifyError(t('You can no longer approve or reject payloads: Approve content was removed from your role.'));
      return;
    }
    notifyErrorHandler(error);
  };

  // Where the payload is used: information only (an approval change never blocks it anymore).
  useEffect(() => {
    let cancelled = false;
    fetchThreatArsenalActionUsage(action.action_id)
      .then((response) => {
        if (!cancelled) setUsage(response.data as ThreatArsenalActionUsageOutput);
      })
      .catch(() => {
        if (!cancelled) setUsage(undefined);
      });
    return () => {
      cancelled = true;
    };
  }, [action.action_id]);

  const closeApprove = () => {
    setApproveOpen(false);
    setComment('');
  };
  const closeReject = () => {
    setRejectOpen(false);
    setReason('');
    setReasonError(undefined);
  };

  const handleApprove = () => approveThreatArsenalAction(action.action_id, { approval_comment: comment.trim() || undefined }, false).then((response) => {
    closeApprove();
    onDecided(response.data as ThreatArsenalApprovalDecisionOutput);
    MESSAGING$.notifySuccess(t('Version v{version} has been approved.', { version: version ?? '' }));
  }).catch(error => onDecisionError(error, closeApprove));

  const handleReject = (resetLoading?: () => void) => {
    const trimmed = reason.trim();
    if (!trimmed) {
      setReasonError(t('A reason is required to reject a version.'));
      resetLoading?.();
      return undefined;
    }
    return rejectThreatArsenalAction(action.action_id, { approval_reason: trimmed }, false).then((response) => {
      closeReject();
      onDecided(response.data as ThreatArsenalApprovalDecisionOutput);
      MESSAGING$.notifySuccess(t('Version v{version} has been rejected.', { version: version ?? '' }));
    }).catch(error => onDecisionError(error, closeReject));
  };

  // A disabled button fires no pointer event, so the tooltip hangs on an enabled wrapper.
  const withPermissionTooltip = (button: ReactElement) => (canApprove
    ? button
    : (
        <Tooltip>
          <TooltipTrigger asChild>
            <span style={{ display: 'inline-flex' }}>{button}</span>
          </TooltipTrigger>
          <TooltipContent>{t(PERMISSION_REQUIRED)}</TooltipContent>
        </Tooltip>
      ));

  const decisionActions = isPending && (
    <div style={{
      display: 'flex',
      gap: theme.spacing(1),
    }}
    >
      {withPermissionTooltip(
        <Button
          type="button"
          size="sm"
          priority="secondary"
          variant="destructive"
          startIcon={<DoNotDisturbOnOutlined fontSize="small" />}
          disabled={!canApprove}
          onClick={() => setRejectOpen(true)}
        >
          {t('Reject')}
        </Button>,
      )}
      {withPermissionTooltip(
        <Button
          type="button"
          size="sm"
          startIcon={<CheckCircleOutlined fontSize="small" />}
          disabled={!canApprove}
          onClick={() => setApproveOpen(true)}
        >
          {t('Approve')}
        </Button>,
      )}
    </div>
  );

  const describe = (entry: PayloadApprovalOutput) => {
    const who = entry.approval_actor_name ?? t('the platform');
    const what = entry.approval_automatic && entry.approval_status === 'APPROVED' && entry.approval_origin !== 'SYSTEM' && entry.approval_origin !== 'MIGRATION'
      ? `${t('Auto-approved')} · ${t(approvalOriginLabel(entry.approval_origin))}`
      : t(approvalOriginLabel(entry.approval_origin));
    return `${what} · ${who} · ${nsdt(entry.approval_created_at)}`;
  };

  return (
    <Section
      title={t('Approval')}
      icon={<FactCheckOutlined fontSize="small" />}
      action={decisionActions || undefined}
    >
      <div style={{
        display: 'grid',
        gridTemplateColumns: '1fr 1fr',
        gap: theme.spacing(2),
      }}
      >
        <Field label="Status">
          <ApprovalStatusChip status={status} />
        </Field>
        <Field label="Last decision">
          <Text variant="content-base">{latest ? describe(latest) : '-'}</Text>
        </Field>
      </div>
      {latest?.approval_comment && (
        <div style={{ marginTop: theme.spacing(1.5) }}>
          <Field label={latest.approval_status === 'REJECTED' ? 'Reason' : 'Comment'}>
            <Text variant="content-base">{latest.approval_comment}</Text>
          </Field>
        </div>
      )}
      {isPayloadUsed(usage) && (
        <div style={{ marginTop: theme.spacing(1.5) }}>
          <Field label="Used in">
            <PayloadUsage usage={usage} />
          </Field>
        </div>
      )}
      <DialogConfirmation
        open={approveOpen}
        handleClose={closeApprove}
        handleSubmit={handleApprove}
        text={t('Approve version v{version}? It can then be used. Injects keep running their current version: you will be asked whether to move them to this one.', { version: version ?? '' })}
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
        text={t('Reject version v{version}? Injects keep running their current version; the author can save a new version.', { version: version ?? '' })}
        submitLabel={t('Reject')}
        submitColor="error"
        extraContent={(
          <div
            className={fdsLayerClass(SURFACE_LAYER)}
            style={{
              ...layerInputVars,
              marginTop: theme.spacing(2),
              display: 'flex',
              flexDirection: 'column',
              gap: theme.spacing(2),
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
            <Text
              variant="content-caption"
              className="text-default-secondary"
              style={{
                display: 'block',
                marginTop: theme.spacing(0.5),
                textAlign: 'right',
              }}
            >
              {`${reason.length} / ${APPROVAL_COMMENT_MAX_LENGTH}`}
            </Text>
          </div>
        )}
      />
    </Section>
  );
};

export default ThreatArsenalApprovalSection;
