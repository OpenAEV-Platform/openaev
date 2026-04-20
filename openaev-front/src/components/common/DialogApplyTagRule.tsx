import {
  Button,
  Dialog as DialogMUI,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
} from '@mui/material';
import { type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../i18n';

interface DialogDeleteProps {
  open: boolean;
  handleClose: () => void;
  handleApplyRule: () => void;
  handleDontApplyRule: () => void;
}

const DialogApplyTagRule: FunctionComponent<DialogDeleteProps> = ({
  open = false,
  handleClose,
  handleApplyRule,
  handleDontApplyRule,
}) => {
  const { t } = useFormatter();

  return (
    <DialogMUI
      open={open}
      onClose={handleClose}
      PaperProps={{ elevation: 1 }}
    >
      <DialogContent>
        <DialogTitle sx={{ paddingLeft: 0 }}>
          {t('ASSET RULE DETECTED')}
        </DialogTitle>
        <DialogContentText>
          {t('We detected that your change will trigger an {assetrule}. Would you like to apply the defined asset groups on your current existing injects?',
            { assetrule: <Link to="/admin/settings/asset_rules" target="_blank" rel="noreferrer">{t('asset rule')}</Link> })}
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleDontApplyRule}>
          {t('No')}
        </Button>
        <Button color="secondary" onClick={handleApplyRule}>
          {t('Yes')}
        </Button>
      </DialogActions>
    </DialogMUI>
  );
};

export default DialogApplyTagRule;
