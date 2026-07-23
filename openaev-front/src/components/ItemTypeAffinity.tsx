import { DevicesOtherOutlined, HelpOutlineOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import { useFormatter } from './i18n';

interface Props {
  typeAffinity?: string;
  size?: 'small' | 'medium' | 'large' | 'inherit';
}

// Type affinity of a scenario / simulation (fed by OpenCTI security
// coverages): the kind of attack surface the scenario exercises. Rendered as
// icon + label, matching the sibling detail fields (ItemCategory,
// ItemMainFocus), instead of a bare uppercase chip.
const renderIcon = (affinity: string, size: Props['size']) => {
  switch (affinity) {
    case 'endpoint':
      return <DevicesOtherOutlined fontSize={size ?? 'medium'} sx={{ marginRight: '10px' }} />;
    default:
      return <HelpOutlineOutlined fontSize={size ?? 'medium'} sx={{ marginRight: '10px' }} />;
  }
};

// Values arrive as free-form STIX strings (e.g. "ENDPOINT"): normalize for
// the icon switch and derive a readable fallback label for unknown values.
const humanize = (value: string) => {
  const lowered = value.toLowerCase().replace(/[-_]+/g, ' ');
  return lowered.charAt(0).toUpperCase() + lowered.slice(1);
};

const ItemTypeAffinity: FunctionComponent<Props> = ({ typeAffinity, size }) => {
  const { t } = useFormatter();

  if (!typeAffinity) {
    return '-';
  }

  const normalized = typeAffinity.toLowerCase();
  const label = normalized === 'endpoint' ? t('Endpoint') : humanize(typeAffinity);

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
    }}
    >
      {renderIcon(normalized, size)}
      <span style={{
        fontSize: 14,
        whiteSpace: 'nowrap',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
      }}
      >
        {label}
      </span>
    </div>
  );
};

export default ItemTypeAffinity;
