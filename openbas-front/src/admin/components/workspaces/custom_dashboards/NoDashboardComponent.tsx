import { Addchart } from '@mui/icons-material';
import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../../components/i18n';

interface Props { actionComponent?: React.ReactNode }

const NoDashboardComponent = ({ actionComponent }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      gap: theme.spacing(1),
      marginTop: theme.spacing(8),
    }}
    >
      <Addchart
        style={{
          width: '80px',
          height: '80px',
          color: theme.palette.text.secondary,
        }}
      />
      <Typography gutterBottom variant="h4">{t('No custom dashboard selected.')}</Typography>
      {actionComponent}
    </div>
  );
};

export default NoDashboardComponent;
