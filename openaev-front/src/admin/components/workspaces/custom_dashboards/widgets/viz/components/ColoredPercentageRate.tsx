import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { SUCCESS_25_COLOR, SUCCESS_50_COLOR, SUCCESS_75_COLOR, SUCCESS_100_COLOR } from '../securityCoverageUtils';

const ColoredPercentageRate = ({ style = {} }) => {
  const theme = useTheme();
  const items = [
    {
      label: '100%',
      color: SUCCESS_100_COLOR,
    },
    {
      label: '< 75%',
      color: SUCCESS_75_COLOR,
    },
    {
      label: '< 50%',
      color: SUCCESS_50_COLOR,
    },
    {
      label: '< 25%',
      color: SUCCESS_25_COLOR,
    },
  ];

  return (
    <div style={{
      display: 'flex',
      ...style,
    }}
    >
      {items.map(({ label, color }) => (
        <Typography
          key={label}
          sx={{
            backgroundColor: color,
            padding: `${theme.spacing(0.5)} ${theme.spacing(1)}`,
          }}
          variant="body2"
          color="white"
        >
          {label}
        </Typography>
      ))}
    </div>
  );
};

export default ColoredPercentageRate;
