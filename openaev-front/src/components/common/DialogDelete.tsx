import type React from 'react';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../i18n';
import DialogConfirmation from './DialogConfirmation';

interface DialogDeleteProps {
  open: boolean;
  handleClose: () => void;
  handleSubmit: (() => void) | (() => Promise<void>) | null | undefined;
  text: string;
  richContent?: React.ReactNode;
}

const DialogDelete: FunctionComponent<DialogDeleteProps> = (props) => {
  const { t } = useFormatter();
  return <DialogConfirmation {...props} submitLabel={t('Delete')} />;
};

export default DialogDelete;
