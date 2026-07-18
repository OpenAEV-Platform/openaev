import { Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import moment from 'moment-timezone';
import { type ReactNode, useContext } from 'react';
import { useOutletContext } from 'react-router';

import { updateRequestedStatus } from '../../../../actions/connector_instances/connector-instance-actions';
import useDialog from '../../../../components/common/dialog/useDialog';
import Tabs, { type TabsEntry } from '../../../../components/common/tabs/Tabs';
import useTabs from '../../../../components/common/tabs/useTabs';
import { useFormatter } from '../../../../components/i18n';
import { useAppDispatch } from '../../../../utils/hooks';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import CreateConnectorInstanceDrawer from '../connector_instance/CreateConnectorInstanceDrawer';
import ActionButton from './ActionButton';
import { computeConnectorLiveliness } from './connector-liveliness';
import ConnectorAlerts from './ConnectorAlerts';
import ConnectorCatalogInfo from './ConnectorCatalogInfo';
import { ConnectorContext } from './ConnectorContext';
import ConnectorDetailHero from './ConnectorDetailHero';
import type { ConnectorContextLayoutType } from './ConnectorLayout';
import ConnectorLogs from './ConnectorLogs';
import ConnectorPopover from './ConnectorPopover';
import ConnectorStatus from './ConnectorStatus';
import MigrateButton from './MigrateButton';

/** Deployed connector detail page (collectors / executors / injectors). */
const ConnectorPage = ({ extraInfoComponent }: { extraInfoComponent?: ReactNode }) => {
  const theme = useTheme();
  const { t, nsdt } = useFormatter();
  const dispatch = useAppDispatch();

  const { connector, instance, catalogConnector, isXtmComposerUp, refreshConnector } = useOutletContext<ConnectorContextLayoutType>();
  const { isValidated: isEnterpriseEdition } = useEnterpriseEdition();
  const ability = useContext(AbilityContext);
  const { logoUrl } = useContext(ConnectorContext);
  const createInstanceDrawer = useDialog();

  const onCloseCreateInstanceDrawer = () => {
    createInstanceDrawer.handleClose();
    refreshConnector();
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

  const legacyConnectorLogoUrl = connector?.type ? logoUrl(connector.type) : undefined;
  const connectorLogoUrl = instance
    ? `/api/images/catalog/connectors/logos/${catalogConnector?.catalog_connector_logo_url}`
    : legacyConnectorLogoUrl;
  // Dummy (test) connectors ship without a real logo.
  const isDummy = (connector?.type ?? catalogConnector?.catalog_connector_slug ?? '').includes('dummy');

  // Instance status: a requested transition shows as loading until it settles.
  const instanceCurrentStatus = instance?.connector_instance_current_status;
  const instanceRequestedStatus = instance?.connector_instance_requested_status;
  const isStatusLoading = (instanceCurrentStatus === 'started' && instanceRequestedStatus === 'stopping')
    || (instanceCurrentStatus === 'stopped' && instanceRequestedStatus === 'starting');
  // Uniform liveliness (same rules as the deployed cards).
  const liveliness = connector ? computeConnectorLiveliness(connector) : undefined;

  const onUpdateRequestedStatusClick = () => {
    if (!instance?.connector_instance_id) return;
    // If we're already in a transition (starting or stopping),
    // user intention is to reverse the current requested action.
    let next: 'starting' | 'stopping';
    if (instanceRequestedStatus === 'starting') {
      next = 'stopping';
    } else if (instanceRequestedStatus === 'stopping') {
      next = 'starting';
    } else {
      next = instanceCurrentStatus === 'started' ? 'stopping' : 'starting';
    }
    dispatch(updateRequestedStatus(instance.connector_instance_id, { connector_instance_requested_status: next }));
  };

  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  const showMigrateButton = connector?.isExternal === true && !instance && isXtmComposerUp && canManage;
  const disabledUpdateButtons = !isEnterpriseEdition || (!isXtmComposerUp && catalogConnector?.catalog_connector_manager_supported === true);

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(2),
    }}
    >
      <ConnectorAlerts
        isEnterpriseEdition={isEnterpriseEdition}
        isXtmComposerUp={isXtmComposerUp}
        catalogConnector={catalogConnector}
      />
      <ConnectorDetailHero
        title={connector?.name || catalogConnector?.catalog_connector_title || ''}
        logoSrc={isDummy ? undefined : connectorLogoUrl}
        type={catalogConnector?.catalog_connector_type}
        useCases={catalogConnector?.catalog_connector_use_cases}
        verified={instance != null}
        external={catalogConnector?.catalog_connector_manager_supported}
        description={catalogConnector?.catalog_connector_short_description}
        statusChip={liveliness && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: theme.spacing(1),
          }}
          >
            <ConnectorStatus
              variant={(() => {
                if (isStatusLoading) return 'loading';
                return liveliness.started ? 'started' : 'stopped';
              })()}
            />
            <Tooltip title={liveliness.lastSeen ? `${t('Last Seen')}: ${nsdt(liveliness.lastSeen)}` : t('Never updated')}>
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: theme.spacing(0.75),
              }}
              >
                <span style={{
                  width: 8,
                  height: 8,
                  flexShrink: 0,
                  borderRadius: '50%',
                  backgroundColor: liveliness.healthy ? theme.palette.success.main : theme.palette.error.main,
                  boxShadow: `0 0 6px ${liveliness.healthy ? theme.palette.success.main : theme.palette.error.main}`,
                }}
                />
                {liveliness.lastSeen && (
                  <Typography
                    variant="body2"
                    sx={{
                      fontSize: 11,
                      whiteSpace: 'nowrap',
                      color: 'text.secondary',
                    }}
                  >
                    {moment(liveliness.lastSeen).fromNow()}
                  </Typography>
                )}
              </div>
            </Tooltip>
          </div>
        )}
        actions={(
          <>
            {canManage && instance?.connector_instance_id && (
              <ConnectorPopover
                connectorInstanceId={instance.connector_instance_id}
                connectorName={connector?.name || catalogConnector?.catalog_connector_title || ''}
                disabled={disabledUpdateButtons}
              />
            )}
            {showMigrateButton && (
              <MigrateButton onMigrateBtnClick={() => createInstanceDrawer.handleOpen()} />
            )}
            {canManage && instance?.connector_instance_id && (
              <ActionButton
                onUpdate={onUpdateRequestedStatusClick}
                disabled={disabledUpdateButtons}
                status={instanceRequestedStatus}
              />
            )}
          </>
        )}
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
    </div>
  );
};

export default ConnectorPage;
