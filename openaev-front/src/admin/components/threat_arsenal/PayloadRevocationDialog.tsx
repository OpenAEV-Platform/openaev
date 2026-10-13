import { Button, Textarea } from '@filigran/design-system';
import { Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import Transition from '../../../components/common/Transition';
import { useFormatter } from '../../../components/i18n';

interface Props {
  open: boolean;
  name: string;
  onClose: () => void;
  onSubmit: (reason: string) => void;
}

const PayloadRevocationDialog: FunctionComponent<Props> = ({ open, name, onClose, onSubmit }) => {
  const { t } = useFormatter();
  const [reason, setReason] = useState('');

  const close = () => {
    setReason('');
    onClose();
  };

  return (
    <Dialog
      open={open}
      slots={{ transition: Transition }}
      onClose={close}
      slotProps={{ paper: { elevation: 1 } }}
      fullWidth
      maxWidth="sm"
    >
      <DialogTitle>{`${t('Revoke')} ${name}`}</DialogTitle>
      <DialogContent>
        <DialogContentText style={{ marginBottom: 12 }}>
          {t('A revoked payload no longer runs in this tenant: injects using it are skipped and their scenarios cannot launch until it is lifted.')}
        </DialogContentText>
        <Textarea
          aria-label={t('Reason')}
          placeholder={t('Reason')}
          minRows={3}
          value={reason}
          onChange={event => setReason(event.target.value)}
        />
      </DialogContent>
      <DialogActions>
        <Button type="button" priority="secondary" onClick={close}>{t('Cancel')}</Button>
        <Button
          type="button"
          variant="destructive"
          disabled={reason.trim() === ''}
          onClick={() => {
            onSubmit(reason.trim());
            setReason('');
          }}
        >
          {t('Revoke')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default PayloadRevocationDialog;
