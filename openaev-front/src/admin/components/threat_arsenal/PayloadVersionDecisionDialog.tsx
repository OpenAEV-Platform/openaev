import { Button, Checkbox, Text, Textarea } from '@filigran/design-system';
import { Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchThreatArsenalVersions } from '../../../actions/threat_arsenals/threatArsenal-version-actions';
import Transition from '../../../components/common/Transition';
import { useFormatter } from '../../../components/i18n';
import { type PayloadVersionOutput } from '../../../utils/api-types';
import payloadVersionDecisionChoices, { type PayloadVersionDecision, payloadVersionMoveTargets } from './payloadVersionDecisionChoices';
import movePayloadVersionUsages from './payloadVersionMove';
import PayloadVersionSelect from './PayloadVersionSelect';

interface Props {
  open: boolean;
  name: string;
  actionId: string;
  payloadId?: string;
  decision: PayloadVersionDecision;
  /** A customized payload also lists the versions revoked through their Filigran base. */
  customized?: boolean;
  onClose: () => void;
  onSubmit: (payloadId: string, reason: string, inherited: boolean) => void | Promise<void>;
}

const TITLES: Record<PayloadVersionDecision, string> = {
  revoke: 'Revoke',
  lift: 'Lift revocation',
  reject: 'Reject',
  reopen: 'Move to pending approval',
};

const WARNINGS: Record<Exclude<PayloadVersionDecision, 'lift'>, string> = {
  revoke: 'A revoked payload no longer runs in this tenant: injects using it are skipped and their scenarios cannot launch until it is lifted.',
  reject: 'A rejected version can no longer be picked or run: injects using it cannot launch until they move to another version. The reason is shown to its author.',
  reopen: 'The version waits for approval again: it can no longer be picked or run, and injects using it cannot launch until it is approved. Nobody is notified: ask for the review yourself.',
};

const EMPTY: Record<PayloadVersionDecision, string> = {
  revoke: 'No version left to revoke.',
  lift: 'No version is revoked in this tenant.',
  reject: 'No version to reject.',
  reopen: 'No version to move back to pending approval.',
};

/**
 * A decision on one version of a payload, all through the same dialog: revoke it or lift its
 * revocation, reject it or move it back to pending approval. Except for lifting, the injects
 * using the version can move to another approved version in the same step.
 */
const PayloadVersionDecisionDialog: FunctionComponent<Props> = ({ open, name, actionId, payloadId, decision, customized = false, onClose, onSubmit }) => {
  const { t } = useFormatter();
  const [reason, setReason] = useState('');
  const [allVersions, setAllVersions] = useState<PayloadVersionOutput[]>([]);
  const [versions, setVersions] = useState<PayloadVersionOutput[] | null>(null);
  const [selected, setSelected] = useState(payloadId ?? '');
  const [move, setMove] = useState(true);
  const [target, setTarget] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const lift = decision === 'lift';

  useEffect(() => {
    if (!open) {
      return;
    }
    setVersions(null);
    fetchThreatArsenalVersions(actionId)
      .then((response: { data: PayloadVersionOutput[] }) => {
        const shown = payloadVersionDecisionChoices(response.data, decision, customized);
        setAllVersions(response.data);
        setVersions(shown);
        setSelected(shown.some(v => v.payload_id === payloadId) ? payloadId ?? '' : shown[0]?.payload_id ?? '');
      })
      .catch(() => setVersions([]));
  }, [open, actionId, payloadId, decision, customized]);

  const version = versions?.find(v => v.payload_id === selected);
  const inherited = lift && customized && !!version && !version.payload_revoked_in_tenant;
  const reasonRequired = decision === 'revoke' || decision === 'reject' || inherited;
  const showReason = !lift || inherited;
  let reasonLabel = decision === 'reopen' ? 'Note (optional)' : 'Reason';
  if (inherited) {
    reasonLabel = 'Why this customized version is safe to run';
  }

  // The injects of the chosen version can follow to an approved version, the newest by default.
  const targets = version ? payloadVersionMoveTargets(allVersions, version.payload_id) : [];
  const canMove = !lift && !!version && version.payload_usage_count > 0 && targets.length > 0;
  const targetVersion = targets.find(v => v.payload_id === target) ?? targets[0];

  const close = () => {
    setReason('');
    setMove(true);
    setTarget('');
    onClose();
  };

  const submit = async () => {
    if (!version) {
      return;
    }
    setSubmitting(true);
    try {
      await onSubmit(selected, reason.trim(), inherited);
      if (canMove && move && version.payload_action_id && targetVersion?.payload_id) {
        await movePayloadVersionUsages(version.payload_action_id, targetVersion.payload_id, targetVersion.payload_version ?? '', t);
      }
      setReason('');
      setMove(true);
      setTarget('');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog
      open={open}
      slots={{ transition: Transition }}
      onClose={close}
      slotProps={{ paper: { elevation: 1 } }}
      fullWidth
      maxWidth="sm"
    >
      <DialogTitle>{`${t(TITLES[decision])} ${name}`}</DialogTitle>
      <DialogContent>
        <DialogContentText style={{ marginBottom: 12 }}>
          {lift
            ? t(inherited ? 'Revoked through its Filigran base. Lifting it lets this version run again in this tenant.' : 'The chosen version runs again in this tenant.')
            : t(WARNINGS[decision])}
        </DialogContentText>
        {versions?.length === 0 ? (
          <Text variant="content-base">{t(EMPTY[decision])}</Text>
        ) : (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            marginBottom: 16,
          }}
          >
            <PayloadVersionSelect
              value={version?.payload_version ?? ''}
              options={(versions ?? []).map(v => ({
                version: v.payload_version ?? '',
                latest: v.payload_is_latest,
                installed: true,
                revoked: lift,
                approvalStatus: v.payload_approval_status,
              }))}
              onChange={value => setSelected(versions?.find(v => v.payload_version === value)?.payload_id ?? '')}
              loading={versions === null}
            />
            {version && (
              <Text variant="content-caption">
                {t('Used by {count} injects', { count: String(version.payload_usage_count) })}
              </Text>
            )}
          </div>
        )}
        {showReason && versions?.length !== 0 && (
          <Textarea
            aria-label={t(reasonLabel)}
            placeholder={t(reasonLabel)}
            minRows={3}
            value={reason}
            onChange={event => setReason(event.target.value)}
          />
        )}
        {canMove && (
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
            marginTop: 16,
          }}
          >
            <Checkbox
              checked={move}
              onCheckedChange={checked => setMove(checked === true)}
              label={t('Move the injects using this version to another version')}
            />
            {move && (
              <PayloadVersionSelect
                value={targetVersion?.payload_version ?? ''}
                options={targets.map(v => ({
                  version: v.payload_version ?? '',
                  latest: v.payload_is_latest,
                  installed: true,
                }))}
                onChange={value => setTarget(targets.find(v => v.payload_version === value)?.payload_id ?? '')}
              />
            )}
          </div>
        )}
      </DialogContent>
      <DialogActions>
        <Button type="button" priority="secondary" onClick={close}>{t('Cancel')}</Button>
        <Button
          type="button"
          variant={decision === 'revoke' || decision === 'reject' ? 'destructive' : 'default'}
          disabled={(reasonRequired && reason.trim() === '') || !version || submitting}
          onClick={submit}
        >
          {t(TITLES[decision])}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default PayloadVersionDecisionDialog;
