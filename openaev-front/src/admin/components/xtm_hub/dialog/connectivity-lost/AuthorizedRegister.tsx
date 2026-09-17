import { Button } from '@filigran/design-system';
import { Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
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
          <p>{t('Please reconnect platform')}</p>
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button type="button" priority="secondary" onClick={onCancel}>
          {t('Cancel')}
        </Button>
        <Button type="button" onClick={onConfirm}>
          {t('Reconnect')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default XtmHubDialogConnectivityLostAuthorizedRegister;
