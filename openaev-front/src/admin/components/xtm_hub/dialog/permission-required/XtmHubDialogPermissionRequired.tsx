import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import type React from 'react';
import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';

import { useFormatter } from '../../../../../components/i18n';
import { XTM_HUB_PERMISSION_REQUIRED_QUERY_PARAM } from '../../XtmHubRedirect';

const XtmHubDialogPermissionRequired: React.FC = () => {
  const { t } = useFormatter();
  const location = useLocation();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const searchParams = new URLSearchParams(location.search);
    if (searchParams.get(XTM_HUB_PERMISSION_REQUIRED_QUERY_PARAM) !== 'true') {
      return;
    }
    setOpen(true);
    searchParams.delete(XTM_HUB_PERMISSION_REQUIRED_QUERY_PARAM);
    const targetSearch = searchParams.toString();
    navigate(
      {
        pathname: location.pathname,
        search: targetSearch ? `?${targetSearch}` : '',
      },
      { replace: true },
    );
  }, [location.pathname, location.search, navigate]);

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
