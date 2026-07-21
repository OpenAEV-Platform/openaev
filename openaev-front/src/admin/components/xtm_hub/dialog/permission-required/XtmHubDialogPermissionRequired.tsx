import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import type React from 'react';
import { useEffect, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { XTM_HUB_PERMISSION_REQUIRED_STORAGE_KEY } from '../../XtmHubRedirect';

const XtmHubDialogPermissionRequired: React.FC = () => {
  const { t } = useFormatter();
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (sessionStorage.getItem(XTM_HUB_PERMISSION_REQUIRED_STORAGE_KEY) !== 'true') {
      return;
    }
    setOpen(true);
    sessionStorage.removeItem(XTM_HUB_PERMISSION_REQUIRED_STORAGE_KEY);
  }, []);

  return (
    <Dialog
      open={open}
      onClose={() => setOpen(false)}
      slotProps={{ paper: { elevation: 1 } }}
      aria-labelledby="xtm-hub-permission-required-title"
      aria-describedby="xtm-hub-permission-required-description"
    >
      <DialogTitle id="xtm-hub-permission-required-title">{t('Permission required')}</DialogTitle>
      <DialogContent>
        <DialogContentText id="xtm-hub-permission-required-description">
          {t('You do not have permission to connect this product. Please contact your product administrator to connect the product on your behalf.')}
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => setOpen(false)}>{t('Close')}</Button>
      </DialogActions>
    </Dialog>
  );
};

export default XtmHubDialogPermissionRequired;
