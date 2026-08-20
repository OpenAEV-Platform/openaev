import { HourglassEmptyOutlined, InfoOutlined, SpeedOutlined } from '@mui/icons-material';
import {
  Box,
  Divider,
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
import { type ReactNode } from 'react';

import { useFormatter } from '../../../components/i18n';
import type { WorkflowConfigurationInput, WorkflowConfigurationOutput } from '../../../utils/api-types';

interface Props {
  workflowConfiguration: WorkflowConfigurationOutput | undefined;
  onUpdate: (overrides: Partial<WorkflowConfigurationInput>) => void;
  /** Autonomous (AI-driven) run: the OpenAEV-owned session timeout replaces the chaining-engine
   *  timeout, and the per-step rate limit does not apply (the orchestrator paces itself), so the
   *  rate-limit control is hidden. */
  autonomous?: boolean;
  /** OpenAEV-owned autonomous session timeout in seconds (default 24h). Only used when autonomous. */
  autonomousTimeoutSeconds?: number | null;
}

const DEFAULT_AUTONOMOUS_TIMEOUT_SECONDS = 24 * 3600;

// A single labelled row of a limits section: typed icon + title + info hint + an enable switch,
// with the concrete controls rendered underneath. Keeping both time-out and rate-limit in one card
// (instead of two sibling cards) balances the second row against the Variables card.
interface LimitSectionProps {
  icon: ReactNode;
  title: string;
  tooltip: string;
  enabled: boolean;
  onToggle: () => void;
  children: ReactNode;
}

const LimitSection = ({ icon, title, tooltip, enabled, onToggle, children }: LimitSectionProps) => {
  const theme = useTheme();
  return (
    <Box sx={{
      display: 'grid',
      gap: theme.spacing(1.5),
    }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(1),
      }}
      >
        <Box sx={{
          display: 'inline-flex',
          color: 'text.secondary',
        }}
        >
          {icon}
        </Box>
        <Typography variant="subtitle2" sx={{ color: 'text.primary' }}>
          {title}
        </Typography>
        <Tooltip title={tooltip}>
          <InfoOutlined
            color="primary"
            sx={{
              fontSize: 16,
              cursor: 'pointer',
            }}
          />
        </Tooltip>
        <Switch checked={enabled} onChange={onToggle} sx={{ ml: 'auto' }} />
      </Box>
      <Box sx={{
        display: 'grid',
        gridTemplateColumns: '1fr 1fr',
        gap: theme.spacing(2),
        maxWidth: 260,
      }}
      >
        {children}
      </Box>
    </Box>
  );
};

const ScopeExecutionLimits = ({ workflowConfiguration, onUpdate, autonomous = false, autonomousTimeoutSeconds }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();

  // Time out. On an autonomous run the displayed budget is the OpenAEV-owned session timeout (the
  // watchdog hard-stops the run at its deadline); it is always on and reflects what the operator set
  // in the launch drawer (default 24h), not the chaining-engine timeout of a manual chained scenario.
  const timeoutEnabled = autonomous || (workflowConfiguration?.workflow_configuration_timeout_enabled ?? false);
  const totalSeconds = autonomous
    ? (autonomousTimeoutSeconds ?? DEFAULT_AUTONOMOUS_TIMEOUT_SECONDS)
    : (workflowConfiguration?.workflow_configuration_timeout_seconds ?? 3600);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const minMinutes = hours === 0 ? 1 : 0;
  // The manual editor caps hours at 23; a 24h+ autonomous budget needs the current value to exist as
  // an option so the read-only select is not left blank.
  const hoursOptionCount = Math.max(24, hours + 1);

  const handleToggleTimeout = () => onUpdate({ workflow_configuration_timeout_enabled: !timeoutEnabled });

  const handleHoursChange = (event: SelectChangeEvent<number>) => {
    const newHours = Number(event.target.value);
    const currentMinutes = newHours === 0 && minutes === 0 ? 1 : minutes;
    onUpdate({ workflow_configuration_timeout_seconds: (newHours * 3600) + (currentMinutes * 60) });
  };

  const handleTimeoutMinutesChange = (event: SelectChangeEvent<number>) => {
    const newMinutes = Number(event.target.value);
    if (hours === 0 && newMinutes === 0) return;
    onUpdate({ workflow_configuration_timeout_seconds: (hours * 3600) + (newMinutes * 60) });
  };

  // Rate limit
  const rateLimitEnabled = workflowConfiguration?.workflow_configuration_rate_limit_enabled ?? false;
  const maxAttempts = workflowConfiguration?.workflow_configuration_max_attempts ?? 1;
  const maxTemporalRateSeconds = workflowConfiguration?.workflow_configuration_max_temporal_rate_seconds ?? 1800;
  const rateMinutes = Math.floor(maxTemporalRateSeconds / 60) || 1;

  const handleToggleRateLimit = () => {
    const enabling = !rateLimitEnabled;
    onUpdate({
      workflow_configuration_rate_limit_enabled: enabling,
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

  const handleRateMinutesChange = (event: SelectChangeEvent<number>) => {
    onUpdate({
      workflow_configuration_max_temporal_rate_seconds: Number(event.target.value) * 60,
      workflow_configuration_max_attempts: maxAttempts,
    });
  };

  return (
    <Paper
      variant="outlined"
      sx={{
        height: '100%',
        p: theme.spacing(2),
        display: 'grid',
        gap: theme.spacing(2),
        alignContent: 'start',
      }}
    >
      <LimitSection
        icon={<HourglassEmptyOutlined fontSize="small" />}
        title={autonomous ? t('Session time out') : t('Simulation time out')}
        tooltip={autonomous
          ? t('Maximum total runtime for this autonomous session. OpenAEV automatically stops the run and tears down its simulation once the timeout is reached.')
          : t('Maximum total runtime for the entire chained scenario. Execution stops automatically once the timeout is reached.')}
        enabled={timeoutEnabled}
        onToggle={handleToggleTimeout}
      >
        <FormControl size="small" disabled={!timeoutEnabled}>
          <InputLabel sx={{ color: theme.palette.grey['500'] }}>{t('Hours')}</InputLabel>
          <Select value={hours} label={t('Hours')} onChange={handleHoursChange}>
            {Array.from({ length: hoursOptionCount }, (_, i) => (
              <MenuItem key={i} value={i}>{String(i).padStart(2, '0')}</MenuItem>
            ))}
          </Select>
        </FormControl>
        <FormControl size="small" disabled={!timeoutEnabled}>
          <InputLabel sx={{ color: theme.palette.grey['500'] }}>{t('Minutes')}</InputLabel>
          <Select value={minutes} label={t('Minutes')} onChange={handleTimeoutMinutesChange}>
            {Array.from({ length: 60 - minMinutes }, (_, i) => i + minMinutes).map(i => (
              <MenuItem key={i} value={i}>{String(i).padStart(2, '0')}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </LimitSection>

      {/* The per-step rate limit paces the manual chaining engine; on an autonomous run the AI
          orchestrator paces itself, so the control does not apply and is hidden. */}
      {!autonomous && (
        <>
          <Divider flexItem />

          <LimitSection
            icon={<SpeedOutlined fontSize="small" />}
            title={t('Simulation rate limit')}
            tooltip={t('Controls how often an attack step is executed. Useful for simulating brute-force or slow, stealthy attacks.')}
            enabled={rateLimitEnabled}
            onToggle={handleToggleRateLimit}
          >
            <FormControl size="small" disabled={!rateLimitEnabled}>
              <InputLabel sx={{ color: theme.palette.grey['500'] }}>{t('Max Attempts')}</InputLabel>
              <Select value={maxAttempts} label={t('Max Attempts')} onChange={handleMaxAttemptsChange}>
                {Array.from({ length: 99 }, (_, i) => (
                  <MenuItem key={i + 1} value={i + 1}>{String(i + 1).padStart(2, '0')}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl size="small" disabled={!rateLimitEnabled}>
              <InputLabel sx={{ color: theme.palette.grey['500'] }}>{t('Minutes')}</InputLabel>
              <Select value={rateMinutes} label={t('Minutes')} onChange={handleRateMinutesChange}>
                {Array.from({ length: 59 }, (_, i) => (
                  <MenuItem key={i + 1} value={i + 1}>{String(i + 1).padStart(2, '0')}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </LimitSection>
        </>
      )}
    </Paper>
  );
};

export default ScopeExecutionLimits;
