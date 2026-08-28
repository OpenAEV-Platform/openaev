import { KeyboardArrowDown, KeyboardArrowUp } from '@mui/icons-material';
import { IconButton, InputBase, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

interface StepperColumnProps {
  value: number;
  onChange: (value: number) => void;
  max: number;
  min?: number;
  step?: number;
  ariaLabel: string;
}

// A single numeric column (hours, minutes or interval) with up/down steppers
// and a directly editable value. Values wrap around on stepping.
const StepperColumn = ({ value, onChange, max, min = 0, step = 1, ariaLabel }: StepperColumnProps) => {
  const range = max - min + 1;
  const stepBy = (delta: number) => onChange(((value - min + delta * step) % range + range) % range + min);
  const pad = (n: number) => String(n).padStart(2, '0');
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
    }}
    >
      <IconButton size="small" aria-label={`${ariaLabel} +`} onClick={() => stepBy(1)}>
        <KeyboardArrowUp fontSize="small" />
      </IconButton>
      <InputBase
        value={pad(value)}
        inputProps={{
          'inputMode': 'numeric',
          'aria-label': ariaLabel,
        }}
        onChange={(event) => {
          const parsed = Number(event.target.value.replace(/\D/g, '').slice(-2));
          if (Number.isNaN(parsed)) {
            return;
          }
          onChange(Math.min(Math.max(parsed, min), max));
        }}
        sx={{
          'width': 44,
          '& input': {
            textAlign: 'center',
            fontSize: 22,
            fontWeight: 500,
            fontVariantNumeric: 'tabular-nums',
            padding: 0,
          },
        }}
      />
      <IconButton size="small" aria-label={`${ariaLabel} -`} onClick={() => stepBy(-1)}>
        <KeyboardArrowDown fontSize="small" />
      </IconButton>
    </div>
  );
};

interface TimeStepperFieldProps {
  label: string;
  hour: number;
  minute: number;
  onChangeHour: (hour: number) => void;
  onChangeMinute: (minute: number) => void;
  error?: string;
  hourLabel: string;
  minuteLabel: string;
}

/**
 * Inline HH:MM stepper replacing the MUI TimePicker in the scheduling dialog:
 * two wrapping numeric columns (24h hours, 5-minute steps but any typed value)
 * inside a labelled, bordered control.
 */
const TimeStepperField = ({ label, hour, minute, onChangeHour, onChangeMinute, error, hourLabel, minuteLabel }: TimeStepperFieldProps) => {
  const theme = useTheme();
  return (
    <div>
      <Typography variant="caption" component="div" sx={{ color: error ? 'error.main' : 'text.secondary' }}>
        {label}
      </Typography>
      <div style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: theme.spacing(0.5),
        border: `1px solid ${error ? theme.palette.error.main : theme.palette.divider}`,
        borderRadius: theme.shape.borderRadius,
        paddingInline: theme.spacing(1.5),
        paddingBlock: theme.spacing(0.25),
        marginTop: theme.spacing(0.5),
      }}
      >
        <StepperColumn value={hour} onChange={onChangeHour} max={23} ariaLabel={hourLabel} />
        <Typography sx={{
          fontSize: 22,
          fontWeight: 500,
          color: 'text.secondary',
          userSelect: 'none',
        }}
        >
          :
        </Typography>
        <StepperColumn value={minute} onChange={onChangeMinute} max={59} step={5} ariaLabel={minuteLabel} />
      </div>
      {error && (
        <Typography
          variant="caption"
          sx={{
            display: 'block',
            color: 'error.main',
          }}
        >
          {error}
        </Typography>
      )}
    </div>
  );
};

export { StepperColumn };
export default TimeStepperField;
