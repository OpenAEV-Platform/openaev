import { useContext, useState } from 'react';
import { useOutletContext } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { deleteConnectorInstance } from '../../../../actions/connector_instances/connector-instance-actions';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import { useFormatter } from '../../../../components/i18n';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import UpdateConnectorInstanceDrawer from '../connector_instance/UpdateConnectorInstanceDrawer';
import type { ConnectorContextLayoutType } from './ConnectorLayout';

type ConnectorPopoverProps = {
  connectorInstanceId: string;
  connectorName: string;
};

const useStyles = makeStyles()(() => ({ autoMarginLeft: { marginLeft: 'auto' } }));

const ConnectorPopover = ({ connectorInstanceId, connectorName }: ConnectorPopoverProps) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);
  const { instance, catalogConnector, isXtmComposerUp } = useOutletContext<ConnectorContextLayoutType>();

  const [openDialogDelete, setOpenDialogDelete] = useState(false);

  // const handleDelete = () => {
  //   setOpenDialogDelete(true);
  // };

  const submitDeleteConnectorInstance = () => {
    dispatch(deleteConnectorInstance(connectorInstanceId));
    setOpenDialogDelete(false);
  };
  const [openUpdateConnectorInstanceDrawer, setOpenCreateConnectorInstanceDrawer] = useState(false);
  const onOpenUpdateConnectorInstanceDrawer = () => setOpenCreateConnectorInstanceDrawer(true);
  const onCloseUpdateConnectorInstanceDrawer = () => setOpenCreateConnectorInstanceDrawer(false);

  // Button Popover
  const entries = [
    {
      //   label: 'Delete',
      //   action: handleDelete,
      //   userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS),
      // }, {
      label: 'Update',
      action: () => onOpenUpdateConnectorInstanceDrawer(),
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS),
    }];

  return (
    <>
      <ButtonPopover
        className={classes.autoMarginLeft}
        entries={entries}
        variant="toggle"
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
          disabled={!isXtmComposerUp}
          disabledMessage={t('Deployment of this {catalogType} requires the installation of our Integration Manager.', { catalogType: catalogConnector.catalog_connector_type.toLowerCase() })}
        />
      )}
    </>
  );
};

export default ConnectorPopover;
