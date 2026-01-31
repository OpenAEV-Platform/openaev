import { Alert } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { updateConnectorInstance } from '../../../../actions/connector_instances/connector-instance-actions';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import DOTS from '../../../../constants/Strings';
import type {
  CatalogConnector, ConfigurationInput,
  ConnectorInstanceOutput,
  CreateConnectorInstanceInput,
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
  connectorInstanceId: ConnectorInstanceOutput['connector_instance_id'];
  disabled?: boolean;
  disabledMessage?: string;
}

const UpdateConnectorInstanceDrawer = ({
  open,
  onClose,
  catalogConnectorId,
  catalogConnectorSlug,
  connectorInstanceId,
  disabled = false, disabledMessage,
}: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const { loading, configurationsDefinitionMap, initialValues } = useConnectorInstanceForm(
    true,
    catalogConnectorId,
    connectorInstanceId,
    open,
  );

  const onUpdateConnectorInstance = (data: Omit<CreateConnectorInstanceInput, 'catalog_connector_id'>) => {
    // don't submit empty secrets
    const shouldFilter = (conf: ConfigurationInput) => {
      return configurationsDefinitionMap[conf.configuration_key].connector_configuration_writeonly
        && (!conf.configuration_value || conf.configuration_value.toString() === DOTS);
    };
    const filteredConfigs = data.connector_instance_configurations?.filter(conf => !shouldFilter(conf));
    updateConnectorInstance(connectorInstanceId, {
      catalog_connector_id: catalogConnectorId,
      connector_instance_configurations: filteredConfigs,
    }).then(() => {
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
      title={t('Update connector instance')}
    >
      <>
        {loading && <Loader />}
        {disabledMessage && disabled && <Alert style={{ marginBottom: theme.spacing(2) }} severity="warning">{disabledMessage}</Alert>}
        {!loading
          && (
            <ConnectorInstanceForm
              catalogConnectorSlug={catalogConnectorSlug}
              initialConfigurationValues={initialValues}
              configurationsDefinitionMap={configurationsDefinitionMap}
              onSubmit={onUpdateConnectorInstance}
              onClose={onClose}
              disabled={disabled}
              isEditing
            />
          )}
      </>
    </Drawer>
  );
};

export default UpdateConnectorInstanceDrawer;
