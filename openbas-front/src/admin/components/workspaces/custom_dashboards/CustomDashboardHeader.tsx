import { Tooltip, Typography } from '@mui/material';
import { type FunctionComponent, useCallback, useContext } from 'react';
import { useNavigate } from 'react-router';

import { type CustomDashboard } from '../../../../utils/api-types';
import { truncate } from '../../../../utils/String';
import { CustomDashboardContext } from './CustomDashboardContext';
import CustomDashboardParameters from './CustomDashboardParameters';
import CustomDashboardPopover from './CustomDashboardPopover';

const CustomDashboardHeader: FunctionComponent = () => {
  // Standard hooks
  const navigate = useNavigate();

  const { customDashboard, setCustomDashboard } = useContext(CustomDashboardContext);

  const handleUpdate = useCallback(
    (currentCustomDashboard: CustomDashboard) => {
      setCustomDashboard({
        ...customDashboard,
        ...currentCustomDashboard,
      });
    },
    [],
  );

  if (!customDashboard) {
    return <></>;
  }

  return (
    <>
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
      }}
      >
        <div style={{
          display: 'flex',
          alignItems: 'center',
        }}
        >
          <Tooltip title={customDashboard.custom_dashboard_name}>
            <Typography variant="h1" style={{ margin: 0 }}>
              {truncate(customDashboard.custom_dashboard_name, 80)}
            </Typography>
          </Tooltip>
        </div>
        <CustomDashboardPopover
          customDashboard={customDashboard}
          onUpdate={handleUpdate}
          onDelete={() => navigate('/admin/workspaces/custom_dashboards')}
        />
      </div>
      <CustomDashboardParameters />
    </>
  );
};
export default CustomDashboardHeader;
