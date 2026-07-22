import { Button, Dialog as DialogMUI, DialogActions, DialogContent, DialogContentText } from '@mui/material';
import type React from 'react';
import { useEffect, useState } from 'react';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../i18n';
import Transition from './Transition';

interface DialogConfirmationProps {
  open: boolean;
  handleClose: () => void;
  handleSubmit: ((resetLoading?: () => void) => void | Promise<void>) | null | undefined; // Updated: Callback is now optional
  text: string;
  submitLabel: string;
  richContent?: React.ReactNode;
}

const isPromiseLike = (value: unknown): value is Promise<void> => {
  return !!value && typeof (value as Promise<void>).then === 'function';
};

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

  // Reset loading state whenever the dialog closes so the next opening starts fresh
  useEffect(() => {
    if (!open) setLoading(false);
  }, [open]);

  const handleLoadingAndSubmit = () => {
    if (!handleSubmit) return;

    setLoading(true);
    const resetLoading = () => setLoading(false);

    try {
      const result = handleSubmit(resetLoading);

      if (isPromiseLike(result)) {
        result.finally(resetLoading);
        return;
      }

      // Legacy callback-style submitters control when loading must stop.
      if (handleSubmit.length > 0) {
        return;
      }

      resetLoading();
    } catch (error) {
      resetLoading();
      throw error;
    }
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
        <Button variant="outlined" color="primary" onClick={handleClose} disabled={loading}>
          {t('Cancel')}
        </Button>
        {handleSubmit && (
          <Button variant="contained" color="primary" loading={loading} onClick={handleLoadingAndSubmit}>
            {submitLabel}
          </Button>
        )}
      </DialogActions>
    </DialogMUI>
  );
};

export default DialogConfirmation;
