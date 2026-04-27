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

interface Props {
  workflowConfiguration: WorkflowConfigurationOutput | undefined;
  onUpdate: (overrides: Partial<WorkflowConfigurationInput>) => void;
}

const ScopeTimeOut = ({ workflowConfiguration, onUpdate }: Props) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();

  const timeoutEnabled = workflowConfiguration?.workflow_configuration_timeout_enabled ?? false;
  const totalSeconds = workflowConfiguration?.workflow_configuration_timeout_seconds ?? 5400;
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);

  const handleToggleTimeout = () => {
    onUpdate({ workflow_configuration_timeout_enabled: !timeoutEnabled });
  };

  const handleHoursChange = (event: SelectChangeEvent<number>) => {
    const newHours = Number(event.target.value);
    const currentMinutes = newHours === 0 && minutes === 0 ? 1 : minutes;
    const newSeconds = (newHours * 3600) + (currentMinutes * 60);
    onUpdate({ workflow_configuration_timeout_seconds: newSeconds });
  };

  const handleMinutesChange = (event: SelectChangeEvent<number>) => {
    const newMinutes = Number(event.target.value);
    if (hours === 0 && newMinutes === 0) return;
    const newSeconds = (hours * 3600) + (newMinutes * 60);
    onUpdate({ workflow_configuration_timeout_seconds: newSeconds });
  };

  const minMinutes = hours === 0 ? 1 : 0;

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
          {t('Simulation time out')}
        </Typography>
        <Switch checked={timeoutEnabled} onChange={handleToggleTimeout} />
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
          <FormControl size="small" disabled={!timeoutEnabled}>
            <InputLabel sx={{ color: theme.palette.grey['500'] }}>
              {t('Hours')}
            </InputLabel>
            <Select
              value={hours}
              label={t('Hours')}
              onChange={handleHoursChange}
            >
              {Array.from({ length: 24 }, (_, i) => (
                <MenuItem key={i} value={i}>
                  {String(i).padStart(2, '0')}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <FormControl size="small" disabled={!timeoutEnabled}>
            <InputLabel sx={{ color: theme.palette.grey['500'] }}>
              {t('Minutes')}
            </InputLabel>
            <Select
              value={minutes}
              label={t('Minutes')}
              onChange={handleMinutesChange}
            >
              {Array.from({ length: 60 - minMinutes }, (_, i) => i + minMinutes).map(i => (
                <MenuItem key={i} value={i}>
                  {String(i).padStart(2, '0')}
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
            {t(
              'Maximum total runtime for the entire attack chaining scenario. Execution stops automatically once the timeout is reached.',
            )}
          </Typography>
        </Box>
      </Paper>
    </Box>
  );
};

export default ScopeTimeOut;
