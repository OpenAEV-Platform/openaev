import { InfoOutlined } from '@mui/icons-material';
import {
  Box,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  type SelectChangeEvent,
  Switch,
  Tooltip,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';

import { SECTION_LABEL_SX } from '../../../components/common/detail/detailStyles';
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
    const enabling = !rateLimitEnabled;
    onUpdate({
      workflow_configuration_rate_limit_enabled: enabling,
      // When enabling, ensure default values are sent so the backend never receives nulls.
      ...(enabling && {
        workflow_configuration_max_attempts: maxAttempts,
        workflow_configuration_max_temporal_rate_seconds: maxTemporalRateSeconds,
      }),
    });
  };

  const handleMaxAttemptsChange = (event: SelectChangeEvent<number>) => {
    onUpdate({
      workflow_configuration_max_attempts: Number(event.target.value),
      workflow_configuration_max_temporal_rate_seconds: maxTemporalRateSeconds,
    });
  };

  const handleMinutesChange = (event: SelectChangeEvent<number>) => {
    onUpdate({
      workflow_configuration_max_temporal_rate_seconds: Number(event.target.value) * 60,
      workflow_configuration_max_attempts: maxAttempts,
    });
  };

  return (
    <Box sx={{
      display: 'grid',
      gridTemplateRows: 'min-content 1fr',
      gap: theme.spacing(1),
    }}
    >
      <Typography
        sx={{
          ...SECTION_LABEL_SX,
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(1),
          m: 0,
        }}
      >
        {t('Simulation rate limit')}
        <Tooltip title={t('Controls how often an attack step is executed. Useful for simulating brute-force or slow, stealthy attacks.')}>
          <InfoOutlined
            color="primary"
            sx={{
              fontSize: 18,
              cursor: 'pointer',
            }}
          />
        </Tooltip>
        <Switch checked={rateLimitEnabled} onChange={handleToggleRateLimit} sx={{ ml: 'auto' }} />
      </Typography>

      <Paper sx={{ p: 2 }} variant="outlined">
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: '80px 80px',
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
        </Box>
      </Paper>
    </Box>
  );
};

export default ScopeRateLimit;
