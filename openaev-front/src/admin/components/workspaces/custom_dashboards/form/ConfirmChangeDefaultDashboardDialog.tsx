import { Button, Typography } from '@mui/material';

import Dialog from '../../../../../components/common/dialog/Dialog';
import { useFormatter } from '../../../../../components/i18n';

interface Props {
  open: boolean;
  onClose: () => void;
  onSubmit: () => void;
  existingDashboardName: string;
  defaultTypeName: string;
}

const ConfirmChangeDefaultDashboardDialog = ({ open, onClose, onSubmit, existingDashboardName, defaultTypeName }: Props) => {
  const { t } = useFormatter();

  return (
    <Dialog
      title={t('Set up new default dashboard')}
      open={open}
      handleClose={onClose}
      actions={(
        <>
          <Button onClick={onClose}>{t('Cancel')}</Button>
          <Button color="secondary" onClick={onSubmit}>
            {t('Continue')}
          </Button>
        </>
      )}
    >
      <>
        <Typography>
          {t('A dashboard ( {existingDashboardName} ) is already set as {defaultTypeName} default', {
            existingDashboardName,
            defaultTypeName,
          })}
        </Typography>
        <Typography>{t('Do you want to continue and set this new dashboard as {defaultTypeName} default ?', { defaultTypeName }) }</Typography>
      </>
    </Dialog>
  );
};

export default ConfirmChangeDefaultDashboardDialog;
