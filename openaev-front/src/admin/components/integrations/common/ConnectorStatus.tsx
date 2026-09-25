import { Chip, type ChipSeverity } from '@filigran/design-system';

import { useFormatter } from '../../../../components/i18n';

type StatusVariant = 'loading' | 'started' | 'stopped' | undefined;

type ConnectorStatusProps = { variant: StatusVariant };

const ConnectorStatus = ({ variant }: ConnectorStatusProps) => {
  const { t } = useFormatter();

  let label = '';
  let severity: ChipSeverity = 'neutral';
  let disabled = false;

  if (variant === 'loading') {
    label = t('Loading');
    disabled = true;
    severity = 'neutral';
  }

  if (variant === 'started') {
    label = t('Started');
    severity = 'low';
  }

  if (variant === 'stopped') {
    label = t('Stopped');
    severity = 'critical';
  }

  return (
    <Chip disabled={disabled} severity={severity} label={label} />
  );
};

export default ConnectorStatus;
