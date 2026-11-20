import { Alert, Button, Checkbox, Spinner, Text, Textarea } from '@filigran/design-system';
import { OpenInNewOutlined } from '@mui/icons-material';
import { Dialog, DialogActions, DialogContent, DialogTitle } from '@mui/material';
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
  // Where the injects can go instead: another installed version that is not revoked.
  const replacements = allVersions.filter(v => v.payload_installed
    && v.payload_id !== selected
    && !v.payload_revoked);
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

  const target = replacements.find(v => v.payload_version === replacement);
  // With "Replace" ticked, the revoke waits until the move can run: the injects loaded and a version to
  // move them to. Otherwise it would revoke the version and leave its injects failing.
  const moving = canReplace && replace;
  const moveReady = !moving || (!!target?.payload_id && !!version?.payload_action_id && !!usages && usages.length > 0);

  // The injects move first, so none of them fails between the revocation and the move.
  const submit = async () => {
    if (!moveReady) return;
    if (moving && target?.payload_id && version?.payload_action_id && usages) {
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

  const versionLabel = version?.payload_version ?? '';
  // The button says what happens: moving the injects first is part of the action.
  let submitLabel = String(lift ? t('Lift revocation') : t('Revoke'));
  if (moving) submitLabel = String(t('{count, plural, one {Move # inject and revoke} other {Move # injects and revoke}}', { count: injectCount }));
  // Where the version is used, one click away (new tab, the dialog stays).
  const usedLink = version?.payload_action_id && (
    <Button asChild priority="secondary" size="sm">
      <Link
        to={`/admin/threat-arsenal?action=${encodeURIComponent(version.payload_action_id)}&tab=versions`}
        target="_blank"
        rel="noopener"
      >
        {t('See where')}
        <OpenInNewOutlined fontSize="small" style={{ marginLeft: 4 }} />
      </Link>
    </Button>
  );
  // The impact in one message, its tone matching reality: a warning when something fails, a check when
  // nothing does. Lifting says what runs again.
  const renderImpact = () => {
    if (!version) return null;
    if (lift) {
      return (
        <Alert
          severity="info"
          title={t('v{version} runs again in this tenant.', { version: versionLabel })}
          description={[
            inherited ? t('It was revoked through its Filigran base.') : null,
            injectCount > 0 && usages !== null ? impact : null,
          ].filter(Boolean).join(' · ') || undefined}
          action={injectCount > 0 ? usedLink : undefined}
        />
      );
    }
    if (injectCount === 0) {
      return (
        <Alert
          severity="success"
          title={t('Nothing fails: v{version} isn\'t used.', { version: versionLabel })}
          description={otherUsage > 0
            ? t('{count, plural, one {# inject keeps running on another version.} other {# injects keep running on other versions.}}', { count: otherUsage })
            : undefined}
        />
      );
    }
    return (
      <Alert
        severity="warning"
        title={t('Revoked versions don\'t run. What uses v{version} will fail:', { version: versionLabel })}
        description={usages === null ? <Spinner size="sm" /> : impact}
        action={usedLink}
      />
    );
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
      {/* A short title; the payload's name below it, on one line (in full on hover). */}
      <DialogTitle>
        {t(lift ? 'Lift a revocation' : 'Revoke a version')}
        <Text
          variant="content-base"
          className="text-default-secondary"
          title={name}
          style={{
            display: 'block',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {name}
        </Text>
      </DialogTitle>
      <DialogContent>
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
          paddingTop: 4,
        }}
        >
          {versions?.length === 0 ? (
            <Text variant="content-base">{lift ? t('No version is revoked in this tenant.') : t('No version left to revoke.')}</Text>
          ) : (
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
            }}
            >
              <Text variant="content-base">{t('Version')}</Text>
              <PayloadVersionSelect
                value={versionLabel}
                options={(versions ?? []).map(v => ({
                  version: v.payload_version ?? '',
                  latest: v.payload_is_latest,
                  installed: true,
                  revoked: lift,
                }))}
                onChange={value => setSelected(versions?.find(v => v.payload_version === value)?.payload_id ?? '')}
                loading={versions === null}
              />
            </div>
          )}
          {renderImpact()}
          {/* The safe way, right under the impact: move the injects first, so nothing fails. */}
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
                label={t('{count, plural, one {Move it first to} other {Move them first to}}', { count: injectCount })}
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
              <Text variant="content-caption" className="text-default-secondary">{t('so nothing fails')}</Text>
            </div>
          )}
          {needsReason && versions?.length !== 0 && (
            <Textarea
              label={t(inherited ? 'Why this customized version is safe to run' : 'Reason')}
              required
              placeholder={t(inherited ? 'Why this customized version is safe to run' : 'Why this version is unsafe')}
              minRows={2}
              value={reason}
              onChange={event => setReason(event.target.value)}
            />
          )}
        </div>
      </DialogContent>
      <DialogActions>
        <Button type="button" priority="secondary" onClick={close}>{t('Cancel')}</Button>
        <Button
          type="button"
          variant={lift ? 'default' : 'destructive'}
          disabled={(needsReason && reason.trim() === '') || !version || !moveReady || submitting}
          onClick={submit}
        >
          {submitting ? <Spinner size="sm" /> : submitLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default PayloadRevocationDialog;
