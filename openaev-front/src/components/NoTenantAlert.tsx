import { Alert, AlertTitle } from '@mui/material';

import { useFormatter } from './i18n';

const NoTenantAlert = () => {
  const { t } = useFormatter();
  return (
    <Alert
      severity="warning"
      sx={{
        display: 'flex',
        alignItems: 'center',
        margin: 2,
      }}
    >
      <AlertTitle>{t('No tenant assigned')}</AlertTitle>
      {t('Your account is not attached to any tenant. Please contact your administrator to get access.')}
    </Alert>
  );
};

export default NoTenantAlert;
