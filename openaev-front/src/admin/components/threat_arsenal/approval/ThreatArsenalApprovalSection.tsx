import { Button, Text, Textarea, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { CheckCircleOutlined, DoNotDisturbOnOutlined, FactCheckOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactElement, useEffect, useState } from 'react';

import {
  approveThreatArsenalAction,
  fetchThreatArsenalActionApprovals,
  fetchThreatArsenalActionUsage,
  rejectThreatArsenalAction,
} from '../../../../actions/threat_arsenals/threatArsenal-actions';
import DialogConfirmation from '../../../../components/common/DialogConfirmation';
import Field from '../../../../components/common/overview/Field';
import Section from '../../../../components/common/overview/Section';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadApprovalOutput, type ThreatArsenalActionFullOutput, type ThreatArsenalActionUsageOutput } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { fdsLayerClass, layerInputVars, SURFACE_LAYER } from '../../../../utils/fdsLayer';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, PERMISSION_REQUIRED, SUBJECTS } from '../../../../utils/permissions/types';
import ApprovalStatusChip from './ApprovalStatusChip';
import { APPROVAL_COMMENT_MAX_LENGTH, approvalOriginLabel, approvalStatusLabel, isPayloadUsed } from './approvalStatusUtils';
import PayloadUsageWarning from './PayloadUsageWarning';

interface Props {
  action: ThreatArsenalActionFullOutput;
  /** Called with the refreshed action after an approve / reject decision. */
  onDecided: (action: ThreatArsenalActionFullOutput) => void;
}

/**
 * Approval block of an action drawer: the status, the latest decision, the Approve / Reject
 * actions for holders of "Approve content" and the approval history.
 */
const ThreatArsenalApprovalSection: FunctionComponent<Props> = ({ action, onDecided }) => {
  const { t, nsdt } = useFormatter();
  const theme = useTheme();
  const ability = useAbility();
  const canApprove = ability.can(ACTIONS.APPROVE, SUBJECTS.THREAT_ARSENALS);

  const [approveOpen, setApproveOpen] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [comment, setComment] = useState('');
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState<string | undefined>(undefined);
  const [history, setHistory] = useState<PayloadApprovalOutput[]>([]);
  const [usage, setUsage] = useState<ThreatArsenalActionUsageOutput | undefined>(undefined);

  const status = action.action_approval_status;
  const latest = action.action_approval_latest;
  const isPending = status === 'PENDING';

  useEffect(() => {
    let cancelled = false;
    fetchThreatArsenalActionApprovals(action.action_id)
      .then((response) => {
        if (!cancelled) setHistory((response.data ?? []) as PayloadApprovalOutput[]);
      })
      .catch(() => {
        if (!cancelled) setHistory([]);
      });
    return () => {
      cancelled = true;
    };
  }, [action.action_id, status, latest?.approval_id]);

  // Warning before impact: where the payload is used, loaded when the Reject dialog opens.
  useEffect(() => {
    if (!rejectOpen) {
      setUsage(undefined);
      return undefined;
    }
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
  }, [rejectOpen, action.action_id]);

  const closeApprove = () => {
    setApproveOpen(false);
    setComment('');
  };
  const closeReject = () => {
    setRejectOpen(false);
    setReason('');
    setReasonError(undefined);
  };

  // The approval is bound to the content shown here: the server refuses it if the payload changed.
  const handleApprove = () => approveThreatArsenalAction(action.action_id, {
    approval_fingerprint: action.action_approval_fingerprint ?? '',
    approval_comment: comment.trim() || undefined,
  }).then((response) => {
    closeApprove();
    onDecided(response.data as ThreatArsenalActionFullOutput);
    MESSAGING$.notifySuccess(t('The payload has been approved.'));
  });

  const handleReject = (resetLoading?: () => void) => {
    const trimmed = reason.trim();
    if (!trimmed) {
      setReasonError(t('A reason is required to reject a payload.'));
      resetLoading?.();
      return undefined;
    }
    return rejectThreatArsenalAction(action.action_id, { approval_reason: trimmed }).then((response) => {
      closeReject();
      onDecided(response.data as ThreatArsenalActionFullOutput);
      MESSAGING$.notifySuccess(t('The payload has been rejected.'));
    });
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
      {history.length > 1 && (
        <div style={{ marginTop: theme.spacing(1.5) }}>
          <Field label="Approval history">
            <ul style={{
              margin: 0,
              paddingLeft: theme.spacing(2),
            }}
            >
              {history.map(entry => (
                <li key={entry.approval_id}>
                  <Text variant="content-caption">
                    {`${t(approvalStatusLabel(entry.approval_status))} · ${describe(entry)}`}
                  </Text>
                  {entry.approval_comment && entry.approval_id !== latest?.approval_id && (
                    <Text variant="content-caption" className="text-default-secondary" style={{ display: 'block' }}>
                      {entry.approval_comment}
                    </Text>
                  )}
                </li>
              ))}
            </ul>
          </Field>
        </div>
      )}
      <DialogConfirmation
        open={approveOpen}
        handleClose={closeApprove}
        handleSubmit={handleApprove}
        text={t('Approve this payload? It can then be used in atomic testings, scenarios and simulations.')}
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
        text={isPayloadUsed(usage)
          ? t('Reject this payload? It will block the launch of the items below until it is edited and approved again.')
          : t('Reject this payload? It stays blocked until it is edited and approved.')}
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
            <PayloadUsageWarning usage={usage} />
            <Textarea
              label={t('Reason')}
              required
              placeholder={t('Why this payload is rejected, shown to its author')}
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
