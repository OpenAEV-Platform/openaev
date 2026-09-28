import { IconButton } from '@filigran/design-system';
import { KeyboardArrowDown, KeyboardArrowUp } from '@mui/icons-material';
import { InputBase } from '@mui/material';

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
      <IconButton
        icon={<KeyboardArrowUp fontSize="small" />}
        aria-label={`${ariaLabel} +`}
        onClick={() => stepBy(1)}
        priority="tertiary"
        size="sm"
      />
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
      <IconButton
        icon={<KeyboardArrowDown fontSize="small" />}
        aria-label={`${ariaLabel} -`}
        onClick={() => stepBy(-1)}
        priority="tertiary"
        size="sm"
      />
    </div>
  );
};

export { StepperColumn };
export default StepperColumn;
