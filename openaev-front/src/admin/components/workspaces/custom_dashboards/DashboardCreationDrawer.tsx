import { Box, Button, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import Drawer from '../../../../components/common/Drawer';
import CustomDashboardAutocompleteField from '../../../../components/fields/CustomDashboardAutocompleteField';
import { useFormatter } from '../../../../components/i18n';
import CustomDashboardForm, { type CustomDashboardFormType } from './CustomDashboardForm';

type Mode = 'existing' | 'new';

interface Props {
  open: boolean;
  onClose: () => void;
  /** Name pre-filled for the fresh dashboard (the scenario / simulation name). */
  defaultName: string;
  /** The parameter automatically wired to both the picked and the fresh dashboard. */
  parameterType: 'scenario' | 'simulation';
  /** Scenario / simulation id, used to scope the existing-dashboard picker. */
  resourceId: string;
  /** Attach an already-existing dashboard to the entity. */
  onSelectExisting: (dashboardId: string) => void;
  /** Create a fresh dashboard (already scoped with the entity parameter). */
  onCreateNew: (data: CustomDashboardFormType) => void;
}

const DashboardCreationDrawer: FunctionComponent<Props> = ({
  open,
  onClose,
  defaultName,
  parameterType,
  resourceId,
  onSelectExisting,
  onCreateNew,
}) => {
  const { t } = useFormatter();
  const [mode, setMode] = useState<Mode>('new');
  const [existingDashboardId, setExistingDashboardId] = useState('');

  const handleClose = () => {
    setMode('new');
    setExistingDashboardId('');
    onClose();
  };

  return (
    <Drawer
      open={open}
      handleClose={handleClose}
      title={t('Create a custom dashboard')}
    >
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
      }}
      >
        <ToggleButtonGroup
          size="small"
          exclusive
          fullWidth
          value={mode}
          onChange={(_, value: Mode | null) => value && setMode(value)}
        >
          <ToggleButton value="new">{t('New dashboard')}</ToggleButton>
          <ToggleButton value="existing">{t('Existing dashboard')}</ToggleButton>
        </ToggleButtonGroup>

        {mode === 'new'
          ? (
              <CustomDashboardForm
                onSubmit={onCreateNew}
                handleClose={handleClose}
                initialValues={{
                  custom_dashboard_name: defaultName,
                  custom_dashboard_description: '',
                  custom_dashboard_parameters: [{
                    custom_dashboards_parameter_name: parameterType,
                    custom_dashboards_parameter_type: parameterType,
                  }],
                }}
              />
            )
          : (
              <>
                <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                  {t('The selected dashboard is scoped to this context automatically through its {type} parameter.', { type: t(parameterType === 'scenario' ? 'Scenario' : 'Simulation') })}
                </Typography>
                <CustomDashboardAutocompleteField
                  label={t('Dashboard')}
                  value={existingDashboardId}
                  scenarioOrSimulationId={resourceId}
                  onChange={value => setExistingDashboardId(value)}
                />
                <Box sx={{
                  display: 'flex',
                  justifyContent: 'flex-end',
                  gap: 1,
                  mt: 2,
                }}
                >
                  <Button variant="outlined" color="primary" onClick={handleClose}>{t('Cancel')}</Button>
                  <Button
                    variant="contained"
                    color="primary"
                    disabled={!existingDashboardId}
                    onClick={() => onSelectExisting(existingDashboardId)}
                  >
                    {t('Continue')}
                  </Button>
                </Box>
              </>
            )}
      </Box>
    </Drawer>
  );
};

export default DashboardCreationDrawer;
