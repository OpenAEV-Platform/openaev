import { Button, Text, Textarea } from '@filigran/design-system';
import { CheckCircleOutlined, DoNotDisturbOnOutlined, FactCheckOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchMe } from '../../../../actions/Application';
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
import {
  type PayloadApprovalOutput,
  type ThreatArsenalActionFullOutput,
  type ThreatArsenalActionUsageOutput,
} from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { type Error as ApiError, notifyErrorHandler } from '../../../../utils/error/errorHandlerUtil';
import { fdsLayerClass, layerInputVars, SURFACE_LAYER } from '../../../../utils/fdsLayer';
import { useAppDispatch } from '../../../../utils/hooks';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { APPROVAL_COMMENT_MAX_LENGTH, type ApprovalOrigin, isPayloadUsed } from './approvalStatusUtils';
import PayloadApprovalHistory from './PayloadApprovalHistory';
import PayloadUsage from './PayloadUsage';
import PayloadVersionCard, { type VersionDecision } from './PayloadVersionCard';
import PayloadVersionComparison from './PayloadVersionComparison';

// History events that submit content for approval (as opposed to a decision on it).
const SUBMISSION_ORIGINS: ApprovalOrigin[] = ['CREATE', 'UPDATE', 'DUPLICATE', 'IMPORT', 'COLLECTOR'];

interface Props {
  action: ThreatArsenalActionFullOutput;
  /** Called with the refreshed action after an approve / reject decision. */
  onDecided: (action: ThreatArsenalActionFullOutput) => void;
}

/**
 * Approval block of an action drawer, built around the payload version (OOUX: the version is the
 * object an approver decides on). Each version is its own card with its own status and decision:
 * the pending version (with what it changes and the Approve / Reject actions of holders of
 * "Approve content") above the approved version in use. Then "Used in" (information only: a pending
 * version never blocks what uses the action) and the approval history.
 */
const ThreatArsenalApprovalSection: FunctionComponent<Props> = ({ action, onDecided }) => {
  const { t } = useFormatter();
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
  const [history, setHistory] = useState<PayloadApprovalOutput[]>([]);

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

  // Approval history: gives each version card its own decision, and the audit trail below.
  useEffect(() => {
    let cancelled = false;
    fetchThreatArsenalActionApprovals(action.action_id)
      .then((response) => {
        if (!cancelled) setHistory(response.data as PayloadApprovalOutput[]);
      })
      .catch(() => {
        if (!cancelled) setHistory([]);
      });
    return () => {
      cancelled = true;
    };
  }, [action.action_id, status, pendingVersion?.version_id]);

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

  // Who can decide sees the buttons on the version they act on; the others see why nothing happens
  // yet instead of greyed-out buttons ("hidden, not greyed out", as in the Scenario Factory screens).
  const decisionActions = canApprove && (
    <>
      <Button
        type="button"
        size="sm"
        priority="secondary"
        variant="destructive"
        startIcon={<DoNotDisturbOnOutlined fontSize="small" />}
        onClick={() => setRejectOpen(true)}
      >
        {t('Reject')}
      </Button>
      <Button
        type="button"
        size="sm"
        startIcon={<CheckCircleOutlined fontSize="small" />}
        onClick={() => setApproveOpen(true)}
      >
        {t('Approve')}
      </Button>
    </>
  );

  // Each card shows its own version's decision, taken from the history: the decision that made
  // the active version approved, the latest rejection, and who submitted what is pending.
  const toDecision = (entry?: PayloadApprovalOutput): VersionDecision | undefined => (entry
    ? {
        who: entry.approval_actor_name,
        when: entry.approval_created_at,
        comment: entry.approval_comment,
        automatic: entry.approval_automatic,
      }
    : undefined);
  const activeDecision = toDecision(history.find(entry => entry.approval_status === 'APPROVED')
    ?? (latest?.approval_status === 'APPROVED' ? latest : undefined));
  const rejection = history.find(entry => entry.approval_origin === 'REJECT')
    ?? (latest?.approval_status === 'REJECTED' ? latest : undefined);
  const submission = history.find(entry => SUBMISSION_ORIGINS.includes(entry.approval_origin));

  const renderVersions = () => {
    if (status === 'APPROVED' && pendingVersion) {
      const pendingNumber = pendingVersion.version_number ?? activeNumber + 1;
      return (
        <>
          <PayloadVersionCard
            versionNumber={pendingNumber}
            status="PENDING"
            authorName={pendingVersion.version_author_name}
            fromCollector={pendingVersion.version_origin === 'COLLECTOR'}
            submittedAt={pendingVersion.version_created_at}
            actions={decisionActions || undefined}
            note={canApprove
              ? undefined
              : t('Waiting for an approver. v{number} keeps running until this version is approved.', { number: String(activeNumber) })}
          >
            {action.action_active_content && (
              <PayloadVersionComparison
                active={action.action_active_content}
                pending={pendingVersion.version_content}
                activeNumber={activeNumber}
                pendingNumber={pendingNumber}
              />
            )}
          </PayloadVersionCard>
          <PayloadVersionCard
            versionNumber={activeNumber}
            status="APPROVED"
            inUse
            decision={activeDecision}
          />
        </>
      );
    }
    if (status === 'APPROVED') {
      return <PayloadVersionCard versionNumber={activeNumber} status="APPROVED" inUse decision={activeDecision} />;
    }
    if (status === 'REJECTED') {
      return (
        <PayloadVersionCard
          versionNumber={activeNumber}
          status="REJECTED"
          authorName={submission?.approval_actor_name}
          fromCollector={submission?.approval_origin === 'COLLECTOR'}
          submittedAt={submission?.approval_created_at}
          decision={toDecision(rejection)}
          note={t('It can\'t be used. Edit the action to submit a new version for approval.')}
        />
      );
    }
    if (status === 'PENDING') {
      return (
        <PayloadVersionCard
          versionNumber={activeNumber}
          status="PENDING"
          authorName={submission?.approval_actor_name}
          fromCollector={submission?.approval_origin === 'COLLECTOR'}
          submittedAt={submission?.approval_created_at}
          actions={decisionActions || undefined}
          note={canApprove
            ? t('New action: it can\'t be used in atomic testings, scenarios or simulations until it is approved.')
            : t('Waiting for an approver. It can\'t be used until it is approved.')}
        />
      );
    }
    return null;
  };

  return (
    <Section
      title={t('Approval')}
      icon={<FactCheckOutlined fontSize="small" />}
    >
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(1.5),
      }}
      >
        {renderVersions()}
        {isPayloadUsed(usage) && (
          <Field label="Used in">
            <PayloadUsage usage={usage} />
          </Field>
        )}
        <Section title={t('Approval history')} collapsible defaultCollapsed>
          <PayloadApprovalHistory history={history} />
        </Section>
      </div>
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
