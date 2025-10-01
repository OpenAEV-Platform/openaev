import { Typography } from '@mui/material';
import { type CSSProperties } from 'react';

import { useFormatter } from '../../../../../../components/i18n';

interface Props {
  startDate?: string;
  endDate?: string;
  style?: CSSProperties;
}

const ExecutionTime = ({ startDate, endDate, style = {} }: Props) => {
  const { t, fldt } = useFormatter();
  const executionTimeInfo: {
    label: string;
    value: string | null;
  }[] = [
    {
      label: 'Start date',
      value: fldt(startDate),
    },
    {
      label: 'End date',
      value: fldt(endDate),
    },
    {
      label: 'Execution Time',
      value: startDate && endDate
        ? `${(new Date(endDate).getTime() - new Date(startDate).getTime()) / 1000} s`
        : '',
    },
  ];

  return (
    <div style={style}>
      {executionTimeInfo.map(info => (
        <div
          key={info.label}
          style={{
            display: 'flex',
            flexBasis: '100%',
            gap: '12px',
          }}
        >
          <Typography variant="h3">{t(info.label)}</Typography>
          <Typography variant="body2">{info.value}</Typography>
        </div>
      ),
      )}
    </div>
  );
};

export default ExecutionTime;
