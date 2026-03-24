import { useTheme } from '@mui/material';
import { type FunctionComponent } from 'react';

import Tag from './common/tag/Tag';

interface ItemSeverityProps {
  label: string;
  severity?: string | null;
  variant?: 'inList';
}

const ItemSeverity: FunctionComponent<ItemSeverityProps> = ({
  label,
  severity,
  variant,
}) => {
  const theme = useTheme();

  const getSeverityColor = () => {
    switch (severity) {
      case 'low':
        return theme.palette.severity?.low ?? '#16AD34';
      case 'medium':
        return theme.palette.severity?.medium ?? '#E1B823';
      case 'high':
        return theme.palette.severity?.high ?? '#E6700F';
      case 'critical':
        return theme.palette.severity?.critical ?? '#EE3838';
      default:
        return null;
    }
  };

  const color = getSeverityColor();
  const maxWidth = variant === 'inList' ? 100 : 100;

  return (
    <Tag
      label={label}
      color={color}
      maxWidth={maxWidth}
      disableTooltip
    />
  );
};

export default ItemSeverity;
