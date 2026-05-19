import { type FunctionComponent } from 'react';

import { useFormatter } from '../i18n';
import DialogConfirmation from './DialogConfirmation';

interface DialogDuplicateProps {
  open: boolean;
  handleClose: () => void;
  handleSubmit: () => void;
  text: string;
}

const DialogDuplicate: FunctionComponent<DialogDuplicateProps> = (props) => {
  const { t } = useFormatter();
  return <DialogConfirmation {...props} submitLabel={t('Duplicate')} />;
};

export default DialogDuplicate;
