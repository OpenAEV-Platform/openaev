import { type ReactNode, useContext } from 'react';
import { useOutletContext } from 'react-router';

import useDialog from '../../../../components/common/dialog/useDialog';
import Tabs, { type TabsEntry } from '../../../../components/common/tabs/Tabs';
import useTabs from '../../../../components/common/tabs/useTabs';
import { useFormatter } from '../../../../components/i18n';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import CreateConnectorInstanceDrawer from '../connector_instance/CreateConnectorInstanceDrawer';
import ConnectorAlerts from './ConnectorAlerts';
import ConnectorCatalogInfo from './ConnectorCatalogInfo';
import { ConnectorContext } from './ConnectorContext';
import type { ConnectorContextLayoutType } from './ConnectorLayout';
import ConnectorLogs from './ConnectorLogs';
import ConnectorTitle from './ConnectorTitle';

const ConnectorPage = ({ extraInfoComponent }: { extraInfoComponent?: ReactNode }) => {
  const { t } = useFormatter();

  const { connector, instance, catalogConnector, isXtmComposerUp, refreshConnector } = useOutletContext<ConnectorContextLayoutType>();
  const { isValidated: isEnterpriseEdition } = useEnterpriseEdition();
  const ability = useContext(AbilityContext);
  const { logoUrl } = useContext(ConnectorContext);
  const createInstanceDrawer = useDialog();

  const onCloseCreateInstanceDrawer = () => {
    createInstanceDrawer.handleClose();
    refreshConnector();
  };

  const onMigrateBtnClick = () => {
    createInstanceDrawer.handleOpen();
  };

  const tabEntries: TabsEntry[] = [{
    key: 'overview',
    label: 'Overview',
  }];

  if (instance?.connector_instance_id) {
    tabEntries.push({
      key: 'logs',
      label: 'Logs',
    });
  }

  const { currentTab, handleChangeTab } = useTabs(tabEntries[0].key);

  return (
    <>
      <ConnectorAlerts
        isEnterpriseEdition={isEnterpriseEdition}
        isXtmComposerUp={isXtmComposerUp}
        catalogConnector={catalogConnector}
      />
      <ConnectorTitle
        connector={{
          instanceId: instance?.connector_instance_id,
          connectorName: connector?.name || catalogConnector?.catalog_connector_title,
          connectorType: catalogConnector?.catalog_connector_type,
          connectorLogoName: connector?.type || catalogConnector?.catalog_connector_slug,
          connectorLogoUrl: instance ? `/api/images/catalog/connectors/logos/${catalogConnector?.catalog_connector_logo_url}` : logoUrl(connector?.type),
          connectorDescription: catalogConnector?.catalog_connector_description,
          isExternal: catalogConnector?.catalog_connector_manager_supported,
          isVerified: instance != null,
          connectorUseCases: catalogConnector?.catalog_connector_use_cases,
        }}
        detailsTitle
        instanceCurrentStatus={instance?.connector_instance_current_status}
        instanceRequestedStatus={instance?.connector_instance_requested_status}
        showUpdateButtons={ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS)}
        showMigrateButton={connector?.isExternal === true && !instance && isXtmComposerUp && ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS)}
        onMigrateBtnClick={onMigrateBtnClick}
        disabledUpdateButtons={!isEnterpriseEdition || (!isXtmComposerUp && catalogConnector?.catalog_connector_manager_supported)}
      />
      <Tabs
        entries={tabEntries}
        currentTab={currentTab}
        onChange={newValue => handleChangeTab(newValue)}
      />
      {currentTab === 'overview' && catalogConnector && (
        <>
          <ConnectorCatalogInfo catalogConnector={catalogConnector} />
          {extraInfoComponent}
        </>
      )}
      {currentTab === 'logs' && (
        <ConnectorLogs connectorInstanceId={instance.connector_instance_id} />
      )}
      <CreateConnectorInstanceDrawer
        open={createInstanceDrawer.open}
        catalogConnectorId={catalogConnector ? catalogConnector.catalog_connector_id : ''}
        catalogConnectorSlug={catalogConnector ? catalogConnector.catalog_connector_slug : ''}
        onClose={onCloseCreateInstanceDrawer}
        connectorType={catalogConnector?.catalog_connector_type}
        disabled={!isXtmComposerUp && catalogConnector?.catalog_connector_manager_supported}
        migrationSource={connector?.id}
        disabledMessage={t('Deployment of this {catalogType} requires the installation of our Integration Manager.', { catalogType: catalogConnector ? catalogConnector.catalog_connector_type.toLowerCase() : '' })}
      />
    </>
  );
};

export default ConnectorPage;
