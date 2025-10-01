import { Chip } from '@mui/material';
import moment from 'moment-timezone';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import countdown from '../../../../utils/hooks/countDown';
import { splitDuration } from '../../../../utils/Time';

const useStyles = makeStyles()(theme => ({
  score: {
    fontSize: '0.75rem',
    height: '20px',
    padding: theme.spacing(1),
  },
}));

interface Props {
  expirationTime: number;
  startDate: string;
}

const ExpirationChipExpired = () => {
  const { classes } = useStyles();
  const { t } = useFormatter();

  return (
    <Chip
      classes={{ root: classes.score }}
      label={t('Expired')}
    />
  );
};

const ExpirationChipCountdown: FunctionComponent<{
  expirationTime: number;
  remainingSeconds: number;
}> = ({ expirationTime, remainingSeconds }) => {
  const { classes } = useStyles();
  const { t } = useFormatter();

  const remainingTimePeriod = countdown(expirationTime - remainingSeconds, 60000, 60);
  const splitExpirationTime = splitDuration(remainingTimePeriod);
  return (
    <Chip
      classes={{ root: classes.score }}
      label={`${t('EXPIRES in')} ${splitExpirationTime.hours}
                                    ${t('h')} ${splitExpirationTime.minutes}
                                    ${t('m')}`}
    />
  );
};

const ExpirationChip: FunctionComponent<Props> = ({ expirationTime, startDate }) => {
  const remainingSeconds = moment.utc().unix() - moment.utc(startDate).unix();

  if (remainingSeconds <= 0) {
    return <ExpirationChipExpired />;
  }

  return <ExpirationChipCountdown expirationTime={expirationTime} remainingSeconds={remainingSeconds} />;
};

export default ExpirationChip;
