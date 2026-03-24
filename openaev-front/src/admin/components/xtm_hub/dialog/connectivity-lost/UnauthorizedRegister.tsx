import { Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import type React from 'react';

import Button from '../../../../../components/common/button/Button';
import { useFormatter } from '../../../../../components/i18n';

interface Props {
  open: boolean;
  onCancel: () => void;
}

const XtmHubDialogConnectivityLostUnauthorizedRegister: React.FC<Props> = ({ open, onCancel }) => {
  const { t } = useFormatter();
  return (
    <Dialog
      open={open}
      onClose={onCancel}
      slotProps={{ paper: { elevation: 1 } }}
      aria-labelledby="unauthorized-register-dialog-title"
      aria-describedby="unauthorized-register-dialog-description"
    >
      <DialogTitle id="unauthorized-register-dialog-title">{t('Connectivity lost')}</DialogTitle>
      <DialogContent>
        <DialogContentText id="unauthorized-register-dialog-description">
          <p>{t('XTM Hub Connection Unavailable')}</p>
          <p>{t('Please contact OpenAEV platform admin')}</p>
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button variant="secondary" onClick={onCancel}>
          {t('Cancel')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default XtmHubDialogConnectivityLostUnauthorizedRegister;
