import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionOutput } from '../../../../utils/api-types';

interface Props {
  value: string;
  versions: PayloadVersionOutput[];
  onChange: (payloadId: string) => void;
}

const PayloadVersionSelect: FunctionComponent<Props> = ({ value, versions, onChange }) => {
  const { t } = useFormatter();
  const label = (version: PayloadVersionOutput) => [
    `v${version.payload_version}`,
    version.payload_is_latest && t('Latest version'),
    version.payload_revoked && t('Revoked'),
  ].filter(Boolean).join(' · ');

  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger aria-label={t('Version')}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {versions.map(version => (
          <SelectItem key={version.payload_id} value={version.payload_id}>{label(version)}</SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
};

export default PayloadVersionSelect;
