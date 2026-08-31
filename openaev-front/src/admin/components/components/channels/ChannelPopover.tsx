import { type FunctionComponent, useState } from 'react';
import { useNavigate } from 'react-router';

import { deleteChannel, updateChannel } from '../../../../actions/channels/channel-action';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type Channel, type ChannelUpdateInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import ChannelForm, { type ChannelFormInput } from './ChannelForm';

interface Props { channel: Channel }

const ChannelPopover: FunctionComponent<Props> = ({ channel }) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const ability = useAbility();

  // Edition
  const [openEdit, setOpenEdit] = useState(false);
  const onSubmitEdit = async (data: ChannelFormInput) => {
    // Carry the appearance fields over so a quick edit from the header never
    // wipes the header mode or the theme colors configured on the page.
    const input: ChannelUpdateInput = {
      ...data,
      channel_mode: channel.channel_mode,
      channel_primary_color_dark: channel.channel_primary_color_dark,
      channel_primary_color_light: channel.channel_primary_color_light,
      channel_secondary_color_dark: channel.channel_secondary_color_dark,
      channel_secondary_color_light: channel.channel_secondary_color_light,
    };
    await dispatch(updateChannel(channel.channel_id, input));
    setOpenEdit(false);
  };

  // Deletion
  const [openDelete, setOpenDelete] = useState(false);
  const submitDelete = async () => {
    await dispatch(deleteChannel(channel.channel_id));
    setOpenDelete(false);
    navigate('/admin/components/channels');
  };

  const initialValues: ChannelFormInput = {
    channel_type: channel.channel_type ?? 'newspaper',
    channel_name: channel.channel_name ?? '',
    channel_description: channel.channel_description ?? '',
  };

  const entries = [{
    label: 'Update',
    action: () => setOpenEdit(true),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.CHANNELS),
  }, {
    label: 'Delete',
    action: () => setOpenDelete(true),
    userRight: ability.can(ACTIONS.DELETE, SUBJECTS.CHANNELS),
  }];

  return (
    <>
      <ButtonPopover entries={entries} variant="icon" />
      <Drawer
        open={openEdit}
        handleClose={() => setOpenEdit(false)}
        title={t('Update the channel')}
      >
        <ChannelForm
          initialValues={initialValues}
          editing
          onSubmit={onSubmitEdit}
          handleClose={() => setOpenEdit(false)}
        />
      </Drawer>
      <DialogDelete
        open={openDelete}
        handleClose={() => setOpenDelete(false)}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this channel?')}
      />
    </>
  );
};

export default ChannelPopover;
