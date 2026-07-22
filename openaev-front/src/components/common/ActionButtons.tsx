import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, type FunctionComponent } from 'react';

interface ActionButtonsProps {
  onCancel: () => void;
  onSubmit?: () => void;
  submitLabel: string;
  cancelLabel: string;
  disabled?: boolean;
  submitting?: boolean;
  style?: CSSProperties;
}

const ActionButtons: FunctionComponent<ActionButtonsProps> = ({
  onCancel,
  onSubmit,
  submitLabel,
  cancelLabel,
  disabled = false,
  submitting = false,
  style = {},
}) => {
  const theme = useTheme();

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'row',
      gap: theme.spacing(2),
      ...style,
    }}
    >
      <Button
        variant="outlined"
        color="primary"
        onClick={onCancel}
        disabled={submitting}
      >
        {cancelLabel}
      </Button>

      <Button
        variant="contained"
        color="primary"
        type="submit"
        {...(onSubmit ? { onClick: onSubmit } : { type: 'submit' as const })}
        disabled={disabled || submitting}
      >
        {submitLabel}
      </Button>
    </div>
  );
};

export default ActionButtons;
