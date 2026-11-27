// fds:keep-mui Dialog: the library has no dialog component yet
import { Button, Text } from '@filigran/design-system';
import { Dialog, DialogActions, DialogContent, DialogTitle } from '@mui/material';
import { useEffect, useState } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type CollectorType, type ControlType, type RemediationMitigationCategory } from './remediationMitigationMock';
import { generateItem, type MockPayload, unsupportedReason } from './remediationMitigationStore';
import { COLLECTOR_LABELS } from './useRemediationMitigationLabels';
import { type RequestContext } from './xtmOneRequest';

// PROTOTYPE: the "AI" is simulated (2 s delay, canned output). Nothing is sent to XTM One.

type MitigationControl = Extract<ControlType, 'detection' | 'prevention'>;

export interface GenerateDefaults {
  controlType?: MitigationControl;
  // 'generic' means no collector.
  collector?: CollectorType | 'generic';
}

interface Props {
  open: boolean;
  onClose: () => void;
  payload: MockPayload;
  category: RemediationMitigationCategory;
  // Where the answer is stored: the action for a remediation, the result for a mitigation.
  scopeKey?: string;
  context?: RequestContext;
  defaults?: GenerateDefaults;
}

const Choice = <T extends string>({ label, value, options, onChange }: {
  label: string;
  value: T;
  options: {
    value: T;
    label: string;
  }[];
  onChange: (value: T) => void;
}) => (
  <div style={{
    display: 'flex',
    flexDirection: 'column',
    gap: 6,
  }}
  >
    <Text variant="content-caption" className="text-default-secondary">{label}</Text>
    <div style={{
      display: 'flex',
      gap: 6,
      flexWrap: 'wrap',
    }}
    >
      {options.map(option => (
        <Button
          key={option.value}
          size="sm"
          priority={option.value === value ? 'primary' : 'secondary'}
          aria-pressed={option.value === value}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </Button>
      ))}
    </div>
  </div>
);

const GenerateDialog = ({ open, onClose, payload, category, scopeKey, context, defaults }: Props) => {
  const { t } = useFormatter();
  const [controlType, setControlType] = useState<MitigationControl>('detection');
  const [collector, setCollector] = useState<CollectorType>('splunk');
  const [loading, setLoading] = useState(false);
  // XTM One answered that it has no suggestion: the reason it gave.
  const [noSuggestion, setNoSuggestion] = useState<string | undefined>();

  // Re-applied each time the dialog opens, so a smart hint can prefill it.
  useEffect(() => {
    if (!open) return;
    setControlType(defaults?.controlType ?? 'detection');
    const target = defaults?.collector;
    setCollector(target && target !== 'generic' ? target : 'splunk');
    setLoading(false);
    setNoSuggestion(undefined);
  }, [open, defaults?.controlType, defaults?.collector]);

  const isMitigation = category === 'mitigation';
  const selectedCollector = isMitigation ? collector : undefined;
  const unsupported = isMitigation
    ? unsupportedReason({
        controlType,
        collector: selectedCollector,
      })
    : undefined;

  const generate = () => {
    setLoading(true);
    generateItem({
      payload,
      category,
      scopeKey,
      controlType: isMitigation ? controlType : undefined,
      collector: selectedCollector,
      findingType: context?.findingType,
    }).then((result) => {
      setLoading(false);
      // A remediation with no patch is an answer, shown by the tab itself (reason + link to the mitigations).
      if (result.unsupported && isMitigation) {
        setNoSuggestion(result.unsupported);
      } else {
        onClose();
      }
    });
  };

  return (
    <Dialog open={open} onClose={loading ? undefined : onClose} fullWidth maxWidth="sm">
      <DialogTitle>{isMitigation ? t('Generate mitigation') : t('Generate remediation')}</DialogTitle>
      <DialogContent>
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
          paddingTop: 4,
        }}
        >
          <Text variant="content-compact" className="text-default-secondary">
            {`${t('Payload')}: ${payload.name}${context?.findingType ? ` · ${t('finding type')}: ${context.findingType}` : ''}`}
          </Text>
          {isMitigation
            ? (
                <>
                  <Choice<MitigationControl>
                    label={t('Control type')}
                    value={controlType}
                    options={[
                      {
                        value: 'detection',
                        label: t('Detection'),
                      },
                      {
                        value: 'prevention',
                        label: t('Prevention'),
                      },
                    ]}
                    onChange={setControlType}
                  />
                  <Choice<CollectorType>
                    label={t('Collector')}
                    value={collector}
                    options={(Object.keys(COLLECTOR_LABELS) as CollectorType[]).map(key => ({
                      value: key,
                      label: COLLECTOR_LABELS[key],
                    }))}
                    onChange={setCollector}
                  />
                  {controlType === 'prevention' && !unsupported && (
                    <Text variant="content-caption" className="text-default-secondary">
                      {t('A prevention rule is generated to run in monitor/detect mode first.')}
                    </Text>
                  )}
                  {unsupported && <Text variant="content-compact">{t(unsupported)}</Text>}
                </>
              )
            : (
                <Text variant="content-compact">
                  {t('A remediation depends on the target platform of the payload, not on a security tool: there is nothing else to choose.')}
                </Text>
              )}
          {loading && <Text variant="content-compact">{t('Generating…')}</Text>}
          {noSuggestion && (
            <Text variant="content-compact">
              {`${isMitigation ? t('XTM One has no mitigation suggestion for this request.') : t('XTM One has no remediation suggestion for this request.')} ${noSuggestion}`}
            </Text>
          )}
        </div>
      </DialogContent>
      <DialogActions>
        <Button priority="secondary" disabled={loading} onClick={onClose}>{t('Cancel')}</Button>
        <Button variant="ia" disabled={loading || !!unsupported} onClick={generate}>{t('Generate')}</Button>
      </DialogActions>
    </Dialog>
  );
};

export default GenerateDialog;
