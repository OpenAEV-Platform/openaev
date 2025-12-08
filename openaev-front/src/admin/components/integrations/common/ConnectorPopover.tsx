import { useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { deleteConnectorInstance } from '../../../../actions/connector_instances/connector-instance-actions';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import { useFormatter } from '../../../../components/i18n';
import { useAppDispatch } from '../../../../utils/hooks';

type ConnectorPopoverProps = {
  connectorInstanceId: string;
  connectorName: string;
  disabled?: boolean;
};

const useStyles = makeStyles()(() => ({ autoMarginLeft: { marginLeft: 'auto' } }));

const ConnectorPopover = ({ connectorInstanceId, connectorName, disabled = false }: ConnectorPopoverProps) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const [openDialogDelete, setOpenDialogDelete] = useState(false);

  const handleDelete = () => {
    setOpenDialogDelete(true);
  };

  const submitDeleteConnectorInstance = () => {
    dispatch(deleteConnectorInstance(connectorInstanceId));
    setOpenDialogDelete(false);
  };

  // Button Popover
  const entries = [{
    label: 'delete',
    action: handleDelete,
    userRight: true, // TODO
  }, {
    label: 'update',
    action: () => console.log('test'),
    userRight: true, // TODO
  }];

  return (
    <>
      <ButtonPopover
        className={classes.autoMarginLeft}
        entries={entries}
        variant="toggle"
        disabled
      />
      <DialogDelete
        open={openDialogDelete}
        handleClose={() => setOpenDialogDelete(false)}
        handleSubmit={submitDeleteConnectorInstance}
        text={`${t('Do you want to delete the connector:')} ${connectorName}?`}
      />
    </>
  );
};

export default ConnectorPopover;
