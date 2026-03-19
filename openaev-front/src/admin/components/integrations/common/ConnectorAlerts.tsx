import { Alert } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import type { CatalogConnectorOutput } from '../../../../utils/api-types';

interface ConnectorAlertsProps {
  isEnterpriseEdition: boolean;
  isXtmComposerUp: boolean;
  catalogConnector?: CatalogConnectorOutput;
}

const ConnectorAlerts: FunctionComponent<ConnectorAlertsProps> = ({
  isEnterpriseEdition,
  isXtmComposerUp,
  catalogConnector,
}) => {
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

  const alertStyle = { marginBottom: theme.spacing(2) };

  return (
    <>
      {isEnterpriseEdition && !isXtmComposerUp && catalogConnector?.catalog_connector_manager_supported && (
        <Alert severity="warning" style={alertStyle}>
          {t('Xtm composer is not reachable', { catalogType: catalogConnector.catalog_connector_type.toLowerCase() })}
        </Alert>
      )}
      {showMigrationAlert && (
        <Alert severity="success" onClose={dismissMigrationAlert} style={alertStyle}>
          {t('This connector has been successfully migrated. You can now stop your manually deployed connector before starting this instance.')}
        </Alert>
      )}
    </>
  );
};

export default ConnectorAlerts;
