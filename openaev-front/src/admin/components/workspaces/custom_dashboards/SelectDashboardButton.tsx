import { InsertChartOutlined } from '@mui/icons-material';
import { Button } from '@mui/material';
import { useState } from 'react';

import Dialog from '../../../../components/common/dialog/Dialog';
import CustomDashboardAutocompleteField from '../../../../components/fields/CustomDashboardAutocompleteField';
import { useFormatter } from '../../../../components/i18n';

interface Props {
  /**
   * outlined: explicit "Change dashboard" button (Statistics tab, dashboard
   * already displayed) - text: plain link-like button (empty state).
   */
  variant?: 'outlined' | 'text';
  defaultDashboardId?: string;
  handleApplyChange: (dashboardId: string) => void;
  scenarioOrSimulationId?: string;
}

const SelectDashboardButton = ({ defaultDashboardId = '', variant = 'outlined', handleApplyChange, scenarioOrSimulationId }: Props) => {
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
      {variant === 'outlined'
        ? (
            <Button
              variant="outlined"
              color="primary"
              size="small"
              startIcon={<InsertChartOutlined />}
              onClick={handleOpenSelectDashboardDialog}
              sx={{
                alignSelf: 'center',
                flexShrink: 0,
              }}
            >
              {t('Change dashboard')}
            </Button>
          )
        : <Button onClick={handleOpenSelectDashboardDialog} variant="text">{t('Select a dashboard')}</Button>}
      <Dialog
        title={t('Select a dashboard')}
        open={openSelectDashboardDialog}
        handleClose={handleCloseSelectDashboardDialog}
        actions={(
          <>
            <Button variant="outlined" color="primary" onClick={handleCloseSelectDashboardDialog}>{t('Cancel')}</Button>
            <Button variant="contained" color="primary" onClick={onHandleSubmit}>
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
