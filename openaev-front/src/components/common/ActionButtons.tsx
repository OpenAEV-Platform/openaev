import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

interface ActionButtonsProps {
  onCancel: () => void;
  onSubmit?: () => void;
  submitLabel: string;
  cancelLabel: string;
  disabled?: boolean;
  submitting?: boolean;
}

const ActionButtons: FunctionComponent<ActionButtonsProps> = ({
  onCancel,
  onSubmit,
  submitLabel,
  cancelLabel,
  disabled = false,
  submitting = false,
}) => {
  const theme = useTheme();

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'row',
      gap: theme.spacing(2),
    }}
    >
      <Button
        variant="contained"
        onClick={onCancel}
        disabled={submitting}
      >
        {cancelLabel}
      </Button>

      <Button
        variant="contained"
        color="secondary"
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
