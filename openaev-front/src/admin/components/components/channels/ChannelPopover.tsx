import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { type FunctionComponent, useContext, useState } from 'react';
import { useNavigate } from 'react-router';

import { deleteChannel, updateChannel } from '../../../../actions/channels/channel-action';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { type Channel, type ChannelUpdateInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import ChannelForm from './ChannelForm';

interface Props { channel: Channel }

const ChannelPopover: FunctionComponent<Props> = ({ channel }) => {
  const [openDelete, setOpenDelete] = useState(false);
  const [openEdit, setOpenEdit] = useState(false);
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const ability = useContext(AbilityContext);

  const handleOpenEdit = () => {
    setOpenEdit(true);
  };

  const handleOpenDelete = () => {
    setOpenDelete(true);
  };

  const onSubmitEdit = async (data: ChannelUpdateInput) => {
    await dispatch(updateChannel(channel.channel_id, data));
    setOpenEdit(false);
  };

  const submitDelete = async () => {
    await dispatch(deleteChannel(channel.channel_id));
    setOpenDelete(false);
    navigate('/admin/components/channels');
  };

  const initialValues = {
    channel_type: channel.channel_type,
    channel_name: channel.channel_name,
    channel_description: channel.channel_description,
  };

  // Button Popover
  const entries = [{
    label: 'Update',
    action: () => handleOpenEdit(),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.CHANNELS),
  }, {
    label: 'Delete',
    action: () => handleOpenDelete(),
    userRight: ability.can(ACTIONS.DELETE, SUBJECTS.CHANNELS),
  }];

  return (
    <div>
      <ButtonPopover
        entries={entries}
        variant="icon"
      />

      <Dialog
        open={openDelete}
        TransitionComponent={Transition}
        onClose={() => setOpenDelete(false)}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to delete this channel?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpenDelete(false)}>{t('Cancel')}</Button>
          <Button color="secondary" onClick={submitDelete}>
            {t('Delete')}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        TransitionComponent={Transition}
        open={openEdit}
        onClose={() => setOpenEdit(false)}
        fullWidth={true}
        maxWidth="md"
        PaperProps={{ elevation: 1 }}
      >
        <DialogTitle>{t('Update the channel')}</DialogTitle>
        <DialogContent>
          <ChannelForm
            initialValues={initialValues}
            editing={true}
            onSubmit={onSubmitEdit}
            handleClose={() => setOpenEdit(false)}
          />
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default ChannelPopover;
