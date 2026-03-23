import { Alert, AlertTitle } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from './i18n';

const SimpleError: FunctionComponent = () => {
  const { t } = useFormatter();
  return (
    <Alert severity="error">
      <AlertTitle>{t('Error')}</AlertTitle>
      {t('An unknown error occurred. Please contact your administrator or the OpenAEV maintainers.')}
    </Alert>
  );
};

export default SimpleError;
