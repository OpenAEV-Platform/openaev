import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import { fetchCatalogConnectorConfigurations } from '../../../../actions/catalog/catalog-actions';
import { createConnectorInstance } from '../../../../actions/connector_instances/connector-instance-actions';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import type {
  CatalogConnector,
  CatalogConnectorConfiguration,
  ConfigurationInput,
  CreateConnectorInstanceInput,
} from '../../../../utils/api-types';
import ConnectorInstanceForm from '../catalog_connectors/ConnectorInstanceForm';

interface Props {
  open: boolean;
  onClose: () => void;
  catalogConnectorId: string;
  catalogConnectorSlug: string;
  connectorType: CatalogConnector['catalog_connector_type'];
}

const CreateConnectorInstanceDrawer = ({ open, onClose, catalogConnectorId, catalogConnectorSlug, connectorType }: Props) => {
  const { t } = useFormatter();
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  const [configurations, setConfigurations] = useState<CatalogConnectorConfiguration[]>([]);
  const [initialValues, setInitialValues] = useState<Omit<CreateConnectorInstanceInput, 'catalog_connector_id'>>({});

  useEffect(() => {
    console.log('use effect');
    if (!open || !catalogConnectorId) return;
    setLoading(true);

    fetchCatalogConnectorConfigurations(catalogConnectorId).then(({ data }: { data: CatalogConnectorConfiguration[] }) => {
      setConfigurations(data);
      const initialConfigurations: ConfigurationInput[] = data.map(conf => (
        {
          configuration_key: conf.connector_configuration_key,
          configuration_value: conf.connector_configuration_type == 'INTEGER' ? conf.connector_configuration_default?.toString() : (conf.connector_configuration_default || ''),
        }),
      );
      setInitialValues({ connector_instance_configurations: initialConfigurations });
      setLoading(false);
    });
  }, [open, catalogConnectorId]);

  const onCreateConnectorInstance = (data: Omit<CreateConnectorInstanceInput, 'catalog_connector_id'>) => {
    console.log('Create connector instance with data: ', data);
    createConnectorInstance({
      catalog_connector_id: catalogConnectorId,
      ...data,
    }).then(({ data }) => {
      console.log('Connector instance created: ', data);
      const collectorId = data.connector_instance_configurations.find(conf => conf.connector_instance_configuration_key === `${connectorType}_ID`)?.connector_instance_configuration_value;
      if (collectorId) {
        navigate(`/admin/integrations/${connectorType?.toLowerCase()}s/${collectorId}`);
      }
      onClose();
    });
  };

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={t('Create a new payload')}
    >
      <>
        {loading && <Loader />}
        {!loading
          && (
            <ConnectorInstanceForm
              catalogConnectorSlug={catalogConnectorSlug}
              initialConfigurationValues={initialValues}
              configurationsDefinition={configurations}
              onSubmit={onCreateConnectorInstance}
              onClose={onClose}
            />
          )}
      </>
    </Drawer>
  );
};

export default CreateConnectorInstanceDrawer;
