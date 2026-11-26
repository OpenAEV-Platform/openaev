import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionOutput } from '../../../../utils/api-types';
import { rowValue, type VersionRow, versionRows } from './versionRows';

interface Props {
  value: string;
  versions: PayloadVersionOutput[];
  // Catalogue versions not installed, offered by their version.
  notInstalled?: string[];
  onChange: (value: string) => void;
}

const PayloadVersionSelect: FunctionComponent<Props> = ({ value, versions, notInstalled, onChange }) => {
  const { t } = useFormatter();
  const label = ({ version, installed }: VersionRow) => [
    `v${version}`,
    installed?.payload_is_latest && t('Latest version'),
    !installed && t('Not installed'),
    installed?.payload_revoked && t('Revoked'),
  ].filter(Boolean).join(' · ');

  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger aria-label={t('Version')}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {versionRows(versions, notInstalled).map(row => (
          <SelectItem key={rowValue(row)} value={rowValue(row)}>{label(row)}</SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
};

export default PayloadVersionSelect;
