import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { CustomDashboardContext } from './CustomDashboardContext';
import CustomDashboardParameters from './CustomDashboardParameters';
import CustomDashboardReactLayout from './CustomDashboardReactLayout';
import SelectDashboardButton from './SelectDashboardButton';
import WidgetDataDrawer from './widgetDataDrawer/WidgetDataDrawer';

interface Props {
  readOnly?: boolean;
  noDashboardSlot?: React.ReactNode;
}

const CustomDashboardComponent = ({ noDashboardSlot, readOnly = true }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const { customDashboard, contextId, handleSelectNewDashboard, canChooseDashboard } = useContext(CustomDashboardContext);

  if (!customDashboard && noDashboardSlot) {
    return noDashboardSlot;
  }
  if (!customDashboard) {
    return (
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
      >
        <Typography variant="h4" color="textSecondary">{t('No custom dashboard selected.')}</Typography>
      </div>
    );
  }

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: '1fr auto',
      gap: theme.spacing(2),
    }}
    >
      <CustomDashboardParameters />
      {canChooseDashboard && handleSelectNewDashboard && (
        <SelectDashboardButton
          defaultDashboardId={customDashboard.custom_dashboard_id}
          handleApplyChange={handleSelectNewDashboard}
          scenarioOrSimulationId={contextId}
        />
      )}
      <CustomDashboardReactLayout style={{ gridColumn: 'span 2' }} readOnly={readOnly} />
      <WidgetDataDrawer />
    </div>
  );
};

export default CustomDashboardComponent;
