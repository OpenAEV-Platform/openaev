import { Button, Dialog as DialogMUI, DialogActions, DialogContent, DialogContentText } from '@mui/material';
import React, {useState} from 'react';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../i18n';
import Transition from './Transition';
import { LoadingButton } from '@mui/lab';

interface DialogDeleteProps {
  open: boolean;
  handleClose: () => void;
  handleSubmit: () => void | null | undefined;
  text: string;
  richContent?: React.ReactNode;
}

const DialogDelete: FunctionComponent<DialogDeleteProps> = ({
  open = false,
  handleClose,
  handleSubmit = undefined,
  text,
  richContent,
}) => {
  const { t } = useFormatter();
  
  const handleLoadingAndSubmit = () => {
    setLoading(true);
    if(handleSubmit)
      handleSubmit();
  }

  const [loading, setLoading] = useState<boolean>(false);


  return (
    <DialogMUI
      open={open}
      onClose={handleClose}
      slotProps={{ paper: { elevation: 1 } }}
      slots={{ transition: Transition }}
    >
      <DialogContent>
        {richContent || (
          <DialogContentText>
            {text}
          </DialogContentText>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>{t('Cancel')}</Button>
        {handleSubmit && (
          <Button color="secondary" loading={loading} onClick={handleLoadingAndSubmit}>
            {t('Delete')}
          </Button>
        )}
      </DialogActions>
    </DialogMUI>
  );
};

export default DialogDelete;
