import { Button, Checkbox, Spinner, Text, Textarea } from '@filigran/design-system';
import { OpenInNewOutlined } from '@mui/icons-material';
import { Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';
import { Link } from 'react-router';

import { changeThreatArsenalVersion, fetchThreatArsenalUsages, fetchThreatArsenalVersions } from '../../../actions/threat_arsenals/threatArsenal-version-actions';
import Transition from '../../../components/common/Transition';
import { useFormatter } from '../../../components/i18n';
import { type PayloadUsageOutput, type PayloadVersionOutput } from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';
import payloadRevocationChoices from './payloadRevocationChoices';
import PayloadVersionSelect from './PayloadVersionSelect';

interface Props {
  open: boolean;
  name: string;
  actionId: string;
  payloadId?: string;
  /** Lifting lists only the versions this tenant revoked: a list's revocation is lifted by a list. */
  lift?: boolean;
  /** A customized payload also lists the versions revoked through their Filigran base. */
  customized?: boolean;
  onClose: () => void;
  onSubmit: (payloadId: string, reason: string, inherited: boolean) => void;
}

const PayloadRevocationDialog: FunctionComponent<Props> = ({ open, name, actionId, payloadId, lift = false, customized = false, onClose, onSubmit }) => {
  const { t } = useFormatter();
  const [reason, setReason] = useState('');
  const [versions, setVersions] = useState<PayloadVersionOutput[] | null>(null);
  const [selected, setSelected] = useState(payloadId ?? '');
  // Every version, to offer a replacement for the injects of the revoked one.
  const [allVersions, setAllVersions] = useState<PayloadVersionOutput[]>([]);
  const [replace, setReplace] = useState(true);
  const [replacement, setReplacement] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    setVersions(null);
    fetchThreatArsenalVersions(actionId)
      .then((response: { data: PayloadVersionOutput[] }) => {
        const shown = payloadRevocationChoices(response.data, lift, customized);
        setAllVersions(response.data);
        setVersions(shown);
        setSelected(shown.some(v => v.payload_id === payloadId) ? payloadId ?? '' : shown[0]?.payload_id ?? '');
      })
      .catch(() => setVersions([]));
  }, [open, actionId, payloadId, lift, customized]);

  const version = versions?.find(v => v.payload_id === selected);

  // What the revocation stops: the injects, atomic tests and chained steps on the chosen version.
  const [usages, setUsages] = useState<PayloadUsageOutput[] | null>(null);
  useEffect(() => {
    setUsages(null);
    if (open && version?.payload_action_id) {
      fetchThreatArsenalUsages(version.payload_action_id)
        .then((response: { data: PayloadUsageOutput[] }) => setUsages(response.data))
        .catch(() => setUsages([]));
    }
  }, [open, version?.payload_action_id]);
  // At scale (hundreds of injects) a list does not fit here: numbers, and the full list one click away.
  const scenarioCount = new Set((usages ?? []).map(usage => usage.usage_scenario_id).filter(Boolean)).size;
  const countOf = (type: string) => (usages ?? []).filter(usage => usage.usage_type === type).length;
  const injectCount = version?.payload_usage_count ?? 0;
  const impact = [
    scenarioCount > 0 ? t('{count, plural, one {# scenario} other {# scenarios}}', { count: scenarioCount }) : null,
    countOf('inject') > 0 ? t('{count, plural, one {# scenario inject} other {# scenario injects}}', { count: countOf('inject') }) : null,
    countOf('atomic_testing') > 0 ? t('{count, plural, one {# atomic test} other {# atomic tests}}', { count: countOf('atomic_testing') }) : null,
    countOf('step') > 0 ? t('{count, plural, one {# chained step} other {# chained steps}}', { count: countOf('step') }) : null,
  ].filter(Boolean).join(' · ');
  // Where the injects can go instead: an installed version that is neither revoked nor waiting for approval.
  const replacements = allVersions.filter(v => v.payload_installed
    && v.payload_id !== selected
    && !v.payload_revoked
    && v.payload_approval_status !== 'PENDING'
    && v.payload_approval_status !== 'REJECTED');
  const defaultReplacement = (replacements.find(v => v.payload_is_latest) ?? replacements[0])?.payload_version ?? '';
  useEffect(() => setReplacement(defaultReplacement), [defaultReplacement]);
  const canReplace = !lift && injectCount > 0 && replacements.length > 0;
  // The impact is always stated, also when it is none: what runs elsewhere keeps running.
  const otherUsage = (versions ?? []).filter(v => v.payload_id !== version?.payload_id)
    .reduce((sum, v) => sum + (v.payload_usage_count ?? 0), 0);
  const inherited = lift && customized && !!version && !version.payload_revoked_in_tenant;
  const needsReason = !lift || inherited;

  const close = () => {
    setReason('');
    onClose();
  };

  // The injects move first, so none of them fails between the revocation and the move.
  const submit = async () => {
    const target = replacements.find(v => v.payload_version === replacement);
    if (canReplace && replace && target?.payload_id && version?.payload_action_id && usages) {
      setSubmitting(true);
      try {
        const response = await changeThreatArsenalVersion(version.payload_action_id, {
          target_payload_id: target.payload_id,
          inject_ids: usages.filter(u => u.usage_type !== 'step').map(u => u.usage_id),
          step_ids: usages.filter(u => u.usage_type === 'step').map(u => u.usage_id),
        });
        const moved = response.data?.moved_ids?.length ?? 0;
        MESSAGING$.notifySuccess(t('{count} usages moved to v{version}', {
          count: String(moved),
          version: target.payload_version ?? '',
        }));
      } finally {
        setSubmitting(false);
      }
    }
    onSubmit(selected, reason.trim(), inherited);
    setReason('');
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
      <DialogTitle>{`${t(lift ? 'Lift revocation' : 'Revoke')} ${name}`}</DialogTitle>
      <DialogContent>
        <DialogContentText style={{ marginBottom: 12 }}>
          {lift
            ? t(inherited ? 'Revoked through its Filigran base. Lifting it lets this version run again in this tenant.' : 'The chosen version runs again in this tenant.')
            : t('A revoked payload no longer runs in this tenant: until it is lifted, injects using it are skipped and fail when their scenario runs.')}
        </DialogContentText>
        {versions?.length === 0 ? (
          <Text variant="content-base">{lift ? t('No version is revoked in this tenant.') : t('No version left to revoke.')}</Text>
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
              }))}
              onChange={value => setSelected(versions?.find(v => v.payload_version === value)?.payload_id ?? '')}
              loading={versions === null}
            />
            {version && (
              <Text variant="content-caption">
                {injectCount === 1 ? t('Used by 1 inject') : t('Used by {count} injects', { count: String(injectCount) })}
              </Text>
            )}
          </div>
        )}
        {version && injectCount === 0 && (
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
            marginBottom: 16,
          }}
          >
            <Text variant="content-base-bold">{lift ? t('Runs again in:') : t('Will fail until it is lifted:')}</Text>
            <Text variant="content-base">
              {t('Nothing: no scenario inject, atomic test or chained step uses v{version}.', { version: version.payload_version ?? '' })}
              {otherUsage > 0 && ` ${t('{count, plural, one {# inject keeps} other {# injects keep}} running on other versions.', { count: String(otherUsage) })}`}
            </Text>
          </div>
        )}
        {version && injectCount > 0 && (
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
            marginBottom: 16,
          }}
          >
            <Text variant="content-base-bold">{lift ? t('Runs again in:') : t('Will fail until it is lifted:')}</Text>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              flexWrap: 'wrap',
            }}
            >
              {usages === null ? <Spinner size="sm" /> : <Text variant="content-base">{impact}</Text>}
              {version.payload_action_id && (
                <Link
                  to={`/admin/threat-arsenal?action=${encodeURIComponent(version.payload_action_id)}&tab=versions`}
                  target="_blank"
                  rel="noopener"
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 4,
                  }}
                >
                  {t('See where it is used')}
                  <OpenInNewOutlined fontSize="inherit" />
                </Link>
              )}
            </div>
            {canReplace && (
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                flexWrap: 'wrap',
              }}
              >
                <Checkbox
                  checked={replace}
                  onCheckedChange={checked => setReplace(checked === true)}
                  label={t('Replace it in these injects with')}
                />
                <PayloadVersionSelect
                  value={replacement}
                  options={replacements.map(v => ({
                    version: v.payload_version ?? '',
                    latest: v.payload_is_latest,
                    installed: true,
                  }))}
                  onChange={setReplacement}
                />
              </div>
            )}
          </div>
        )}
        {needsReason && versions?.length !== 0 && (
          <Textarea
            aria-label={t(inherited ? 'Why this customized version is safe to run' : 'Reason')}
            placeholder={t(inherited ? 'Why this customized version is safe to run' : 'Reason')}
            minRows={3}
            value={reason}
            onChange={event => setReason(event.target.value)}
          />
        )}
      </DialogContent>
      <DialogActions>
        <Button type="button" priority="secondary" onClick={close}>{t('Cancel')}</Button>
        <Button
          type="button"
          variant={lift ? 'default' : 'destructive'}
          disabled={(needsReason && reason.trim() === '') || !version || submitting}
          onClick={submit}
        >
          {submitting ? <Spinner size="sm" /> : t(lift ? 'Lift revocation' : 'Revoke')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default PayloadRevocationDialog;
