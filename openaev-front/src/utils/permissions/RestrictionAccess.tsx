import { Alert } from '@mui/material';

import { useFormatter } from '../../components/i18n';

const RestrictionAccess = ({ restrictedField }: { restrictedField: string }) => {
  const { t } = useFormatter();
  return (
    <div>
      <Alert severity="warning">
        {t('Your {restricted_field} library access is restricted. Please contact your administrator to upgrade.', { restricted_field: restrictedField })}
      </Alert>
    </div>
  );
};

export default RestrictionAccess;
