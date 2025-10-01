import { Alert, AlertTitle } from '@mui/material';

import { useFormatter } from './i18n';

const NotFound = () => {
  const { t } = useFormatter();
  return (
    <div>
      <Alert severity="info">
        <AlertTitle>{t('Error')}</AlertTitle>
        {t('This page is not found on this OpenAEV application.')}
      </Alert>
    </div>
  );
};

export default NotFound;
