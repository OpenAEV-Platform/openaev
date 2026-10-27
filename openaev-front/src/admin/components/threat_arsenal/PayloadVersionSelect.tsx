import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue, Spinner } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';

export interface PayloadVersionOption {
  version: string;
  latest?: boolean;
  installed?: boolean;
  revoked?: boolean;
}

interface Props {
  value: string;
  options: PayloadVersionOption[];
  onChange: (version: string) => void;
  loading?: boolean;
}

const PayloadVersionSelect: FunctionComponent<Props> = ({ value, options, onChange, loading = false }) => {
  const { t } = useFormatter();
  const label = (option: PayloadVersionOption) => [
    `v${option.version}`,
    option.latest && t('Latest version'),
    !option.installed && t('Not installed'),
    option.revoked && t('Revoked'),
  ].filter(Boolean).join(' · ');
  // While loading, the current version is the only option, so the trigger keeps its label.
  const shown = loading && !options.some(option => option.version === value)
    ? [{
        version: value,
        installed: true,
      }]
    : options;

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: 8,
    }}
    >
      {loading && <Spinner size="sm" />}
      <Select value={value} onValueChange={onChange} disabled={loading}>
        <SelectTrigger aria-label={t('Version')} aria-busy={loading}>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {shown.map(option => <SelectItem key={option.version} value={option.version}>{label(option)}</SelectItem>)}
        </SelectContent>
      </Select>
    </div>
  );
};

export default PayloadVersionSelect;
