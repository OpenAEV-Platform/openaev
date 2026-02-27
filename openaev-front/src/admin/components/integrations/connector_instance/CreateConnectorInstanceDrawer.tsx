import { Alert } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useNavigate } from 'react-router';

import { createConnectorInstance } from '../../../../actions/connector_instances/connector-instance-actions';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import type {
  CatalogConnector,
  CreateConnectorInstanceInput, JsonNode,
} from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { notifyErrorHandler } from '../../../../utils/error/errorHandlerUtil';
import ConnectorInstanceForm from './ConnectorInstanceForm';
import useConnectorInstanceForm from './useConnectorInstance';

interface Props {
  open: boolean;
  onClose: () => void;
  catalogConnectorId: string;
  catalogConnectorSlug: string;
  connectorType: CatalogConnector['catalog_connector_type'];
  disabled?: boolean;
  migrationSource?: string;
  disabledMessage?: string;
}

const CreateConnectorInstanceDrawer = ({ open, onClose, catalogConnectorId, catalogConnectorSlug, connectorType, disabled = false, disabledMessage, migrationSource }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const navigate = useNavigate();

  const { loading, configurationsDefinitionMap, initialValues } = useConnectorInstanceForm(
    false,
    catalogConnectorId,
    undefined,
    open,
  );

  const onCreateConnectorInstance = (data: Omit<CreateConnectorInstanceInput, 'catalog_connector_id'>) => {
    if (migrationSource) {
      data.connector_instance_configurations?.push({
        configuration_key: connectorType + '_ID',
        configuration_value: migrationSource as unknown as JsonNode,
      });
    }
    createConnectorInstance({
      catalog_connector_id: catalogConnectorId,
      ...data,
    }).then(({ data }) => {
      const connectorId = data.connector_instance_configurations.find(conf => conf.connector_instance_configuration_key === `${connectorType}_ID`)?.connector_instance_configuration_value;
      if (connectorId) {
        const migrationParam = migrationSource ? '?isMigration=true' : '';
        navigate(`/admin/integrations/${connectorType?.toLowerCase()}s/${connectorId}${migrationParam}`);
      }
      onClose();
    }).catch((error) => {
      if (error?.status === 500) {
        MESSAGING$.notifyError(t(error.message));
      } else {
        notifyErrorHandler(error);
      }
    });
  };

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={migrationSource ? t('Migrate to a new connector instance') : t('Create a new connector instance')}
    >
      <>
        {loading && <Loader />}
        {disabledMessage && disabled && <Alert style={{ marginBottom: theme.spacing(2) }} severity="warning">{disabledMessage}</Alert>}
        {!loading && (
          <ConnectorInstanceForm
            catalogConnectorSlug={catalogConnectorSlug}
            initialConfigurationValues={initialValues}
            configurationsDefinitionMap={configurationsDefinitionMap}
            onSubmit={onCreateConnectorInstance}
            onClose={onClose}
            disabled={disabled}
            isMigrating={migrationSource !== undefined}
          />
        )}
      </>
    </Drawer>
  );
};

export default CreateConnectorInstanceDrawer;
