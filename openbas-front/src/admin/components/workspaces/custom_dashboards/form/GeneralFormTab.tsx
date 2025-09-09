import { FormControlLabel, Switch, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';
import { useFormContext } from 'react-hook-form';

import { fetchCustomDashboard } from '../../../../../actions/custom_dashboards/customdashboard-action';
import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import ConfirmChangeDefaultDashboardDialog from './ConfirmChangeDefaultDashboardDialog';

interface Props {
  initialDefaultDashboardIds: {
    home: string | undefined;
    scenario: string | undefined;
    simulation: string | undefined;
  };
}

const GeneralFormTab = ({ initialDefaultDashboardIds }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { setValue, watch } = useFormContext();

  const defaultDashboardConfig = {
    home: {
      fieldName: 'is_default_home_dashboard',
      label: t('Use by default in home'),
      defaultId: initialDefaultDashboardIds?.home,
    },
    scenario: {
      fieldName: 'is_default_scenario_dashboard',
      label: t('Use by default in scenario'),
      defaultId: initialDefaultDashboardIds?.scenario,
    },
    simulation: {
      fieldName: 'is_default_simulation_dashboard',
      label: t('Use by default in simulation'),
      defaultId: initialDefaultDashboardIds?.simulation,
    },
  } as const;

  const [defaultDashboardPendingChange, setDefaultDashboardPendingChange] = useState<{
    type: keyof typeof defaultDashboardConfig;
    dashboardName: string;
  } | null>(null);

  const handleDashboardToggle = (configType: keyof typeof defaultDashboardConfig, checked: boolean) => {
    const config = defaultDashboardConfig[configType];
    if (!checked || !config.defaultId) {
      setValue(config.fieldName, checked, {
        shouldValidate: true,
        shouldDirty: true,
      });
      return;
    }

    fetchCustomDashboard(config.defaultId).then(({ data }) => {
      setDefaultDashboardPendingChange({
        type: configType,
        dashboardName: data.custom_dashboard_name,
      });
    });
  };

  const handleConfirmDefaultDashboardChange = () => {
    if (defaultDashboardPendingChange) {
      setValue(defaultDashboardConfig[defaultDashboardPendingChange.type].fieldName, true, {
        shouldValidate: true,
        shouldDirty: true,
      });
      setDefaultDashboardPendingChange(null);
    }
  };

  const handleCloseConfirmDefaultDashboard = () => {
    setDefaultDashboardPendingChange(null);
  };

  return (
    <>
      <TextFieldController
        variant="standard"
        name="custom_dashboard_name"
        label={t('Name')}
        required
      />
      <TextFieldController
        variant="standard"
        name="custom_dashboard_description"
        label={t('Description')}
      />
      <div style={{
        display: 'grid',
        gap: theme.spacing(1),
      }}
      >
        <Typography>{t('Use by default')}</Typography>

        {Object.entries(defaultDashboardConfig).map(([type, config]) => (
          <FormControlLabel
            key={type}
            style={{ marginLeft: 0 }}
            label={t(config.label)}
            control={(
              <Switch
                size="small"
                checked={watch(config.fieldName)}
                onChange={value => handleDashboardToggle(type as keyof typeof defaultDashboardConfig, value.target.checked)}
              />
            )}
          />
        ))}
      </div>
      <ConfirmChangeDefaultDashboardDialog
        open={!!defaultDashboardPendingChange}
        onClose={handleCloseConfirmDefaultDashboard}
        onSubmit={handleConfirmDefaultDashboardChange}
        existingDashboardName={defaultDashboardPendingChange?.dashboardName ?? ''}
        defaultTypeName={defaultDashboardPendingChange?.type ?? ''}
      />
    </>
  );
};

export default GeneralFormTab;
