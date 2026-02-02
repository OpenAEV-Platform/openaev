import { Chip, CircularProgress } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import colorStyles from '../../../../components/Color';
import { useFormatter } from '../../../../components/i18n';

type StatusVariant = 'loading' | 'started' | 'stopped' | undefined;

type ConnectorStatusProps = { variant: StatusVariant };

const useStyles = makeStyles()(theme => ({
  chipVerified: {
    padding: theme.spacing(2),
    fontSize: 12,
    height: 20,
    textTransform: 'uppercase',
    borderRadius: 4,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
}));

const ConnectorStatus = ({ variant }: ConnectorStatusProps) => {
  const { classes } = useStyles();
  const { t } = useFormatter();

  let label: React.ReactNode = null;
  let chipStyle = colorStyles.grey;
  let disabled = false;

  if (variant === 'loading') {
    label = <CircularProgress size={20} color="inherit" style={{ verticalAlign: 'middle' }} />;
    disabled = true;
    chipStyle = colorStyles.grey;
  }

  if (variant === 'started') {
    label = t('Started');
    chipStyle = colorStyles.green;
  }

  if (variant === 'stopped') {
    label = t('Stopped');
    chipStyle = colorStyles.red;
  }

  return (
    <Chip
      variant="filled"
      className={classes.chipVerified}
      disabled={disabled}
      style={chipStyle}
      label={label}
    />
  );
};

export default ConnectorStatus;
