import { useContext, useState } from 'react';
import { useNavigate, useOutletContext } from 'react-router';

import { deleteConnectorInstance } from '../../../../actions/connector_instances/connector-instance-actions';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import { useFormatter } from '../../../../components/i18n';
import { DATA_DELETE_SUCCESS } from '../../../../constants/ActionTypes';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import UpdateConnectorInstanceDrawer from '../connector_instance/UpdateConnectorInstanceDrawer';
import { ConnectorContext } from './ConnectorContext';
import type { ConnectorContextLayoutType } from './ConnectorLayout';

type ConnectorPopoverProps = {
  connectorInstanceId: string;
  connectorName: string;
  disabled?: boolean;
};

const ConnectorPopover = ({ connectorInstanceId, connectorName, disabled = false }: ConnectorPopoverProps) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const ability = useContext(AbilityContext);
  const { connector, instance, catalogConnector, isXtmComposerUp } = useOutletContext<ConnectorContextLayoutType>();
  const { apiRequest, connectorType } = useContext(ConnectorContext);

  const [openDialogDelete, setOpenDialogDelete] = useState(false);

  const handleDelete = () => {
    setOpenDialogDelete(true);
  };

  const submitDeleteConnectorInstance = async () => {
    await dispatch(deleteConnectorInstance(connectorInstanceId));
    // The backend also deletes the associated connector entity (injector/collector/executor).
    // Explicitly remove it from the Redux store so the list updates immediately without a manual refresh.
    const connectorEntityType = `${connectorType}s`; // 'injectors', 'collectors', 'executors'
    if (connector?.id) {
      dispatch({
        type: DATA_DELETE_SUCCESS,
        payload: {
          type: connectorEntityType,
          id: connector.id,
        },
      });
    }
    await dispatch(apiRequest.fetchAll());
    setOpenDialogDelete(false);
    navigate('..');
  };
  const [openUpdateConnectorInstanceDrawer, setOpenCreateConnectorInstanceDrawer] = useState(false);
  const onOpenUpdateConnectorInstanceDrawer = () => setOpenCreateConnectorInstanceDrawer(true);
  const onCloseUpdateConnectorInstanceDrawer = () => setOpenCreateConnectorInstanceDrawer(false);

  // OpenCTI parity: a started managed connector can never be deleted. Deletion
  // is only allowed once a stop has been requested (requested status stopping)
  // or is effective (current status stopped); the backend enforces the same
  // rule. The entry stays visible but disabled, with the reason as tooltip.
  const canDeleteInstance = instance?.connector_instance_requested_status === 'stopping'
    || instance?.connector_instance_current_status === 'stopped';

  // Button Popover
  const entries = [
    {
      label: 'Update',
      action: () => onOpenUpdateConnectorInstanceDrawer(),
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS),
    },
    {
      label: 'Delete',
      action: handleDelete,
      userRight: ability.can(ACTIONS.DELETE, SUBJECTS.TENANT_SETTINGS),
      disabled: !canDeleteInstance,
      disabledMessage: t('Stop the connector before deleting it'),
    }];

  return (
    <>
      <ButtonPopover
        entries={entries}
        disabled={disabled}
      />
      <DialogDelete
        open={openDialogDelete}
        handleClose={() => setOpenDialogDelete(false)}
        handleSubmit={submitDeleteConnectorInstance}
        text={`${t('Do you want to delete the connector:')} ${connectorName}?`}
      />
      {catalogConnector && instance && (
        <UpdateConnectorInstanceDrawer
          open={openUpdateConnectorInstanceDrawer}
          catalogConnectorId={catalogConnector.catalog_connector_id}
          catalogConnectorSlug={catalogConnector.catalog_connector_slug}
          connectorInstanceId={instance.connector_instance_id}
          onClose={onCloseUpdateConnectorInstanceDrawer}
          connectorType={catalogConnector.catalog_connector_type}
          disabled={!isXtmComposerUp && catalogConnector.catalog_connector_manager_supported}
          disabledMessage={t('Deployment of this {catalogType} requires the installation of our Integration Manager.', { catalogType: catalogConnector.catalog_connector_type.toLowerCase() })}
        />
      )}
    </>
  );
};

export default ConnectorPopover;
