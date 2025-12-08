import { Alert, Paper } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext } from 'react';
import { useOutletContext } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import Tabs, { type TabsEntry } from '../../../../components/common/tabs/Tabs';
import useTabs from '../../../../components/common/tabs/useTabs';
import { useFormatter } from '../../../../components/i18n';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import ConnectorTitle from '../catalog_connectors/ConnectorTitle';
import ConnectorCatalogInfo from '../common/ConnectorCatalogInfo';
import ConnectorLogs from '../common/ConnectorLogs';
import InjectorContracts from './InjectorContracts';
import { type InjectorsContextType } from './InjectorsLayout';

const useStyles = makeStyles()(theme => ({
  paperConnector: {
    marginTop: theme.spacing(3),
    height: '100%',
  },
}));

const InjectorPage = () => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const theme = useTheme();

  const { injector, instance, catalogConnector, isXtmComposerUp } = useOutletContext<InjectorsContextType>();
  const { isValidated: isEnterpriseEdition } = useEnterpriseEdition();
  const ability = useContext(AbilityContext);

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
      {isEnterpriseEdition && !isXtmComposerUp && catalogConnector?.catalog_connector_manager_supported
        && (
          <Alert severity="warning" style={{ marginBottom: theme.spacing(2) }}>
            {t('Xtm composer is not reachable', { catalogType: catalogConnector.catalog_connector_type.toLowerCase() })}
          </Alert>
        )}
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
        showUpdateButtons={isEnterpriseEdition && ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS)}
        disabledUpdateButtons={!isXtmComposerUp && catalogConnector?.catalog_connector_manager_supported}
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
