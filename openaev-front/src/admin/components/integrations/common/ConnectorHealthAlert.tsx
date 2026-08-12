import { Alert, AlertTitle, Button } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type ConnectorInstanceOutput } from '../../../../utils/api-types';

interface ConnectorHealthAlertProps {
  instance?: ConnectorInstanceOutput;
  onSeeLogs?: () => void;
}

const ConnectorHealthAlert: FunctionComponent<ConnectorHealthAlertProps> = ({ instance, onSeeLogs }) => {
  const { t } = useFormatter();

  if (instance?.connector_instance_is_in_reboot_loop !== true) {
    return null;
  }

  return (
    <Alert
      severity="error"
      action={onSeeLogs && (
        <Button color="inherit" size="small" onClick={onSeeLogs}>
          {t('See logs')}
        </Button>
      )}
    >
      <AlertTitle>{t('This connector keeps restarting')}</AlertTitle>
      {t('It failed to start and has been restarted {count} times. It is not processing anything.', { count: instance.connector_instance_restart_count })}
    </Alert>
  );
};

export default ConnectorHealthAlert;
