import { Button, Spinner, Text, Textarea } from '@filigran/design-system';
import { OpenInNewOutlined } from '@mui/icons-material';
import { Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';
import { Link } from 'react-router';

import { liftPayloadRevocation, revokePayload } from '../../../../actions/payloads/payload-revocation-actions';
import { fetchThreatArsenalUsages } from '../../../../actions/threat_arsenals/threatArsenal-version-actions';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadUsagesOutput, type PayloadVersionOutput } from '../../../../utils/api-types';
import PayloadVersionSelect from '../versions/PayloadVersionSelect';

interface Props {
  open: boolean;
  name: string;
  lift: boolean;
  // The versions this dialog can revoke, or lift.
  versions: PayloadVersionOutput[];
  currentPayloadId: string;
  onClose: () => void;
  onDone: (version: PayloadVersionOutput) => void;
}

const PayloadRevocationDialog: FunctionComponent<Props> = ({ open, name, lift, versions, currentPayloadId, onClose, onDone }) => {
  const { t } = useFormatter();
  const [selected, setSelected] = useState('');
  const [usages, setUsages] = useState<PayloadUsagesOutput | null>(null);
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      setReason('');
      setSelected(versions.some(v => v.payload_id === currentPayloadId) ? currentPayloadId : versions[0]?.payload_id ?? '');
    }
  }, [open, versions, currentPayloadId]);

  const version = versions.find(v => v.payload_id === selected);
  useEffect(() => {
    setUsages(null);
    if (open && version) {
      fetchThreatArsenalUsages(version.payload_action_id).then(response => setUsages(response.data as PayloadUsagesOutput));
    }
  }, [open, version?.payload_action_id]);

  const shown = usages?.payload_usages ?? [];
  const usageCount = shown.length + (usages?.payload_usages_hidden_count ?? 0);
  const scenarioCount = new Set(shown.map(usage => usage.usage_scenario_id).filter(Boolean)).size;
  const countOf = (type: string) => shown.filter(usage => usage.usage_type === type).length;
  const impact = [
    scenarioCount > 0 ? t('{count, plural, one {# scenario} other {# scenarios}}', { count: scenarioCount }) : null,
    countOf('inject') > 0 ? t('{count, plural, one {# scenario inject} other {# scenario injects}}', { count: countOf('inject') }) : null,
    countOf('atomic_testing') > 0 ? t('{count, plural, one {# atomic test} other {# atomic tests}}', { count: countOf('atomic_testing') }) : null,
    countOf('step') > 0 ? t('{count, plural, one {# chained step} other {# chained steps}}', { count: countOf('step') }) : null,
  ].filter(Boolean).join(' · ');

  const submitLabel = lift ? t('Lift revocation') : t('Revoke');

  const submit = async () => {
    if (!version) return;
    setSubmitting(true);
    try {
      if (lift) {
        await liftPayloadRevocation(version.payload_id);
      } else {
        await revokePayload(version.payload_id, { payload_revocation_reason: reason.trim() });
      }
      onDone(version);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog
      open={open}
      slots={{ transition: Transition }}
      onClose={onClose}
      slotProps={{ paper: { elevation: 1 } }}
      fullWidth
      maxWidth="sm"
    >
      <DialogTitle>{`${submitLabel} ${name}`}</DialogTitle>
      <DialogContent>
        <DialogContentText style={{ marginBottom: 12 }}>
          {lift
            ? t('The chosen version runs again in this tenant.')
            : t('Revoked versions don\'t run. Anything using this version will fail until you lift the revocation.')}
        </DialogContentText>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          marginBottom: 16,
        }}
        >
          <PayloadVersionSelect value={selected} versions={versions} onChange={setSelected} />
          {usages && (
            <Text variant="content-caption">
              {usageCount === 1 ? t('Used by 1 inject') : t('Used by {count} injects', { count: String(usageCount) })}
            </Text>
          )}
        </div>
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
          marginBottom: 16,
        }}
        >
          <Text variant="content-base-bold">{lift ? t('Runs again in:') : t('What will fail:')}</Text>
          {usages === null && <Spinner size="sm" />}
          {usages && usageCount === 0 && (
            <Text variant="content-base">{t('Nothing fails: v{version} isn\'t used.', { version: version?.payload_version ?? '' })}</Text>
          )}
          {usages && usageCount > 0 && (
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              flexWrap: 'wrap',
            }}
            >
              <Text variant="content-base">{impact}</Text>
              {version && (
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
          )}
        </div>
        {!lift && (
          <Textarea
            aria-label={t('Reason')}
            placeholder={t('Reason')}
            minRows={3}
            value={reason}
            onChange={event => setReason(event.target.value)}
          />
        )}
      </DialogContent>
      <DialogActions>
        <Button type="button" priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
        <Button
          type="button"
          variant={lift ? undefined : 'destructive'}
          disabled={!version || submitting || (!lift && reason.trim() === '')}
          onClick={submit}
        >
          {submitting ? <Spinner size="sm" /> : submitLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default PayloadRevocationDialog;
