import { Dialog as DialogMUI, DialogActions, DialogContent, DialogContentText } from '@mui/material';
import type React from 'react';
import { useState } from 'react';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../i18n';
import Button from './button/Button';
import Transition from './Transition';

interface DialogConfirmationProps {
  open: boolean;
  handleClose: () => void;
  handleSubmit: (() => void) | (() => Promise<void>) | null | undefined;
  text: string;
  submitLabel: string;
  richContent?: React.ReactNode;
}

const DialogConfirmation: FunctionComponent<DialogConfirmationProps> = ({
  open = false,
  handleClose,
  handleSubmit = undefined,
  text,
  submitLabel,
  richContent,
}) => {
  const { t } = useFormatter();
  const [loading, setLoading] = useState(false);

  const handleLoadingAndSubmit = () => {
    setLoading(true);
    if (handleSubmit) handleSubmit();
  };

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
        <Button variant="secondary" onClick={handleClose}>{t('Cancel')}</Button>
        {handleSubmit && (
          <Button variant="primary" loading={loading} onClick={handleLoadingAndSubmit}>
            {submitLabel}
          </Button>
        )}
      </DialogActions>
    </DialogMUI>
  );
};

export default DialogConfirmation;
