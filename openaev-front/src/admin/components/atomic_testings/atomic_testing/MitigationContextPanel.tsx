import { Chip, Paper, Text } from '@filigran/design-system';
import { type ReactNode } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type MitigationFinding } from './MitigationCard';
import { SENSITIVE_TYPES } from './mitigationMock';

// PROTOTYPE: the inputs of a mitigation request, shown before generating. These same inputs
// (command, finding types, operating system) are the reuse key: if all are identical, the stored
// mitigation is shown and XTM One is not called again.

interface Props {
  action: string;
  // Command template. Sensitive arguments are masked before anything leaves the platform.
  command?: string;
  // Findings the execution produced. Values of sensitive types are never shown nor sent.
  findings: MitigationFinding[];
  techniques?: string[];
  // Operating system / environment line. Undefined when the execution has no agent.
  environment?: string;
}

const row = (label: string, value: ReactNode) => (
  <div>
    <Text variant="content-caption" className="block text-default-secondary">{label}</Text>
    <div style={{
      display: 'flex',
      flexWrap: 'wrap',
      gap: 4,
    }}
    >
      {value}
    </div>
  </div>
);

const MitigationContextPanel = ({ action, command, findings, techniques = [], environment }: Props) => {
  const { t } = useFormatter();
  const label = (finding: MitigationFinding) => (finding.value && !SENSITIVE_TYPES.some(sensitive => finding.type.toLowerCase().includes(sensitive))
    ? `${finding.type}: ${finding.value}`
    : `${finding.type} (${t('value masked')})`);
  return (
    <Paper
      padding={16}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
      }}
    >
      <Text variant="content-base-bold" className="text-default-secondary">{t('Context sent to XTM One')}</Text>
      {row(t('Action'), <Text variant="content-compact">{action}</Text>)}
      {command && row(
        t('Command'),
        <div style={{
          fontFamily: '"IBM Plex Mono", "Roboto Mono", monospace',
          fontSize: 12.5,
          overflowWrap: 'anywhere',
        }}
        >
          {command}
        </div>,
      )}
      {row(
        t('Findings'),
        findings.length > 0
          ? findings.map(finding => <Chip key={`${finding.type}-${finding.value ?? ''}`} label={label(finding)} severity="neutral" />)
          : <Text variant="content-compact">{t('None')}</Text>,
      )}
      {techniques.length > 0 && row(t('Techniques'), techniques.map(ap => <Chip key={ap} label={ap} severity="neutral" />))}
      {row(
        t('Operating system'),
        <Text variant="content-compact">
          {environment ?? t('No agent (injector based execution): the suggestion relies on the command and its findings.')}
        </Text>,
      )}
      <Text variant="content-caption" className="text-default-secondary">
        {t('Same command, finding types and operating system: the stored mitigation is reused, no new request.')}
      </Text>
    </Paper>
  );
};

export default MitigationContextPanel;
