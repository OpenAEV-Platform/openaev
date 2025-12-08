import { Alert } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext } from 'react';
import { useOutletContext } from 'react-router';

import Tabs, { type TabsEntry } from '../../../../components/common/tabs/Tabs';
import useTabs from '../../../../components/common/tabs/useTabs';
import { useFormatter } from '../../../../components/i18n';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import ConnectorTitle from '../catalog_connectors/ConnectorTitle';
import ConnectorCatalogInfo from '../common/ConnectorCatalogInfo';
import ConnectorLogs from '../common/ConnectorLogs';
import { type ExecutorsContextType } from './ExecutorsLayout';

const ExecutorPage = () => {
  const { t } = useFormatter();
  const theme = useTheme();

  const { executor, instance, catalogConnector, isXtmComposerUp } = useOutletContext<ExecutorsContextType>();
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
          connectorName: executor?.executor_name || catalogConnector?.catalog_connector_title,
          connectorType: 'EXECUTOR',
          connectorLogoName: executor?.executor_type || catalogConnector?.catalog_connector_slug,
          connectorLogoUrl: executor?.executor_type ? `/api/images/executors/${executor.executor_type}` : `/api/images/catalog/connectors/logos/${catalogConnector?.catalog_connector_logo_url}`,
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

export default ExecutorPage;
