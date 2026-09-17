import { Chip } from '@filigran/design-system';
import moment from 'moment-timezone';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import countdown from '../../../../utils/hooks/countDown';
import { splitDuration } from '../../../../utils/Time';

interface Props {
  expirationTime: number;
  startDate: string;
}

const ExpirationChipExpired = () => {
  const { t } = useFormatter();

  return (
    <Chip label={t('Expired')} />
  );
};

const ExpirationChipCountdown: FunctionComponent<{
  expirationTime: number;
  remainingSeconds: number;
}> = ({ expirationTime, remainingSeconds }) => {
  const { t } = useFormatter();

  const remainingTimePeriod = countdown(expirationTime - remainingSeconds, 60000, 60);
  const splitExpirationTime = splitDuration(remainingTimePeriod);
  return (
    <Chip
      label={`${t('EXPIRES in')} ${splitExpirationTime.hours}
                                    ${t('h')} ${splitExpirationTime.minutes}
                                    ${t('m')}`}
    />
  );
};

const ExpirationChip: FunctionComponent<Props> = ({ expirationTime, startDate }) => {
  const remainingSeconds = moment.utc().unix() - moment.utc(startDate).unix();

  if (expirationTime < remainingSeconds) {
    return <ExpirationChipExpired />;
  }

  return <ExpirationChipCountdown expirationTime={expirationTime} remainingSeconds={remainingSeconds} />;
};

export default ExpirationChip;
