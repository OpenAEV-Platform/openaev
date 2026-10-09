import { Button, Text, Textarea, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { CheckCircleOutlined, DoNotDisturbOnOutlined, FactCheckOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactElement, useEffect, useState } from 'react';

import { fetchMe } from '../../../../actions/Application';
import {
  approveThreatArsenalAction,
  fetchThreatArsenalActionApprovals,
  fetchThreatArsenalActionUsage,
  fetchThreatArsenalActionVersions,
  rejectThreatArsenalAction,
} from '../../../../actions/threat_arsenals/threatArsenal-actions';
import DialogConfirmation from '../../../../components/common/DialogConfirmation';
import Field from '../../../../components/common/overview/Field';
import Section from '../../../../components/common/overview/Section';
import { useFormatter } from '../../../../components/i18n';
import {
  type PayloadApprovalOutput,
  type PayloadVersionOutput,
  type ThreatArsenalActionFullOutput,
  type ThreatArsenalActionUsageOutput,
} from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { type Error as ApiError, notifyErrorHandler } from '../../../../utils/error/errorHandlerUtil';
import { fdsLayerClass, layerInputVars, SURFACE_LAYER } from '../../../../utils/fdsLayer';
import { useAppDispatch } from '../../../../utils/hooks';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, PERMISSION_REQUIRED, SUBJECTS } from '../../../../utils/permissions/types';
import ApprovalStatusChip from './ApprovalStatusChip';
import { APPROVAL_COMMENT_MAX_LENGTH, approvalOriginLabel, approvalStatusLabel, isPayloadUsed, versionStatusLabel } from './approvalStatusUtils';
import PayloadUsage from './PayloadUsage';
import PayloadVersionComparison from './PayloadVersionComparison';

interface Props {
  action: ThreatArsenalActionFullOutput;
  /** Called with the refreshed action after an approve / reject decision. */
  onDecided: (action: ThreatArsenalActionFullOutput) => void;
}

/**
 * Approval block of an action drawer: the status, the latest decision, the Approve / Reject
 * actions for holders of "Approve content" and the approval history. When an approved action has
 * a pending version, the decision acts on that version: the block shows the active and pending
 * versions, what the pending one changes, and the version history. "Used in" is information only:
 * a pending version never blocks what uses the action.
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
  const [history, setHistory] = useState<PayloadApprovalOutput[]>([]);
  const [usage, setUsage] = useState<ThreatArsenalActionUsageOutput | undefined>(undefined);
  const [versions, setVersions] = useState<PayloadVersionOutput[]>([]);

  const status = action.action_approval_status;
  const latest = action.action_approval_latest;
  const pendingVersion = action.action_pending_version;
  const activeNumber = action.action_active_version ?? 1;
  const isPending = status === 'PENDING' || !!pendingVersion;

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

  useEffect(() => {
    let cancelled = false;
    fetchThreatArsenalActionVersions(action.action_id)
      .then((response) => {
        if (!cancelled) setVersions((response.data ?? []) as PayloadVersionOutput[]);
      })
      .catch(() => {
        if (!cancelled) setVersions([]);
      });
    return () => {
      cancelled = true;
    };
  }, [action.action_id, status, latest?.approval_id, pendingVersion?.version_id]);

  const closeApprove = () => {
    setApproveOpen(false);
    setComment('');
  };
  const closeReject = () => {
    setRejectOpen(false);
    setReason('');
    setReasonError(undefined);
  };

  // The approval is bound to the content shown here (the pending version when there is one): the
  // server refuses it if that content changed meanwhile.
  const handleApprove = () => approveThreatArsenalAction(action.action_id, {
    approval_fingerprint: pendingVersion?.version_fingerprint ?? action.action_approval_fingerprint ?? '',
    approval_comment: comment.trim() || undefined,
  }, false).then((response) => {
    closeApprove();
    onDecided(response.data as ThreatArsenalActionFullOutput);
    MESSAGING$.notifySuccess(pendingVersion ? t('The new version has been approved.') : t('The payload has been approved.'));
  }).catch(error => onDecisionError(error, closeApprove));

  const handleReject = (resetLoading?: () => void) => {
    const trimmed = reason.trim();
    if (!trimmed) {
      setReasonError(pendingVersion ? t('A reason is required to reject a version.') : t('A reason is required to reject a payload.'));
      resetLoading?.();
      return undefined;
    }
    return rejectThreatArsenalAction(action.action_id, { approval_reason: trimmed }, false).then((response) => {
      closeReject();
      onDecided(response.data as ThreatArsenalActionFullOutput);
      MESSAGING$.notifySuccess(pendingVersion ? t('The new version has been rejected.') : t('The payload has been rejected.'));
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

  const describeVersion = (version: PayloadVersionOutput) => {
    const who = version.version_origin === 'COLLECTOR'
      ? t('Synchronized by a collector')
      : version.version_author_name ?? t('Unknown user');
    return `${who} · ${nsdt(version.version_created_at)}`;
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
        {status === 'APPROVED' && (
          <Field label="Active version">
            <Text variant="content-base">{`v${activeNumber}`}</Text>
          </Field>
        )}
        {pendingVersion && (
          <Field label="Pending version">
            <Text variant="content-base">{describeVersion(pendingVersion)}</Text>
          </Field>
        )}
      </div>
      {pendingVersion && action.action_active_content && (
        <div style={{ marginTop: theme.spacing(1.5) }}>
          <Field label="Changes in the pending version">
            <PayloadVersionComparison
              active={action.action_active_content}
              pending={pendingVersion.version_content}
              activeNumber={activeNumber}
              pendingNumber={pendingVersion.version_number ?? activeNumber + 1}
            />
          </Field>
        </div>
      )}
      {latest?.approval_comment && (
        <div style={{ marginTop: theme.spacing(1.5) }}>
          <Field label={latest.approval_status === 'REJECTED' ? 'Reason' : 'Comment'}>
            <Text variant="content-base">{latest.approval_comment}</Text>
          </Field>
        </div>
      )}
      {versions.length > 0 && (
        <div style={{ marginTop: theme.spacing(1.5) }}>
          <Field label="Version history">
            <ul style={{
              margin: 0,
              paddingLeft: theme.spacing(2),
            }}
            >
              {versions.map(version => (
                <li key={version.version_id}>
                  <Text variant="content-caption">
                    {`v${version.version_number} · ${t(versionStatusLabel(version.version_status))} · ${describeVersion(version)}${version.version_decider_name && version.version_status !== 'PENDING' ? ` · ${t('Decided by {name}', { name: version.version_decider_name })}` : ''}`}
                  </Text>
                  {version.version_comment && (
                    <Text variant="content-caption" className="text-default-secondary" style={{ display: 'block' }}>
                      {version.version_comment}
                    </Text>
                  )}
                </li>
              ))}
            </ul>
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
        text={pendingVersion
          ? t('Approve version {number}? It replaces the active version: the next runs use it.', { number: String(pendingVersion.version_number) })
          : t('Approve this payload? It can then be used in atomic testings, scenarios and simulations.')}
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
        text={pendingVersion
          ? t('Reject version {number}? The active version stays in use.', { number: String(pendingVersion.version_number) })
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
            <Textarea
              label={t('Reason')}
              required
              placeholder={pendingVersion ? t('Why this version is rejected, shown to its author') : t('Why this payload is rejected, shown to its author')}
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
