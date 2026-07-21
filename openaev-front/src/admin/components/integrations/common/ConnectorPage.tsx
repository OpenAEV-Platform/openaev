import { Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import moment from 'moment-timezone';
import { type ReactNode, useContext, useState } from 'react';
import { useNavigate, useOutletContext } from 'react-router';

import { updateRequestedStatus } from '../../../../actions/connector_instances/connector-instance-actions';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import useDialog from '../../../../components/common/dialog/useDialog';
import DialogDelete from '../../../../components/common/DialogDelete';
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
import { ConnectorContext, isSupportedByFiligran } from './ConnectorContext';
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
  const navigate = useNavigate();
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);

  const { connector, instance, catalogConnector, isXtmComposerUp, refreshConnector } = useOutletContext<ConnectorContextLayoutType>();
  const { isValidated: isEnterpriseEdition } = useEnterpriseEdition();
  const ability = useContext(AbilityContext);
  const { logoUrl, connectorType, apiRequest, routes } = useContext(ConnectorContext);
  // Built-in connectors have no catalog entry, so fall back to the layout's
  // connector type ('injector' -> 'INJECTOR') and the connector's own external
  // flag, keeping the hero's type / Built-in chip consistent with the catalog card.
  const heroType = catalogConnector?.catalog_connector_type
    ?? (connectorType.toUpperCase() as 'INJECTOR' | 'COLLECTOR' | 'EXECUTOR' | 'SECRETS_PROVIDER');
  const heroExternal = catalogConnector?.catalog_connector_manager_supported ?? connector?.isExternal;
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

  // Instance status: a requested transition shows as loading until it settles.
  const instanceCurrentStatus = instance?.connector_instance_current_status;
  const instanceRequestedStatus = instance?.connector_instance_requested_status;
  const isStatusLoading = (instanceCurrentStatus === 'started' && instanceRequestedStatus === 'stopping')
    || (instanceCurrentStatus === 'stopped' && instanceRequestedStatus === 'starting');
  // Uniform liveliness (same rules as the deployed cards), computed against the
  // layout's LIVE instance entity: connector.connectorInstance is a fetch-time
  // snapshot embedded in the DTO (absent for a just-deployed connector whose
  // injector has not registered yet) and never receives the SSE status updates,
  // so keying the chip on it would leave a freshly started instance on
  // "Stopped" forever.
  const liveliness = connector
    ? computeConnectorLiveliness({
        ...connector,
        connectorInstance: instance ?? connector.connectorInstance,
      })
    : undefined;

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
  // A connector with no managed instance (registered directly / manually, not
  // deployed through the Integration Manager) has no instance popover, so it
  // could never be removed. Offer a direct delete of the connector entity - the
  // heartbeat is what keeps it alive, so a stopped connector that no longer
  // pings can now be cleaned up. Built-in connectors run inside the platform and
  // must not be deletable.
  const canDeleteConnector = canManage && !instance && !!connector?.id && !liveliness?.builtIn;

  const handleDeleteConnector = () => {
    if (!connector?.id) return;
    dispatch(apiRequest.deleteSingle(connector.id)).then(() => {
      setIsDeleteOpen(false);
      navigate(routes.list);
    });
  };

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(2),
    }}
    >
      <ConnectorAlerts />
      <ConnectorDetailHero
        title={connector?.name || catalogConnector?.catalog_connector_title || ''}
        logoSrc={connectorLogoUrl}
        type={heroType}
        useCases={catalogConnector?.catalog_connector_use_cases}
        verified={isSupportedByFiligran(connector, catalogConnector?.catalog_connector_verified)}
        external={heroExternal}
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
            <Tooltip title={(() => {
              if (liveliness.builtIn) return t('Runs inside the platform');
              if (liveliness.lastSeen) return `${t('Last Seen')}: ${nsdt(liveliness.lastSeen)}`;
              return t('Never updated');
            })()}
            >
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
              // Kept openable when the Integration Manager is down: the Update
              // drawer shows the warning inside and disables the form, so the
              // action surface stays reachable (OpenCTI pattern).
              <ConnectorPopover
                connectorInstanceId={instance.connector_instance_id}
                connectorName={connector?.name || catalogConnector?.catalog_connector_title || ''}
                disabled={!isEnterpriseEdition}
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
            {canDeleteConnector && (
              <ButtonPopover
                variant="icon"
                entries={[{
                  label: 'Delete',
                  action: () => setIsDeleteOpen(true),
                  userRight: true,
                }]}
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
      {currentTab === 'overview' && (
        <>
          {catalogConnector && <ConnectorCatalogInfo catalogConnector={catalogConnector} />}
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
      <DialogDelete
        open={isDeleteOpen}
        handleClose={() => setIsDeleteOpen(false)}
        handleSubmit={handleDeleteConnector}
        text={`${t('Do you want to delete this integration:')} ${connector?.name ?? ''}?`}
      />
    </div>
  );
};

export default ConnectorPage;
