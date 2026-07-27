import { useState } from 'react';

import { addChannel } from '../../../../actions/channels/channel-action';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { useAppDispatch } from '../../../../utils/hooks';
import ChannelForm, { type ChannelFormInput } from './ChannelForm';

const CreateChannel = () => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const [open, setOpen] = useState(false);

  const onSubmit = async (data: ChannelFormInput) => {
    const result = await dispatch(addChannel(data));
    if (result.result) {
      setOpen(false);
    }
    return result;
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Create a new channel')}
      >
        <ChannelForm
          onSubmit={onSubmit}
          handleClose={() => setOpen(false)}
        />
      </Drawer>
    </>
  );
};

export default CreateChannel;
