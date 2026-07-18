import { type SyntheticEvent, useMemo, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type CatalogConnectorOutput } from '../../../../utils/api-types';
import DeployButton from '../common/DeployButton';
import CreateConnectorInstanceDrawer from '../connector_instance/CreateConnectorInstanceDrawer';
import { type ConnectorItem, fromCatalogConnector } from './catalog-facets';
import ConnectorMarketplace from './ConnectorMarketplace';

interface Props {
  catalogConnectors: CatalogConnectorOutput[];
  isXtmComposerUp: boolean;
}

/**
 * The "Available" tab of the integrations page: the full connector catalog as
 * a faceted marketplace, with the deploy flow on every card.
 */
const Catalog = ({ catalogConnectors, isXtmComposerUp }: Props) => {
  const { t } = useFormatter();

  const items = useMemo(() => catalogConnectors.map(fromCatalogConnector), [catalogConnectors]);
  const connectorsById = useMemo(
    () => new Map(catalogConnectors.map(connector => [connector.catalog_connector_id, connector])),
    [catalogConnectors],
  );

  const [selectedConnector, setSelectedConnector] = useState<CatalogConnectorOutput>();
  const [openCreateConnectorInstanceDrawer, setOpenCreateConnectorInstanceDrawer] = useState(false);

  const onDeployBtnClick = (e: SyntheticEvent, item: ConnectorItem) => {
    e.preventDefault();
    e.stopPropagation();
    setSelectedConnector(connectorsById.get(item.id));
    setOpenCreateConnectorInstanceDrawer(true);
  };

  return (
    <>
      <ConnectorMarketplace
        items={items}
        renderFooterAction={item => (
          <DeployButton
            onDeployBtnClick={e => onDeployBtnClick(e, item)}
            deploymentCount={item.deployedCount}
          />
        )}
      />
      <CreateConnectorInstanceDrawer
        open={openCreateConnectorInstanceDrawer}
        catalogConnectorId={selectedConnector ? selectedConnector.catalog_connector_id : ''}
        catalogConnectorSlug={selectedConnector ? selectedConnector.catalog_connector_slug : ''}
        onClose={() => setOpenCreateConnectorInstanceDrawer(false)}
        connectorType={selectedConnector?.catalog_connector_type}
        disabled={!isXtmComposerUp && selectedConnector?.catalog_connector_manager_supported}
        disabledMessage={t('Deployment of this {catalogType} requires the installation of our Integration Manager.', { catalogType: selectedConnector ? selectedConnector.catalog_connector_type.toLowerCase() : '' })}
      />
    </>
  );
};

export default Catalog;
