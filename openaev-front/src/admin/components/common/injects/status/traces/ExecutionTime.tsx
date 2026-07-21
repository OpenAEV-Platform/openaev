import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties } from 'react';

import { useFormatter } from '../../../../../../components/i18n';

interface Props {
  startDate?: string;
  endDate?: string;
  style?: CSSProperties;
}

const ExecutionTime = ({ startDate, endDate, style = {} }: Props) => {
  const { t, fldt, du } = useFormatter();
  const theme = useTheme();

  const duration = startDate && endDate
    ? du(new Date(endDate).getTime() - new Date(startDate).getTime())
    : null;

  const items: {
    label: string;
    value: string | null;
  }[] = [
    {
      label: t('Start date'),
      value: fldt(startDate),
    },
    {
      label: t('End date'),
      value: fldt(endDate),
    },
    {
      label: t('Execution Time'),
      value: duration,
    },
  ];

  return (
    <div
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        columnGap: theme.spacing(2),
        rowGap: theme.spacing(0.5),
        padding: theme.spacing(0.5, 0),
        ...style,
      }}
    >
      {items.map(item => (
        <div
          key={item.label}
          style={{
            display: 'flex',
            alignItems: 'baseline',
            gap: theme.spacing(0.75),
            minWidth: 0,
          }}
        >
          <Typography
            variant="caption"
            sx={{
              color: 'text.secondary',
              textTransform: 'uppercase',
              letterSpacing: 0.5,
              whiteSpace: 'nowrap',
            }}
          >
            {item.label}
          </Typography>
          <Typography variant="body2" sx={{ fontWeight: 500 }}>
            {item.value || '-'}
          </Typography>
        </div>
      ))}
    </div>
  );
};

export default ExecutionTime;
