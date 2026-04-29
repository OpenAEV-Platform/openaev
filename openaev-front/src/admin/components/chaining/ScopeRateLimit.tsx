import {
  Box,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  type SelectChangeEvent,
  Switch,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../components/i18n';
import type { WorkflowConfigurationInput, WorkflowConfigurationOutput } from '../../../utils/api-types';

interface ScopeRateLimitProps {
  workflowConfiguration: WorkflowConfigurationOutput | undefined;
  onUpdate: (overrides: Partial<WorkflowConfigurationInput>) => void;
}

const ScopeRateLimit = ({ workflowConfiguration, onUpdate }: ScopeRateLimitProps) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();

  const rateLimitEnabled = workflowConfiguration?.workflow_configuration_rate_limit_enabled ?? false;
  const maxAttempts = workflowConfiguration?.workflow_configuration_max_attempts ?? 1;
  const maxTemporalRateSeconds = workflowConfiguration?.workflow_configuration_max_temporal_rate_seconds ?? 1800;
  const minutes = Math.floor(maxTemporalRateSeconds / 60) || 1;

  const handleToggleRateLimit = () => {
    onUpdate({ workflow_configuration_rate_limit_enabled: !rateLimitEnabled });
  };

  const handleMaxAttemptsChange = (event: SelectChangeEvent<number>) => {
    onUpdate({ workflow_configuration_max_attempts: Number(event.target.value) });
  };

  const handleMinutesChange = (event: SelectChangeEvent<number>) => {
    onUpdate({ workflow_configuration_max_temporal_rate_seconds: Number(event.target.value) * 60 });
  };

  return (
    <Box sx={{
      display: 'grid',
      gap: theme.spacing(2),
    }}
    >
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <Typography variant="h4">
          {t('Simulation rate limit')}
        </Typography>
        <Switch checked={rateLimitEnabled} onChange={handleToggleRateLimit} />
      </Box>

      <Paper sx={{ p: 2 }} variant="outlined">
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: '80px 80px 1fr',
            gap: theme.spacing(2),
            alignItems: 'end',
          }}
        >
          <FormControl size="small" disabled={!rateLimitEnabled}>
            <InputLabel sx={{ color: theme.palette.grey['500'] }}>{t('Max Attempts')}</InputLabel>
            <Select
              value={maxAttempts}
              label={t('Max Attempts')}
              onChange={handleMaxAttemptsChange}
            >
              {Array.from({ length: 99 }, (_, i) => (
                <MenuItem key={i + 1} value={i + 1}>
                  {String(i + 1).padStart(2, '0')}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <FormControl size="small" disabled={!rateLimitEnabled}>
            <InputLabel sx={{ color: theme.palette.grey['500'] }}>{t('Minutes')}</InputLabel>
            <Select
              value={minutes}
              label={t('Minutes')}
              onChange={handleMinutesChange}
            >
              {Array.from({ length: 59 }, (_, i) => (
                <MenuItem key={i + 1} value={i + 1}>
                  {String(i + 1).padStart(2, '0')}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <Typography
            variant="body2"
            sx={{
              color: theme.palette.grey['500'],
              alignSelf: 'center',
            }}
          >
            {t('Controls how often an attack step is executed. Useful for simulating brute-force or slow, stealthy attacks.')}
          </Typography>
        </Box>
      </Paper>
    </Box>
  );
};

export default ScopeRateLimit;
