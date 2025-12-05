import { Paper } from '@mui/material';
import { useOutletContext } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import Tabs, { type TabsEntry } from '../../../../components/common/tabs/Tabs';
import useTabs from '../../../../components/common/tabs/useTabs';
import { type CatalogConnectorOutput, type ConnectorInstance, type Injector } from '../../../../utils/api-types';
import ConnectorTitle from '../catalog_connectors/ConnectorTitle';
import ConnectorCatalogInfo from '../common/ConnectorCatalogInfo';
import ConnectorLogs from '../common/ConnectorLogs';
import InjectorContracts from './InjectorContracts';

const useStyles = makeStyles()(theme => ({
  paperConnector: {
    marginTop: theme.spacing(3),
    height: '100%',
  },
}));

const InjectorPage = () => {
  const { classes } = useStyles();

  const { injector, instance, catalogConnector }: {
    injector: Injector;
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
          instanceId: instance?.connector_instance_id,
          connectorName: injector?.injector_name || catalogConnector?.catalog_connector_title,
          connectorType: 'INJECTOR',
          connectorLogoName: injector?.injector_type || catalogConnector?.catalog_connector_slug,
          connectorLogoUrl: injector?.injector_type ? `/api/images/injectors/${injector.injector_type}` : `/api/images/catalog/connectors/logos/${catalogConnector?.catalog_connector_logo_url}`,
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

      {catalogConnector
        ? (
            <>
              <Tabs
                entries={tabEntries}
                currentTab={currentTab}
                onChange={newValue => handleChangeTab(newValue)}
              />
              {currentTab === 'overview' && catalogConnector && (
                <>
                  <ConnectorCatalogInfo catalogConnector={catalogConnector} />
                  <Paper variant="outlined" className={`paper ${classes.paperConnector}`}>
                    <InjectorContracts />
                  </Paper>
                </>
              )}
              {currentTab === 'logs' && (
                <ConnectorLogs />
              )}
            </>
          )
        : (
            <Paper variant="outlined" className={`paper ${classes.paperConnector}`}>
              <InjectorContracts />
            </Paper>
          )}
    </>
  );
};

export default InjectorPage;
