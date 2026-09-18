import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useCallback, useContext } from 'react';
import { useNavigate } from 'react-router';

import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import { type CustomDashboard } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { truncate } from '../../../../utils/String';
import { CustomDashboardContext } from './CustomDashboardContext';
import CustomDashboardPopover from './CustomDashboardPopover';
import WidgetCreation from './widgets/WidgetCreation';

const CustomDashboardEditHeader: FunctionComponent = () => {
  const { t } = useFormatter();
  const theme = useTheme();
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
      <Breadcrumbs
        variant="list"
        elements={[{ label: t('Dashboards') },
          {
            label: t('Custom dashboards'),
            link: '/admin/workspaces/custom_dashboards',
          },
          {
            label: customDashboard?.custom_dashboard_name ?? '',
            current: true,
          }]}
      />
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        marginBottom: theme.spacing(2),
      }}
      >
        <Tooltip>
          <TooltipTrigger asChild>
            <Typography variant="h1">
              {truncate(customDashboard.custom_dashboard_name, 80)}
            </Typography>
          </TooltipTrigger>
          {customDashboard.custom_dashboard_name && <TooltipContent>{customDashboard.custom_dashboard_name}</TooltipContent>}
        </Tooltip>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(1),
        }}
        >
          {/* The primary action closes the row: the kebab of secondary actions
              sits before it, never after. */}
          <Can I={ACTIONS.MANAGE} a={SUBJECTS.DASHBOARDS}>
            <CustomDashboardPopover
              customDashboard={customDashboard}
              onUpdate={handleUpdate}
              onDelete={() => navigate('/admin/workspaces/custom_dashboards')}
            />
          </Can>
          <Can I={ACTIONS.MANAGE} a={SUBJECTS.DASHBOARDS}>
            <WidgetCreation />
          </Can>
        </div>
      </div>
    </>
  );
};

export default CustomDashboardEditHeader;
