import { ErrorOutlineOutlined, WarningAmberOutlined } from '@mui/icons-material';
import { alpha, Box, Button, Popover, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent, useState } from 'react';
import { useNavigate } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import type { HealthCheck } from '../../../../utils/api-types';

interface Props {
  healthchecks: HealthCheck[];
  scenarioId?: string;
  exerciseId?: string;
}

const DOCUMENTATION_ROOT_URL = 'https://docs.openaev.io';

/**
 * Discrete, hero-friendly replacement for the bulky Healthchecks accordion:
 * a compact pill that surfaces the number of configuration issues and opens a
 * clean popover listing each one with its remediation action. Renders nothing
 * when everything is healthy.
 */
const HealthcheckIndicator: FunctionComponent<Props> = ({ healthchecks, scenarioId, exerciseId }) => {
  const theme = useTheme();
  const navigate = useNavigate();
  const { t } = useFormatter();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  if (!healthchecks?.length) {
    return null;
  }

  const hasError = healthchecks.some(healthcheck => healthcheck.status === 'ERROR');
  const accent = hasError ? theme.palette.error.main : theme.palette.warning.main;
  const Icon = hasError ? ErrorOutlineOutlined : WarningAmberOutlined;

  const ordered = [...healthchecks].sort((a, b) => (a.status === 'ERROR' && b.status !== 'ERROR' ? -1 : 1));

  const goToHealthcheckAction = (healthcheckType: string) => {
    setAnchorEl(null);
    switch (healthcheckType) {
      case 'SMTP':
        window.open(`${DOCUMENTATION_ROOT_URL}/latest/deployment/configuration/?h=smtp#mail-services`);
        break;
      case 'IMAP':
        window.open(`${DOCUMENTATION_ROOT_URL}/latest/deployment/configuration/?h=smtp#imap`);
        break;
      case 'AGENT_OR_EXECUTOR':
        navigate('/admin/agents');
        break;
      case 'SECURITY_SYSTEM_COLLECTOR':
        window.open(`${DOCUMENTATION_ROOT_URL}/latest/usage/collectors/?h=collector`);
        break;
      case 'INJECT':
      case 'TEAMS':
        navigate(exerciseId ? `/admin/simulations/${exerciseId}/injects` : `/admin/scenarios/${scenarioId}/injects`);
        break;
      case 'NMAP':
      case 'NUCLEI':
        window.open(`${DOCUMENTATION_ROOT_URL}/latest/usage/injectors`);
        break;
      case 'SCOPE_DEFINITION':
        navigate(exerciseId ? `/admin/simulations/${exerciseId}/scope` : `/admin/scenarios/${scenarioId}/scope`);
        break;
      default:
    }
  };

  return (
    <>
      <Button
        size="small"
        variant="outlined"
        startIcon={<Icon sx={{ fontSize: 16 }} />}
        onClick={(event: MouseEvent<HTMLElement>) => setAnchorEl(event.currentTarget)}
        sx={{
          'lineHeight': 'initial',
          'color': accent,
          'borderColor': alpha(accent, 0.4),
          'backgroundColor': alpha(accent, 0.08),
          '&:hover': {
            borderColor: accent,
            backgroundColor: alpha(accent, 0.14),
          },
        }}
      >
        {healthchecks.length === 1
          ? t('1 to configure')
          : t('{count} to configure', { count: healthchecks.length })}
      </Button>
      <Popover
        open={!!anchorEl}
        anchorEl={anchorEl}
        onClose={() => setAnchorEl(null)}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'right',
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'right',
        }}
        slotProps={{
          paper: {
            variant: 'outlined',
            sx: {
              marginTop: 1,
              width: 420,
              maxWidth: '90vw',
              borderRadius: 1,
              padding: 2,
            },
          },
        }}
      >
        <Typography sx={{
          fontFamily: '"Geologica", sans-serif',
          fontSize: 11,
          fontWeight: 600,
          letterSpacing: '0.12em',
          textTransform: 'uppercase',
          color: 'text.secondary',
          marginBottom: 1.5,
        }}
        >
          {t(exerciseId ? 'Simulation configuration' : 'Scenario configuration')}
        </Typography>
        <Box sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 1,
        }}
        >
          {ordered.map((healthcheck, index) => {
            const dotColor = healthcheck.status === 'ERROR' ? theme.palette.error.main : theme.palette.warning.main;
            return (
              <Box
                key={`healthcheck-${healthcheck.type}-${index}`}
                sx={{
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: 1.25,
                  padding: 1,
                  borderRadius: 1,
                  border: `1px solid ${alpha(dotColor, 0.25)}`,
                  background: alpha(dotColor, 0.05),
                }}
              >
                <Box sx={{
                  width: 8,
                  height: 8,
                  borderRadius: '50%',
                  flexShrink: 0,
                  marginTop: 0.75,
                  background: dotColor,
                  boxShadow: `0 0 6px ${alpha(dotColor, 0.6)}`,
                }}
                />
                <Box sx={{
                  flex: 1,
                  minWidth: 0,
                }}
                >
                  <Typography sx={{
                    fontSize: 13,
                    fontWeight: 600,
                  }}
                  >
                    {t(`healthcheck.type.${healthcheck.type}`)}
                  </Typography>
                  <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                    {t(`healthcheck.description.${healthcheck.type}.${healthcheck.detail}`)}
                  </Typography>
                </Box>
                <Button
                  color="primary"
                  size="small"
                  sx={{
                    flexShrink: 0,
                  }}
                  onClick={() => goToHealthcheckAction(healthcheck.type!)}
                >
                  {t(`healthcheck.button.${healthcheck.type}.${healthcheck.detail}`)}
                </Button>
              </Box>
            );
          })}
        </Box>
      </Popover>
    </>
  );
};

export default HealthcheckIndicator;
