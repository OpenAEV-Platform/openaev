import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import type React from 'react';

import { useFormatter } from '../../../../../components/i18n';

interface Props {
  open: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

const XtmHubDialogConnectivityLostAuthorizedRegister: React.FC<Props> = ({ open, onCancel, onConfirm }) => {
  const { t } = useFormatter();

  return (
    <Dialog
      open={open}
      onClose={onCancel}
      slotProps={{ paper: { elevation: 1 } }}
      aria-labelledby="authorized-register-dialog-title"
      aria-describedby="authorized-register-dialog-description"
    >
      <DialogTitle id="authorized-register-dialog-title">{t('Connectivity lost')}</DialogTitle>
      <DialogContent>
        <DialogContentText id="authorized-register-dialog-description">
          <p>{t('XTM Hub Connection Unavailable')}</p>
          <p>{t('Please re-register platform')}</p>
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} color="primary">
          {t('Cancel')}
        </Button>
        <Button onClick={onConfirm} color="secondary">
          {t('Re-register')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default XtmHubDialogConnectivityLostAuthorizedRegister;
