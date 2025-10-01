import { Alert, AlertTitle } from '@mui/material';

import { useFormatter } from '../../components/i18n';

const NoAccess = () => {
  const { t } = useFormatter();
  return (
    <div>
      <Alert severity="info">
        <AlertTitle>{t('Error')}</AlertTitle>
        {t('You dont have the right access. Please contact your administrator.')}
      </Alert>
    </div>
  );
};

export default NoAccess;
