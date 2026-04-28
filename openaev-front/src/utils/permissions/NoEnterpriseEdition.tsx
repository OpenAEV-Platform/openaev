import { Alert, AlertTitle } from '@mui/material';

import EnterpriseEditionButton from '../../admin/components/common/entreprise_edition/EnterpriseEditionButton';
import { useFormatter } from '../../components/i18n';

const NoEnterpriseEdition = () => {
  const { t } = useFormatter();
  return (
    <Alert
      severity="info"
      sx={{
        display: 'flex',
        alignItems: 'center',
      }}
      action={<EnterpriseEditionButton />}
    >
      <AlertTitle>{t('Enterprise Edition')}</AlertTitle>
      {t('This feature requires an Enterprise Edition license.')}
    </Alert>
  );
};

export default NoEnterpriseEdition;
