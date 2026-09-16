import { Box, Button } from '@mui/material';

import { useFormatter } from '../../../../../components/i18n';

interface ActionFormButtonsProps {
  disabled: boolean;
  onCancel: () => void;
  submitLabel?: string;
  readOnly?: boolean;
  cancelLabel?: string;
}

const ActionFormButtons = ({
  disabled,
  onCancel,
  submitLabel,
  readOnly = false,
  cancelLabel,
}: ActionFormButtonsProps) => {
  const { t } = useFormatter();

  return (
    <Box sx={{
      display: 'flex',
      justifyContent: 'flex-end',
      gap: 1,
      mt: 1,
    }}
    >
      <Button variant="outlined" color="primary" onClick={onCancel}>
        {cancelLabel ?? (readOnly ? t('Close') : t('Cancel'))}
      </Button>
      {!readOnly && (
        <Button variant="contained" color="primary" type="submit" disabled={disabled}>
          {submitLabel ?? t('Save')}
        </Button>
      )}
    </Box>
  );
};

export default ActionFormButtons;
