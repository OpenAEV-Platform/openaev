import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import Button from '../../../../../components/common/button/Button';

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
      <Button
        variant="secondary"
        onClick={onCancel}
        disabled={submitting}
      >
        {cancelLabel}
      </Button>

      <Button
        variant="primary"
        type="submit"
        disabled={disabled || submitting}
      >
        {submitLabel}
      </Button>
    </div>
  );
};

export default FormActions;
