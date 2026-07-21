import { Alert } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';

import { useFormatter } from '../../../../components/i18n';

// Page-level alerts for a deployed connector detail page. The
// Integration-Manager-unreachable warning is NOT shown here anymore: it lives
// inside the deployment / update drawer (which also disables the form), so the
// action buttons stay reachable (OpenCTI pattern). Only the post-migration
// success confirmation remains at page level.
const ConnectorAlerts: FunctionComponent = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [searchParams, setSearchParams] = useSearchParams();
  const [showMigrationAlert, setShowMigrationAlert] = useState(searchParams.get('isMigration') === 'true');

  useEffect(() => {
    setShowMigrationAlert(searchParams.get('isMigration') === 'true');
  }, [searchParams]);

  const dismissMigrationAlert = useCallback(() => {
    setShowMigrationAlert(false);
    searchParams.delete('isMigration');
    setSearchParams(searchParams, { replace: true });
  }, [searchParams, setSearchParams]);

  if (!showMigrationAlert) {
    return null;
  }

  return (
    <Alert severity="success" onClose={dismissMigrationAlert} style={{ marginBottom: theme.spacing(2) }}>
      {t('This connector has been successfully migrated. You can now stop your manually deployed connector before starting this instance.')}
    </Alert>
  );
};

export default ConnectorAlerts;
