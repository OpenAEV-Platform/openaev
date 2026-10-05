import { Chip, type ChipSeverity } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

interface ItemSeverityProps {
  label: string;
  severity?: string | null;
  variant?: 'inList';
}

const computeSeverity = (severity: string | undefined | null): ChipSeverity => {
  switch (severity) {
    case 'low':
      return 'low';
    case 'medium':
      return 'info';
    case 'high':
      return 'high';
    case 'critical':
      return 'critical';
    default:
      return 'neutral';
  }
};

const ItemSeverity: FunctionComponent<ItemSeverityProps> = ({
  label,
  severity,
}) => {
  return (
    <Chip severity={computeSeverity(severity)} label={label} />
  );
};

export default ItemSeverity;
