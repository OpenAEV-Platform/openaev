import { useTheme } from '@mui/material';
import { type FunctionComponent } from 'react';

import { getStatusColor } from '../utils/statusUtils';
import Tag from './common/tag/Tag';
import { useFormatter } from './i18n';

interface ItemStatusProps {
  label: string;
  status?: string | null;
  variant?: 'inList';
  isInject?: boolean;
}

const ItemStatus: FunctionComponent<ItemStatusProps> = ({
  label,
  status,
  variant,
  isInject = false,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  let finalLabel = label;
  if (isInject) {
    if (status === 'SUCCESS') {
      finalLabel = t('INJECT EXECUTED');
    }
  }

  const color = getStatusColor(theme, status ?? undefined);
  const maxWidth = variant === 'inList' ? 150 : 150;

  return (
    <Tag
      label={finalLabel}
      color={color}
      maxWidth={maxWidth}
      tooltipTitle={finalLabel}
    />
  );
};

export default ItemStatus;
