import { InfoOutlined } from '@mui/icons-material';
import {
  Alert,
  Box,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  type SelectChangeEvent,
  Snackbar,
  Switch,
  Tooltip,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import React from 'react';

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

  const [validationError, setValidationError] = React.useState(false);

  const rateLimitEnabled = workflowConfiguration?.workflow_configuration_rate_limit_enabled ?? false;
  const maxAttempts = workflowConfiguration?.workflow_configuration_max_attempts ?? 1;
  const maxTemporalRateSeconds = workflowConfiguration?.workflow_configuration_max_temporal_rate_seconds ?? 1800;
  const minutes = Math.floor(maxTemporalRateSeconds / 60) || 1;

  const handleSaveAttempt = (overrides: Partial<WorkflowConfigurationInput>) => {
    const effectiveRateLimitEnabled = overrides.workflow_configuration_rate_limit_enabled ?? rateLimitEnabled;
    const effectiveMaxAttempts = overrides.workflow_configuration_max_attempts ?? maxAttempts;
    const effectiveMaxTemporalRateSeconds = overrides.workflow_configuration_max_temporal_rate_seconds ?? maxTemporalRateSeconds;
    if (effectiveRateLimitEnabled && (!effectiveMaxAttempts || !effectiveMaxTemporalRateSeconds)) {
      setValidationError(true);
      return;
    }
    onUpdate(overrides);
  };

  const handleToggleRateLimit = () => {
    onUpdate({ workflow_configuration_rate_limit_enabled: !rateLimitEnabled });
  };

  const handleMaxAttemptsChange = (event: SelectChangeEvent<number>) => {
    handleSaveAttempt({ workflow_configuration_max_attempts: Number(event.target.value) });
  };

  const handleMinutesChange = (event: SelectChangeEvent<number>) => {
    handleSaveAttempt({ workflow_configuration_max_temporal_rate_seconds: Number(event.target.value) * 60 });
  };

  return (
    <Box sx={{
      display: 'grid',
      gridTemplateRows: 'min-content 1fr',
      gap: theme.spacing(1),
    }}
    >
      <Typography
        variant="h4"
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          m: 0,
        }}
      >
        {t('Simulation rate limit')}
        <Switch checked={rateLimitEnabled} onChange={handleToggleRateLimit} />
      </Typography>

      <Paper sx={{ p: 2 }} variant="outlined">
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: '80px 80px auto',
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

          <Tooltip
            title={t('Controls how often an attack step is executed. Useful for simulating brute-force or slow, stealthy attacks.')}
            placement="top"
          >
            <InfoOutlined sx={{
              color: theme.palette.grey['500'],
              alignSelf: 'center',
              cursor: 'help',
            }}
            />
          </Tooltip>
        </Box>
      </Paper>
      <Snackbar
        open={validationError}
        autoHideDuration={4000}
        onClose={() => setValidationError(false)}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'center',
        }}
      >
        <Alert
          onClose={() => setValidationError(false)}
          severity="error"
          sx={{ width: '100%' }}
        >
          {t('Rate limit is enabled but no values are configured. Please set Max Attempts and Minutes before saving.')}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default ScopeRateLimit;
