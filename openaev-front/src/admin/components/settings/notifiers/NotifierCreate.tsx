import { type FunctionComponent, useState } from 'react';

import { createNotifier } from '../../../../actions/notifications/notifier-actions';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type NotifierInput, type NotifierOutput } from '../../../../utils/api-types';
import NotifierForm from './NotifierForm';

interface Props { onCreate?: (result: NotifierOutput) => void }

const NotifierCreate: FunctionComponent<Props> = ({ onCreate }) => {
  const { t } = useFormatter();
  const [open, setOpen] = useState(false);

  const onSubmit = (input: NotifierInput) => {
    createNotifier(input).then((result: { data: NotifierOutput }) => {
      if (result) {
        onCreate?.(result.data);
        setOpen(false);
      }
      return result;
    });
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Create a notifier')}
      >
        <NotifierForm onSubmit={onSubmit} />
      </Drawer>
    </>
  );
};

export default NotifierCreate;
