import { useOutletContext } from 'react-router';

import Tabs, { type TabsEntry } from '../../../../components/common/tabs/Tabs';
import useTabs from '../../../../components/common/tabs/useTabs';
import {
  type CatalogConnectorOutput,
  type Collector,
  type ConnectorInstance,
} from '../../../../utils/api-types';
import ConnectorTitle from '../catalog_connectors/ConnectorTitle';
import ConnectorCatalogInfo from '../common/ConnectorCatalogInfo';
import ConnectorLogs from '../common/ConnectorLogs';

const CollectorPage = () => {
  const { collector, instance, catalogConnector }: {
    collector: Collector;
    instance: ConnectorInstance;
    catalogConnector: CatalogConnectorOutput;
  } = useOutletContext();

  const tabEntries: TabsEntry[] = [{
    key: 'overview',
    label: 'Overview',
  }, {
    key: 'logs',
    label: 'Logs',
  }];
  const { currentTab, handleChangeTab } = useTabs(tabEntries[0].key);

  return (
    <>
      <ConnectorTitle
        connector={{
          connectorName: collector?.collector_name || catalogConnector?.catalog_connector_title,
          connectorType: 'COLLECTOR',
          connectorLogoName: collector?.collector_type || catalogConnector?.catalog_connector_slug,
          connectorLogoUrl: collector?.collector_type ? `/api/images/collectors/${collector.collector_type}` : `/api/images/catalog/connectors/logos/${catalogConnector?.catalog_connector_logo_url}`,
          connectorDescription: catalogConnector?.catalog_connector_description,
          isExternal: catalogConnector?.catalog_connector_manager_supported,
          isVerified: instance != null,
          connectorUseCases: catalogConnector?.catalog_connector_use_cases,
        }}
        detailsTitle
        instanceCurrentStatus={instance?.connector_instance_current_status}
        showUpdateButton
        showUpdateStatusButton
      // onDeployBtnClick={onOpenCreateConnectorInstanceDrawer}
      />
      <Tabs
        entries={tabEntries}
        currentTab={currentTab}
        onChange={newValue => handleChangeTab(newValue)}
      />
      {currentTab === 'overview' && catalogConnector && (
        <ConnectorCatalogInfo catalogConnector={catalogConnector} />
      )}
      {currentTab === 'logs' && (
        <ConnectorLogs />
      )}
    </>
  );
};

export default CollectorPage;
