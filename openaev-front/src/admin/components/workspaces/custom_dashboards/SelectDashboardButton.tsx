import { Button } from '@mui/material';
import { useState } from 'react';

import ButtonPopover from '../../../../components/common/ButtonPopover';
import Dialog from '../../../../components/common/dialog/Dialog';
import CustomDashboardAutocompleteField from '../../../../components/fields/CustomDashboardAutocompleteField';
import { useFormatter } from '../../../../components/i18n';

interface Props {
  variant?: 'popover' | 'text';
  defaultDashboardId?: string;
  handleApplyChange: (dashboardId: string) => void;
  scenarioOrSimulationId?: string;
}

const SelectDashboardButton = ({ defaultDashboardId = '', variant = 'popover', handleApplyChange, scenarioOrSimulationId }: Props) => {
  // Standard hooks
  const { t } = useFormatter();
  const [dashboardId, setDashboardId] = useState<string>(defaultDashboardId);

  const [openSelectDashboardDialog, setOpenSelectDashboardDialog] = useState(false);
  const handleOpenSelectDashboardDialog = () => setOpenSelectDashboardDialog(true);
  const handleCloseSelectDashboardDialog = () => setOpenSelectDashboardDialog(false);

  const onHandleSubmit = () => {
    handleApplyChange(dashboardId);
    handleCloseSelectDashboardDialog();
  };

  return (
    <>
      {variant === 'popover'
        ? (
            <ButtonPopover
              entries={[{
                label: 'Select a dashboard',
                action: handleOpenSelectDashboardDialog,
                userRight: true,
              }]}
              style={{ alignSelf: 'start' }}
            />
          )
        : <Button onClick={handleOpenSelectDashboardDialog} variant="text">{t('Select a dashboard')}</Button>}
      <Dialog
        title={t('Select a dashboard')}
        open={openSelectDashboardDialog}
        handleClose={handleCloseSelectDashboardDialog}
        actions={(
          <>
            <Button onClick={handleCloseSelectDashboardDialog}>{t('Cancel')}</Button>
            <Button color="secondary" onClick={onHandleSubmit}>
              {t('Continue')}
            </Button>
          </>
        )}
      >
        <CustomDashboardAutocompleteField label={t('Dashboard')} value={dashboardId} scenarioOrSimulationId={scenarioOrSimulationId} onChange={value => setDashboardId(value)} />
      </Dialog>
    </>
  );
};

export default SelectDashboardButton;
