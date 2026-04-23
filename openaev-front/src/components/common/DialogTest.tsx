import { Alert, Button, Dialog, DialogActions, DialogContent, DialogContentText } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../i18n';
import Transition from './Transition';

interface DialogTestProps {
  open: boolean;
  handleClose: () => void;
  handleSubmit: () => void;
  text: string;
  alertText?: string;
}

const DialogTest: FunctionComponent<DialogTestProps> = ({
  open,
  handleClose,
  handleSubmit,
  text,
  alertText,
}) => {
  const { t } = useFormatter();
  return (
    <Dialog
      open={open}
      onClose={handleClose}
      slotProps={{ paper: { elevation: 1 } }}
      slots={{ transition: Transition }}
    >
      <DialogContent>
        <DialogContentText component="span" style={{ textAlign: 'center' }}>
          {text}
          {!!alertText && (
            <Alert variant="outlined" severity="warning" style={{ marginTop: 20 }}>
              {alertText}
            </Alert>
          )}
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>{t('Cancel')}</Button>
        <Button color="secondary" onClick={handleSubmit}>
          {t('Confirm')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default DialogTest;
