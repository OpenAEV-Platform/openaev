import { Button, Checkbox, Radio, RadioGroup } from '@filigran/design-system';
import { Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import {
  changeThreatArsenalVersion,
  fetchThreatArsenalUsages,
  fetchThreatArsenalVersions,
} from '../../../actions/threat_arsenals/threatArsenal-version-actions';
import Transition from '../../../components/common/Transition';
import { useFormatter } from '../../../components/i18n';
import {
  type PayloadUsageOutput,
  type PayloadVersionChangeOutput,
  type PayloadVersionOutput,
} from '../../../utils/api-types';

interface Props {
  open: boolean;
  actionId: string;
  name: string;
  onClose: () => void;
}

// F30: pick a non-revoked version, then the scenario injects, atomic tests and chained steps to move.
const ChangeVersionDialog: FunctionComponent<Props> = ({ open, actionId, name, onClose }) => {
  const { t } = useFormatter();
  const [step, setStep] = useState<1 | 2>(1);
  const [versions, setVersions] = useState<PayloadVersionOutput[]>([]);
  const [usages, setUsages] = useState<PayloadUsageOutput[]>([]);
  const [targetId, setTargetId] = useState('');
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [result, setResult] = useState<PayloadVersionChangeOutput | null>(null);

  useEffect(() => {
    if (!open) return;
    setStep(1);
    setTargetId('');
    setSelected(new Set());
    setResult(null);
    fetchThreatArsenalVersions(actionId).then(response => setVersions(response.data as PayloadVersionOutput[]));
    fetchThreatArsenalUsages(actionId).then(response => setUsages(response.data as PayloadUsageOutput[]));
  }, [open, actionId]);

  const groups = useMemo(() => {
    const byGroup = new Map<string, PayloadUsageOutput[]>();
    usages.forEach((usage) => {
      const group = usage.usage_scenario_name ?? t('Atomic testings');
      byGroup.set(group, [...(byGroup.get(group) ?? []), usage]);
    });
    return [...byGroup.entries()];
  }, [usages, t]);

  const selectable = versions.filter(v => !v.payload_current && !v.payload_revoked);
  const allSelected = usages.length > 0 && selected.size === usages.length;

  const toggle = (id: string) => setSelected((previous) => {
    const next = new Set(previous);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    return next;
  });

  const submit = async () => {
    const chosen = usages.filter(usage => selected.has(usage.usage_id));
    const response = await changeThreatArsenalVersion(actionId, {
      target_payload_id: targetId,
      inject_ids: chosen.filter(usage => usage.usage_type !== 'step').map(usage => usage.usage_id),
      step_ids: chosen.filter(usage => usage.usage_type === 'step').map(usage => usage.usage_id),
    });
    setResult(response.data as PayloadVersionChangeOutput);
  };

  const versionLabel = (version: PayloadVersionOutput) => `v${version.payload_version ?? '-'}`;
  const versionDescription = (version: PayloadVersionOutput) => [
    version.payload_current && t('Current version'),
    version.payload_is_latest && t('Latest version'),
    version.payload_revoked && t('Revoked'),
  ].filter(Boolean).join(' · ') || undefined;
  const usageLabel = (usage: PayloadUsageOutput) => usage.usage_name ?? usage.usage_id;
  const nameOf = (id: string) => usageLabel(usages.find(usage => usage.usage_id === id) ?? {
    usage_id: id,
    usage_type: '',
  });

  const renderResult = (output: PayloadVersionChangeOutput) => (
    <>
      <DialogContentText>{`${t('Moved')}: ${output.moved_ids?.length ?? 0}`}</DialogContentText>
      {(output.refused ?? []).length > 0 && (
        <>
          <DialogContentText style={{ marginTop: 12 }}>{`${t('Not moved')}:`}</DialogContentText>
          <ul>
            {(output.refused ?? []).map(refusal => (
              <li key={refusal.usage_id}>{`${nameOf(refusal.usage_id)}: ${refusal.reason}`}</li>
            ))}
          </ul>
        </>
      )}
    </>
  );

  const renderVersions = () => (selectable.length === 0
    ? <DialogContentText>{t('No other version of this payload')}</DialogContentText>
    : (
        <RadioGroup aria-label={t('Version')} value={targetId} onValueChange={setTargetId}>
          {versions.map(version => (
            <Radio
              key={version.payload_id}
              value={version.payload_id}
              label={versionLabel(version)}
              description={versionDescription(version)}
              disabled={version.payload_current || version.payload_revoked}
            />
          ))}
        </RadioGroup>
      ));

  const renderUsages = () => (usages.length === 0
    ? <DialogContentText>{t('No scenario or atomic testing uses this version')}</DialogContentText>
    : (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 12,
        }}
        >
          <Checkbox
            label={t('Select all')}
            checked={allSelected}
            onCheckedChange={() => setSelected(allSelected ? new Set() : new Set(usages.map(usage => usage.usage_id)))}
          />
          {groups.map(([group, items]) => (
            <div
              key={group}
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: 8,
              }}
            >
              <strong>{group}</strong>
              {items.map(usage => (
                <Checkbox
                  key={usage.usage_id}
                  label={usageLabel(usage)}
                  description={usage.usage_type === 'step' ? t('Chained step') : undefined}
                  checked={selected.has(usage.usage_id)}
                  onCheckedChange={() => toggle(usage.usage_id)}
                />
              ))}
            </div>
          ))}
        </div>
      ));

  return (
    <Dialog
      open={open}
      slots={{ transition: Transition }}
      onClose={onClose}
      slotProps={{ paper: { elevation: 1 } }}
      fullWidth
      maxWidth="sm"
    >
      <DialogTitle>{`${t('Change version')} ${name}`}</DialogTitle>
      <DialogContent>
        {result && renderResult(result)}
        {!result && step === 1 && renderVersions()}
        {!result && step === 2 && renderUsages()}
      </DialogContent>
      <DialogActions>
        {result && <Button type="button" onClick={onClose}>{t('Close')}</Button>}
        {!result && (
          <>
            <Button type="button" priority="secondary" onClick={step === 1 ? onClose : () => setStep(1)}>
              {t(step === 1 ? 'Cancel' : 'Back')}
            </Button>
            {step === 1
              ? <Button type="button" disabled={targetId === ''} onClick={() => setStep(2)}>{t('Next')}</Button>
              : <Button type="button" disabled={selected.size === 0} onClick={submit}>{t('Change version')}</Button>}
          </>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default ChangeVersionDialog;
