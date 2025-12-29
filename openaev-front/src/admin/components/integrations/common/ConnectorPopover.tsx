import { useTheme } from '@mui/material/styles';
import { useContext, useState } from 'react';
import { useNavigate, useOutletContext } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { updateRequestedStatus } from '../../../../actions/connector_instances/connector-instance-actions';
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
  disabled?: boolean;
  disabledDeleteButtons?: boolean;
};

const useStyles = makeStyles()(() => ({ autoMarginLeft: { marginLeft: 'auto' } }));

const ConnectorPopover = ({ connectorInstanceId, connectorName, disabled = false, disabledDeleteButtons = false }: ConnectorPopoverProps) => {
  const { classes } = useStyles();
  const theme = useTheme();
  const { t } = useFormatter();

  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);
  const { instance, catalogConnector, isXtmComposerUp } = useOutletContext<ConnectorContextLayoutType>();

  const [openDialogDelete, setOpenDialogDelete] = useState(false);

  const handleDelete = () => {
    setOpenDialogDelete(true);
  };

  const submitDeleteConnectorInstance = () => {
    dispatch(updateRequestedStatus(connectorInstanceId, { connector_instance_requested_status: 'deleting' })).then(() => {
      const parentPath = location.pathname.split('/').slice(0, -1).join('/');
      navigate(parentPath);
    });
    setOpenDialogDelete(false);
  };

  const [openUpdateConnectorInstanceDrawer, setOpenCreateConnectorInstanceDrawer] = useState(false);
  const onOpenUpdateConnectorInstanceDrawer = () => setOpenCreateConnectorInstanceDrawer(true);
  const onCloseUpdateConnectorInstanceDrawer = () => setOpenCreateConnectorInstanceDrawer(false);

  // Button Popover
  const entries = [{
    label: t('Update'),
    action: () => onOpenUpdateConnectorInstanceDrawer(),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS),
  }, {
    label: t('Delete'),
    action: handleDelete,
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS),
    disabled: disabledDeleteButtons,
    style: { color: theme.palette.error.light },
  }];

  return (
    <>
      <ButtonPopover
        className={classes.autoMarginLeft}
        entries={entries}
        variant="toggle"
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
          disabled={!isXtmComposerUp}
          disabledMessage={t('Deployment of this {catalogType} requires the installation of our Integration Manager.', { catalogType: catalogConnector.catalog_connector_type.toLowerCase() })}
        />
      )}
    </>
  );
};

export default ConnectorPopover;
