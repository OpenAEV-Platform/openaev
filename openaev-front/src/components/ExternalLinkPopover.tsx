import { Button, Dialog, DialogActions, DialogContent, DialogContentText } from '@mui/material';
import { type FunctionComponent } from 'react';

import Transition from './common/Transition';
import { useFormatter } from './i18n';

interface ExternalLinkPopoverProps {
  displayExternalLink: boolean;
  externalLink: string | URL | undefined;
  setDisplayExternalLink: (value: boolean) => void;
  setExternalLink: (value: string | URL | undefined) => void;
}

const ExternalLinkPopover: FunctionComponent<ExternalLinkPopoverProps> = ({
  displayExternalLink,
  externalLink,
  setDisplayExternalLink,
  setExternalLink,
}) => {
  const { t } = useFormatter();
  const handleCloseExternalLink = () => {
    setDisplayExternalLink(false);
    setExternalLink(undefined);
  };
  const handleBrowseExternalLink = () => {
    window.open(externalLink, '_blank');
    setDisplayExternalLink(false);
    setExternalLink(undefined);
  };
  return (
    <Dialog
      PaperProps={{ elevation: 1 }}
      open={displayExternalLink}
      keepMounted={true}
      TransitionComponent={Transition}
      onClose={handleCloseExternalLink}
    >
      <DialogContent>
        <DialogContentText>
          {t('Do you want to browse this external link?')}
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleCloseExternalLink}>{t('Cancel')}</Button>
        <Button color="secondary" onClick={handleBrowseExternalLink}>
          {t('Browse the link')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ExternalLinkPopover;
