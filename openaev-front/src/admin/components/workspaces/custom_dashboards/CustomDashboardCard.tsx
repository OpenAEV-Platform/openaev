import { AnalyticsOutlined } from '@mui/icons-material';
import { Box, Paper, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { useNavigate } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { type CustomDashboard } from '../../../../utils/api-types';
import CustomDashboardPopover from './CustomDashboardPopover';

interface Props {
  customDashboard: CustomDashboard;
  onUpdate: (result: CustomDashboard) => void;
  onDelete: (result: string) => void;
}

// Marketplace-style card for the custom dashboards list (same anatomy as the
// security platform cards: framed icon, name, clamped description, hover lift).
const CustomDashboardCard: FunctionComponent<Props> = ({
  customDashboard,
  onUpdate,
  onDelete,
}) => {
  const theme = useTheme();
  const navigate = useNavigate();
  const { t, fldt } = useFormatter();

  return (
    <Paper
      variant="outlined"
      data-testid="custom-dashboard-card"
      onClick={() => navigate(`/admin/workspaces/custom_dashboards/${customDashboard.custom_dashboard_id}`)}
      sx={{
        'position': 'relative',
        'display': 'flex',
        'flexDirection': 'column',
        'gap': 1.5,
        'padding': 2,
        'borderRadius': 1,
        'height': '100%',
        'cursor': 'pointer',
        'transition': theme.transitions.create(['border-color', 'box-shadow', 'transform']),
        '&:hover': {
          borderColor: alpha(theme.palette.primary.main, 0.5),
          boxShadow: `0 4px 16px ${alpha(theme.palette.common.black, 0.25)}`,
          transform: 'translateY(-2px)',
        },
      }}
    >
      <Box
        sx={{
          position: 'absolute',
          top: 6,
          right: 6,
        }}
        onClick={event => event.stopPropagation()}
      >
        <CustomDashboardPopover
          customDashboard={customDashboard}
          onUpdate={onUpdate}
          onDelete={onDelete}
          inList
        />
      </Box>

      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        minWidth: 0,
        paddingRight: 3,
      }}
      >
        <Box sx={{
          width: 44,
          height: 44,
          flexShrink: 0,
          borderRadius: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'primary.main',
          border: `1px solid ${alpha(theme.palette.primary.main, 0.2)}`,
          backgroundColor: alpha(theme.palette.primary.main, 0.08),
        }}
        >
          <AnalyticsOutlined />
        </Box>
        <Tooltip title={customDashboard.custom_dashboard_name}>
          <Typography sx={{
            fontSize: 14,
            fontWeight: 600,
            lineHeight: 1.35,
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
            wordBreak: 'break-word',
          }}
          >
            {customDashboard.custom_dashboard_name}
          </Typography>
        </Tooltip>
      </Box>

      <Typography
        variant="body2"
        sx={{
          color: 'text.secondary',
          minHeight: 40,
          display: '-webkit-box',
          WebkitLineClamp: 2,
          WebkitBoxOrient: 'vertical',
          overflow: 'hidden',
        }}
      >
        {customDashboard.custom_dashboard_description || '-'}
      </Typography>

      <Typography
        variant="body2"
        sx={{
          marginTop: 'auto',
          fontSize: 12,
          color: 'text.secondary',
        }}
      >
        {`${t('Updated at')} ${fldt(customDashboard.custom_dashboard_updated_at)}`}
      </Typography>
    </Paper>
  );
};

export default CustomDashboardCard;
