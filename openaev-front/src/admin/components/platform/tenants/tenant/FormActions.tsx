import { Button } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

interface FormActionsProps {
  onCancel: () => void;
  submitLabel: string;
  cancelLabel: string;
  disabled?: boolean;
  submitting?: boolean;
}

const FormActions: FunctionComponent<FormActionsProps> = ({
  onCancel,
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
      <Button priority="secondary" onClick={onCancel} disabled={submitting}>
        {cancelLabel}
      </Button>

      <Button type="submit" disabled={disabled || submitting}>
        {submitLabel}
      </Button>
    </div>
  );
};

export default FormActions;
